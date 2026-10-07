package com.ramrom.emumanager.engine.install

/**
 * Compares an installed versionName with a release tag. Apps report versions their own way (Eden a
 * commit hash, Azahar "2125.0", melonDS "2.0.1 GH"), so we only answer when both read as numbers.
 */
object Versions {
    private val numbers = Regex("""\d+(?:\.\d+)*""")
    private val prefix = Regex("""^(?:v|b|beta|alpha)[-_ ]?(?=\d)""", RegexOption.IGNORE_CASE)

    /** The leading number, only if it stands alone: "2.0.1 GH" yes, "1f6734c" (a hash) no. */
    private fun parts(v: String): List<Int>? {
        // "v1.2", and the beta tags frontends use: Cocoon reports "b2.2" and tags "beta-3.07".
        val t = v.trim().replace(prefix, "")
        val m = numbers.find(t) ?: return null
        if (m.range.first != 0 || t.getOrNull(m.range.last + 1)?.isLetter() == true) return null
        return m.value.split('.').mapNotNull { it.toIntOrNull() }.takeIf { it.isNotEmpty() }
    }

    /** True: latest is newer. False: same or older. Null: can't tell. */
    fun isNewer(installed: String, latest: String): Boolean? {
        if (installed.isBlank() || latest.isBlank()) return null
        val a = parts(installed) ?: return null
        val b = parts(latest) ?: return null
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return y > x
        }
        return false
    }
}
