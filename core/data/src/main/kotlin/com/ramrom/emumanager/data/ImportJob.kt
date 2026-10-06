package com.ramrom.emumanager.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ImportJobStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

enum class ImportItemAction {
    COPY,
    MOVE,
    SKIP
}

enum class ImportItemStatus {
    PENDING,
    IN_PROGRESS,
    DONE,
    FAILED
}

/**
 * ImportJob: a batch import operation.
 */
@Entity(tableName = "import_jobs")
data class ImportJob(
    @PrimaryKey
    val id: String,
    val source: String, // path or identifier
    val options: String = "", // JSON {resumable, includeArchives, etc}
    val status: ImportJobStatus = ImportJobStatus.PENDING
)

/**
 * ImportItem: a single item in an import job (resumable).
 */
@Entity(
    tableName = "import_items",
    foreignKeys = [
        ForeignKey(
            entity = ImportJob::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["jobId"]),
        Index(value = ["status"])
    ]
)
data class ImportItem(
    @PrimaryKey
    val id: String,
    val jobId: String,
    val srcPath: String,
    val detectedSystem: String,
    val confidence: Float = 0.0f,
    val destPath: String = "",
    val action: ImportItemAction,
    val status: ImportItemStatus = ImportItemStatus.PENDING,
    val error: String? = null
)
