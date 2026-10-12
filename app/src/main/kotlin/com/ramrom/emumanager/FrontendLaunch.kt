package com.ramrom.emumanager

import android.content.Context
import android.content.Intent
import com.ramrom.emumanager.engine.Plan
import com.ramrom.emumanager.knowledge.KnowledgeBundle

/** The frontend "Open …" buttons start: where the games are played from. */
data class FrontendLaunch(val name: String, val intent: Intent)

/**
 * The plan's first installed frontend (the user's order, ES-DE first when chosen), else any
 * installed one; one health says is working comes before one that isn't pointed at the games
 * yet. Null when there's none, and those buttons don't show.
 */
fun frontendLaunch(context: Context, bundle: KnowledgeBundle?, plan: Plan?, report: com.ramrom.emumanager.run.HealthReport? = null): FrontendLaunch? {
    bundle ?: return null
    val notReady = report?.frontends.orEmpty().filter { !it.ready }.map { it.id }.toSet()
    val order = (plan?.frontends(bundle)?.ifEmpty { null } ?: bundle.frontends).sortedBy { it.id in notReady }
    return order.firstNotNullOfOrNull { f ->
        f.packages.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }?.let { FrontendLaunch(f.name.ifEmpty { f.id }, it) }
    }
}
