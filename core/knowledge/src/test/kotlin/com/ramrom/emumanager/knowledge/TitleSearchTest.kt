package com.ramrom.emumanager.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleSearchTest {
    private val index = TitleSearch(listOf(
        TitleIndexEntry("switch", "The Legend of Zelda: Breath of the Wild", "US", 2017),
        TitleIndexEntry("switch", "The Legend of Zelda: Breath of the Wild", "EU", 2017),
        TitleIndexEntry("wiiu", "The Legend of Zelda: Breath of the Wild", "US", 2017),
        TitleIndexEntry("switch", "Zelda's Adventure Pack", "US", 2020),
        TitleIndexEntry("ps2", "Ōkami", "JP", 2006),
        TitleIndexEntry("gc", "Pokémon Colosseum", "US", 2003),
        TitleIndexEntry("ps2", "Ratchet & Clank", "US", 2002),
        TitleIndexEntry("switch", "Pokémon: Let’s Go, Pikachu!", "US", 2018),
    ))

    @Test fun wordPrefixesMatch() = assertEquals("The Legend of Zelda: Breath of the Wild", index.search("zel bre").first().name)
    @Test fun regionsCollapse() = assertEquals(listOf("US", "EU"), index.search("breath", setOf("switch")).single().regions)
    @Test fun sameNameOnTwoSystemsStaysTwo() = assertEquals(2, index.search("breath of the wild").size)
    @Test fun systemFilter() = assertTrue(index.search("zelda", setOf("wiiu")).all { it.system == "wiiu" })
    @Test fun accentsDontMatter() = assertEquals("Ōkami", index.search("okami").single().name)
    @Test fun accentsInTitleDontMatter() = assertEquals("Pokémon Colosseum", index.search("pokemon colo").single().name)
    @Test fun ampersandIsAnd() = assertEquals("Ratchet & Clank", index.search("ratchet and clank").single().name)
    @Test fun titleStartingWithQueryRanksFirst() = assertEquals("Zelda's Adventure Pack", index.search("zelda").first().name)
    @Test fun emptyQueryFindsNothing() = assertTrue(index.search("  ").isEmpty())
    @Test fun curlyApostrophes() = assertEquals("Pokémon: Let’s Go, Pikachu!", index.search("lets go pika").single().name)
    @Test fun noMatch() = assertTrue(index.search("halo").isEmpty())

    @Test fun verdictsAreConservative() {
        assertEquals(Verdict.Great, Verdicts.forSystem("gba", "sd865"))
        assertEquals(Verdict.Varies, Verdicts.forSystem("switch", "sd8gen2"))
        assertEquals(Verdict.Good, Verdicts.forSystem("switch", "sd8elite"))
        assertEquals(Verdict.Tough, Verdicts.forSystem("switch", "sd865"))
        assertEquals(Verdict.Good, Verdicts.forSystem("ps2", "sd8gen2"))
        assertEquals(Verdict.Varies, Verdicts.forSystem("ps2", "generic"))
    }
}
