package com.ramrom.emumanager.nav

import androidx.compose.runtime.mutableStateListOf
import com.ramrom.emumanager.ui.layout.Section

/** Every place the app can be. First-run routes come from SCREENS.md's navigation map. */
sealed interface Route {
    data object Welcome : Route
    data object Device : Route
    data object ChoosePath : Route
    data class Access(val next: Route) : Route
    /** S06–S10. `otherDevice` names the target when planning for a handheld this isn't. */
    data class PlanFrontend(val otherDevice: String? = null) : Route
    data class PlanSystems(val otherDevice: String? = null) : Route
    data class PlanGames(val otherDevice: String? = null) : Route
    data class PlanBios(val otherDevice: String? = null) : Route
    data class PlanReview(val otherDevice: String? = null) : Route
    data object PlanImport : Route
    /** S12 and S13. */
    data object Wizard : Route
    data class Guided(val stepId: String) : Route
    /** S18's per-system game list. */
    data class LibrarySystem(val systemId: String) : Route
    /** S19, by the game's base name in its system folder. */
    data class GameDetail(val systemId: String, val baseName: String) : Route
    /** S21 for [com.ramrom.emumanager.run.ConfigWriter.pending]. */
    data object ApplyPreview : Route
    /** S22, all changes or one game's. */
    data class ChangeHistory(val gameId: String? = null) : Route
    /** S33. */
    data class AppDetail(val appId: String) : Route
    /** S23 and S24. */
    data object ImportGames : Route
    data object ImportProgress : Route
    /** S16 What we found. */
    data object Adopt : Route
    /** S14 and S15. */
    data object Health : Route
    data class Issue(val issueId: String) : Route
    data class Main(val section: Section) : Route
    data object About : Route
    /** S40: the open-source software inside the app, and each licence's full text. */
    data object Licenses : Route
    data class LicenseText(val file: String, val title: String) : Route
}

/** A plain back stack. B pops; reaching the root lets Android handle back (leave the app). */
class Navigator(start: Route) {
    val stack = mutableStateListOf(start)
    val current: Route get() = stack.last()
    val canPop: Boolean get() = stack.size > 1

    fun push(r: Route) { stack += r }
    fun pop() { if (canPop) stack.removeAt(stack.lastIndex) }
    /** Replace the whole stack, e.g. when setup finishes and Home becomes the root. */
    fun reset(r: Route) { stack.clear(); stack += r }
    /** Swap the top route, e.g. one guided step for the next, so B still goes back to S12. */
    fun replace(r: Route) { stack[stack.lastIndex] = r }
    fun switchSection(s: Section) { stack[stack.lastIndex] = Route.Main(s) }
}
