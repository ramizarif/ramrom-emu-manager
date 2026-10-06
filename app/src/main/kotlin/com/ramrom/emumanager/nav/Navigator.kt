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
    data class PlanReview(val otherDevice: String? = null) : Route
    data class Main(val section: Section) : Route
    data object About : Route
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
    fun switchSection(s: Section) { stack[stack.lastIndex] = Route.Main(s) }
}
