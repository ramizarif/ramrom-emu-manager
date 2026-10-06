package com.ramrom.emumanager.engine

import com.ramrom.emumanager.engine.inventory.FileLister
import com.ramrom.emumanager.knowledge.KnowledgeBundle
import kotlinx.serialization.json.Json

/** The real bundle, as shipped. */
val testBundle: KnowledgeBundle by lazy {
    val text = Thread.currentThread().contextClassLoader!!.getResourceAsStream("knowledge/bundle-v1.json")!!.bufferedReader().readText()
    Json { ignoreUnknownKeys = true }.decodeFromString(KnowledgeBundle.serializer(), text)
}

/** A file tree from a list of absolute file paths; directories are implied. */
class FakeFiles(vararg paths: String) : FileLister {
    private val files = paths.toSet()
    private val dirs = files.flatMap { p -> generateSequence(p.substringBeforeLast('/')) { d -> d.substringBeforeLast('/', "").ifEmpty { null } }.toList() }.toSet()
    override fun listFiles(folderPath: String) = files.filter { it.startsWith("$folderPath/") }.map { it.removePrefix("$folderPath/") }
    override fun exists(path: String) = path in files || path in dirs
    override fun isDirectory(path: String) = path in dirs
}
