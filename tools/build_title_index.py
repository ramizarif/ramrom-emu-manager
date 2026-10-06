#!/usr/bin/env python3
"""
Build game title index from free, open sources for RamROM Emu Manager.

Sources:
  - Wikidata SPARQL (CC0): all gaming platforms
  - blawar/titledb (MIT): Switch title IDs and names (facts only, never descriptions/artwork)

Output: gzipped JSON at core/knowledge/src/main/assets/knowledge/title-index-v1.json.gz

Uses stdlib only; no external dependencies.
Respects SPARQL endpoint (User-Agent, sequential queries, retries with backoff).

Usage:
  python3 build_title_index.py --systems switch ps2 ps1 --out title-index-v1.json.gz
  python3 build_title_index.py --all  # All known systems

Verification notes (2026-10-05):
  - Redump licence unconfirmed; use Wikidata instead
  - titledb: take ONLY facts (titleId, name, region, year); never descriptions/artwork
"""

import json
import urllib.request
import urllib.error
import sys
import argparse
import gzip
import time
from typing import List, Dict, Any, Optional, Set
from urllib.parse import urlencode
from collections import defaultdict


class TitleIndexBuilder:
    """Build a game title index from Wikidata and blawar/titledb."""

    # Wikidata platform mappings: system -> Wikidata item QID
    # Verified with wbsearchentities queries; English labels confirmed
    WIKIDATA_SYSTEMS = {
        "switch": "Q19610114",      # Nintendo Switch
        "nds": "Q170323",           # Nintendo DS
        "n3ds": "Q203597",          # Nintendo 3DS
        "ps1": "Q10677",            # PlayStation
        "ps2": "Q10680",            # PlayStation 2
        "psp": "Q170325",           # PlayStation Portable
        "psvita": "Q188808",        # PlayStation Vita
        "gc": "Q182172",            # Nintendo GameCube
        "wii": "Q8079",             # Nintendo Wii
        "wiiu": "Q56942",           # Wii U
        "gba": "Q188642",           # Game Boy Advance
        "gbc": "Q203992",           # Game Boy Color
        "gb": "Q186437",            # Game Boy
        "nes": "Q172742",           # Nintendo Entertainment System
        "snes": "Q183259",          # Super Nintendo Entertainment System
        "n64": "Q184839",           # Nintendo 64
        "genesis": "Q10676",        # Sega Genesis (Mega Drive)
        "dreamcast": "Q184198",     # Sega Dreamcast
        "gamegear": "Q751719",      # Sega Game Gear
        "mastersystem": "Q209868",  # Sega Master System
        "saturn": "Q200912",        # Sega Saturn
        "neogeo": "Q1054350",       # SNK Neo Geo
    }

    # Wikidata SPARQL endpoint
    SPARQL_ENDPOINT = "https://query.wikidata.org/sparql"

    # blawar/titledb for Switch (facts only)
    TITLEDB_GITHUB_URL = "https://raw.githubusercontent.com/blawar/titledb/master"

    # User-Agent for politeness (no email or personal info)
    USER_AGENT = "RamROMEmuManager-build/0.1 (private build tool)"

    def __init__(self, output_path: str = "title-index-v1.json.gz", verbose: bool = True):
        self.output_path = output_path
        self.entries: List[Dict[str, Any]] = []
        self.verbose = verbose
        self.seen_keys: Set[str] = set()  # For deduplication

    def log(self, msg: str) -> None:
        """Print to stderr if verbose."""
        if self.verbose:
            print(msg, file=sys.stderr)

    def fetch_url(self, url: str, timeout: int = 30, max_retries: int = 3) -> Optional[str]:
        """Fetch content from a URL with retries and backoff."""
        req = urllib.request.Request(url, headers={"User-Agent": self.USER_AGENT})

        for attempt in range(1, max_retries + 1):
            try:
                with urllib.request.urlopen(req, timeout=timeout) as response:
                    return response.read().decode("utf-8")
            except urllib.error.URLError as e:
                if attempt == max_retries:
                    self.log(f"Error fetching {url} (final attempt): {e}")
                    return None
                wait_time = 2 ** (attempt - 1)
                self.log(f"  Retry {attempt}/{max_retries} in {wait_time}s: {e}")
                time.sleep(wait_time)
            except Exception as e:
                self.log(f"Unexpected error fetching {url}: {e}")
                return None

        return None

    def normalize_name(self, name: str) -> str:
        """Normalize game name for comparison and storage."""
        # Strip leading/trailing whitespace
        name = name.strip()
        # Collapse multiple spaces
        name = " ".join(name.split())
        return name

    def extract_region_from_name(self, name: str) -> Optional[str]:
        """Try to extract region from game name (e.g., '(US)', '[JP]', etc.)."""
        name_upper = name.upper()
        for region in ["US", "JP", "EU", "KR", "CN"]:
            if f"({region})" in name_upper or f"[{region}]" in name_upper:
                return region
        return None

    def extract_year_from_name(self, name: str) -> Optional[int]:
        """Try to extract year from name (e.g., 'Game (2020)')."""
        # Look for 4-digit number in parentheses at end
        import re
        match = re.search(r'\((\d{4})\)\s*$', name)
        if match:
            try:
                year = int(match.group(1))
                if 1980 <= year <= 2030:
                    return year
            except ValueError:
                pass
        return None

    def deduplicate_entry(self, entry: Dict[str, Any]) -> bool:
        """
        Check if entry is a duplicate; return True if new and added.
        Key = (system, name_normalized, region).
        """
        key = (entry["system"], entry["name"].lower(), entry.get("region", "UNKNOWN"))
        key_str = "|".join(key)

        if key_str in self.seen_keys:
            return False

        self.seen_keys.add(key_str)
        return True

    def query_wikidata(self, platform_qid: str, system_id: str, limit: int = 1000) -> List[Dict[str, Any]]:
        """
        Query Wikidata SPARQL endpoint for games on a given platform.
        Returns entries with title, region, year where available.
        """
        entries = []

        # SPARQL: games on a platform with release date
        sparql_query = f"""
SELECT ?game ?gameLabel ?releaseDate
WHERE {{
  ?game wdt:P31 wd:Q7889 .      # instance of video game
  ?game wdt:P400 wd:{platform_qid} .  # platform
  OPTIONAL {{ ?game wdt:P577 ?releaseDate . }}
  SERVICE wikibase:label {{ bd:serviceParam wikibase:language "en" . }}
}}
ORDER BY ?gameLabel
LIMIT {limit}
"""

        url = self.SPARQL_ENDPOINT + "?" + urlencode({
            "query": sparql_query,
            "format": "json"
        })

        self.log(f"  Querying Wikidata for {system_id} (QID {platform_qid})...")
        content = self.fetch_url(url, timeout=30)
        if not content:
            return entries

        try:
            data = json.loads(content)
            for result in data.get("results", {}).get("bindings", []):
                name = result.get("gameLabel", {}).get("value", "").strip()
                if not name:
                    continue

                # Drop entries with bare QID names (no English label)
                if name.startswith("Q") and name[1:].isdigit():
                    continue

                name = self.normalize_name(name)
                release_date = result.get("releaseDate", {}).get("value", "")

                year = None
                if release_date and len(release_date) >= 4:
                    try:
                        year = int(release_date[:4])
                    except ValueError:
                        pass

                entry = {
                    "system": system_id,
                    "name": name,
                    "region": "UNKNOWN",  # Wikidata doesn't provide region easily
                    "ids": {},
                }
                if year:
                    entry["year"] = year

                # Deduplicate
                if self.deduplicate_entry(entry):
                    entries.append(entry)

        except json.JSONDecodeError as e:
            self.log(f"  Error parsing Wikidata JSON: {e}")

        self.log(f"    Added {len(entries)} entries")
        return entries

    def fetch_titledb_switch(self, region: str = "US", language: str = "en") -> List[Dict[str, Any]]:
        """
        Fetch Nintendo Switch titles from blawar/titledb.
        Takes ONLY facts: titleId (base games only, ending in "000"), name, region, year.
        Never takes descriptions, artwork, or categories.
        Base games are identified by titleId ending in "000" (excludes DLC/updates).

        Format of titledb: { "titleId": { "id": "...", "name": "...", "releaseDate": "...", ... } }
        """
        entries = []

        # Fetch the region-specific titles file
        url = f"{self.TITLEDB_GITHUB_URL}/{region}.{language.lower()}.json"

        self.log(f"  Fetching titledb for {region}.{language}...")
        content = self.fetch_url(url, timeout=30)
        if not content:
            self.log(f"    Warning: Could not fetch titledb for {region}.{language}")
            return entries

        try:
            titles_by_titleid = json.loads(content)

            # Filter for base games only (titleId ending in "000")
            for titleid, entry_data in titles_by_titleid.items():
                if not isinstance(entry_data, dict):
                    continue

                # Only keep base games (ending in "000")
                if not titleid.endswith("000"):
                    continue

                name = entry_data.get("name", "").strip()
                if not name:
                    continue

                name = self.normalize_name(name)

                # Extract year from releaseDate if available
                year = None
                release_date = entry_data.get("releaseDate")
                if release_date:
                    try:
                        # releaseDate might be a string (YYYY-MM-DD) or an int
                        release_str = str(release_date)
                        if len(release_str) >= 4:
                            year = int(release_str[:4])
                    except (ValueError, TypeError):
                        pass

                entry = {
                    "system": "switch",
                    "name": name,
                    "region": region,
                    "ids": {"titleId": titleid},
                }
                if year:
                    entry["year"] = year

                entries.append(entry)

            self.log(f"    Added {len(entries)} base game entries from titledb {region}.{language}")

        except json.JSONDecodeError as e:
            self.log(f"    Error parsing titledb JSON: {e}")

        return entries

    def build_from_wikidata(self, systems: List[str]) -> None:
        """Query Wikidata for all requested systems."""
        for system in systems:
            if system not in self.WIKIDATA_SYSTEMS:
                self.log(f"  Skipping {system} (no Wikidata QID)")
                continue

            qid = self.WIKIDATA_SYSTEMS[system]
            entries = self.query_wikidata(qid, system, limit=5000)
            self.entries.extend(entries)

            # Polite delay between queries
            time.sleep(1)

    def build_from_titledb(self) -> None:
        """Fetch Switch titles from blawar/titledb."""
        # For now, fetch US English; could extend to other regions later
        entries = self.fetch_titledb_switch("US", "en")
        self.entries.extend(entries)

    def write_output(self) -> None:
        """Write TitleIndexEntry objects to gzipped JSON file."""
        output = {
            "schemaVersion": "1",
            "dataVersion": "20261005",
            "entries": self.entries,
            "count": len(self.entries),
        }

        # Write to gzip
        json_str = json.dumps(output, separators=(",", ":"), ensure_ascii=False)
        json_bytes = json_str.encode("utf-8")

        with gzip.open(self.output_path, "wb") as f:
            f.write(json_bytes)

        uncompressed_size = len(json_bytes) / 1024
        compressed_size = 0
        try:
            with open(self.output_path, "rb") as f:
                compressed_size = len(f.read()) / 1024
        except:
            pass

        self.log(f"\nWrote {len(self.entries)} entries to {self.output_path}")
        self.log(f"  Uncompressed: {uncompressed_size:.1f} KB")
        self.log(f"  Compressed (gzip): {compressed_size:.1f} KB")
        self.log(f"  Compression ratio: {uncompressed_size / max(compressed_size, 0.1):.1f}x")

    def build(self, systems: List[str], include_switch_titledb: bool = True) -> None:
        """Build the title index for specified systems."""
        if not systems:
            systems = list(self.WIKIDATA_SYSTEMS.keys())

        self.log(f"Building title index for {len(systems)} systems: {', '.join(sorted(systems))}")

        # Handle Switch specially: use titledb if requested, then Wikidata
        has_switch = "switch" in systems
        wikidata_systems = [s for s in systems if s in self.WIKIDATA_SYSTEMS]

        # Query Wikidata
        if wikidata_systems:
            self.log("\n=== Querying Wikidata ===")
            self.build_from_wikidata(wikidata_systems)

        # For Switch: prefer titledb (has official IDs), but Wikidata provides more coverage
        if has_switch and include_switch_titledb:
            self.log("\n=== Fetching blawar/titledb (Switch) ===")
            self.build_from_titledb()

        self.log("\n=== Output ===")
        self.write_output()

        # Summary per system
        system_counts = defaultdict(int)
        for entry in self.entries:
            system_counts[entry["system"]] += 1

        self.log("\n=== Entry counts per system ===")
        for system in sorted(system_counts.keys()):
            self.log(f"  {system:15} {system_counts[system]:6} entries")


def main():
    parser = argparse.ArgumentParser(
        description="Build offline game title index from Wikidata and blawar/titledb",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python3 build_title_index.py --all
  python3 build_title_index.py --systems switch ps2 ps1
  python3 build_title_index.py --systems gba nes --out my-index.json.gz
        """
    )
    parser.add_argument(
        "--systems",
        nargs="+",
        default=None,
        help="Systems to include (e.g., switch ps2 gba nes)",
    )
    parser.add_argument(
        "--all",
        action="store_true",
        help="Include all known systems",
    )
    parser.add_argument(
        "--out",
        default="title-index-v1.json.gz",
        help="Output gzipped JSON file path (default: title-index-v1.json.gz)",
    )
    parser.add_argument(
        "--quiet",
        action="store_true",
        help="Suppress verbose output",
    )
    parser.add_argument(
        "--no-titledb",
        action="store_true",
        help="Skip blawar/titledb for Switch (Wikidata only)",
    )

    args = parser.parse_args()

    systems = []
    if args.all:
        systems = list(TitleIndexBuilder.WIKIDATA_SYSTEMS.keys())
    elif args.systems:
        systems = args.systems
    else:
        # Default: just Switch and a few popular systems
        systems = ["switch", "ps2", "ps1"]

    builder = TitleIndexBuilder(args.out, verbose=not args.quiet)
    try:
        builder.build(systems, include_switch_titledb=not args.no_titledb)
    except KeyboardInterrupt:
        builder.log("\nInterrupted by user")
        sys.exit(1)
    except Exception as e:
        builder.log(f"\nFatal error: {e}")
        import traceback
        traceback.print_exc(file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
