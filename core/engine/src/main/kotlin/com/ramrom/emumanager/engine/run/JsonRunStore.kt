package com.ramrom.emumanager.engine.run

import com.ramrom.emumanager.engine.CompiledPlan
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.File

/**
 * Keeps one run's step statuses in a small JSON file, written atomically. The plan itself isn't
 * stored here: step ids are stable, so the caller recompiles the plan and hands it in.
 */
class JsonRunStore(private val dir: File, private val plan: CompiledPlan) : RunStore {
    private fun file(runId: String) = File(dir, "$runId.json")

    override suspend fun saveSnapshot(snapshot: RunSnapshot) {
        val json = buildJsonObject {
            put("runId", snapshot.runId)
            put("startedAtMs", snapshot.startedAtMs)
            put("isPaused", snapshot.isPaused)
            put("steps", JsonObject(snapshot.steps.mapValues { (_, s) ->
                buildJsonObject {
                    put("status", encode(s.status))
                    put("attempts", s.attempts)
                    s.lastError?.let { put("lastError", it) }
                }
            }))
        }
        dir.mkdirs()
        val tmp = File(dir, "${snapshot.runId}.json.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(file(snapshot.runId))) { file(snapshot.runId).delete(); tmp.renameTo(file(snapshot.runId)) }
    }

    override suspend fun loadSnapshot(runId: String): RunSnapshot? {
        val f = file(runId).takeIf { it.exists() } ?: return null
        return runCatching {
            val o = Json.parseToJsonElement(f.readText()).jsonObject
            RunSnapshot(
                runId = runId,
                plan = plan,
                startedAtMs = o["startedAtMs"]!!.jsonPrimitive.long,
                steps = o["steps"]!!.jsonObject.mapValues { (id, v) ->
                    val s = v.jsonObject
                    StepSnapshot(id, decode(s["status"]!!.jsonObject), s["attempts"]?.jsonPrimitive?.int ?: 0, s["lastError"]?.jsonPrimitive?.content)
                },
                isPaused = o["isPaused"]?.jsonPrimitive?.boolean ?: false,
            )
        }.getOrNull() // A damaged file means starting over from the device's real state, not a crash.
    }

    override suspend fun deleteSnapshot(runId: String) { file(runId).delete() }

    companion object {
        fun encode(s: StepRunStatus): JsonObject = buildJsonObject {
            when (s) {
                StepRunStatus.Pending -> put("k", "pending")
                is StepRunStatus.Running -> { put("k", "running"); put("p", s.progress) }
                StepRunStatus.Verifying -> put("k", "verifying")
                StepRunStatus.Done -> put("k", "done")
                StepRunStatus.AlreadyDone -> put("k", "already")
                is StepRunStatus.NeedsYou -> { put("k", "needs"); put("r", s.reason) }
                is StepRunStatus.Failed -> { put("k", "failed"); put("r", s.reason) }
                StepRunStatus.Skipped -> put("k", "skipped")
                is StepRunStatus.Blocked -> { put("k", "blocked"); put("by", s.by) }
            }
        }

        fun decode(o: JsonObject): StepRunStatus {
            fun str(k: String) = (o[k] as? JsonPrimitive)?.content.orEmpty()
            return when (str("k")) {
                "running" -> StepRunStatus.Running((o["p"] as? JsonPrimitive)?.float ?: 0f)
                "verifying" -> StepRunStatus.Verifying
                "done" -> StepRunStatus.Done
                "already" -> StepRunStatus.AlreadyDone
                "needs" -> StepRunStatus.NeedsYou(str("r"))
                "failed" -> StepRunStatus.Failed(str("r"))
                "skipped" -> StepRunStatus.Skipped
                "blocked" -> StepRunStatus.Blocked(str("by"))
                else -> StepRunStatus.Pending
            }
        }
    }
}
