package com.ramrom.emumanager.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class WishlistItemStatus {
    WISHLIST,
    OWNED,
    COMPLETED
}

/**
 * WishlistItem: a game wish list entry.
 * Note: no source URL field by design.
 */
@Entity(
    tableName = "wishlist_items",
    indices = [
        Index(value = ["system"])
    ]
)
data class WishlistItem(
    @PrimaryKey
    val id: String,
    val system: String,
    val name: String,
    val titleKey: String? = null,
    val status: WishlistItemStatus = WishlistItemStatus.WISHLIST,
    val notes: String = "",
    val tags: List<String> = emptyList(), // JSON
    val addedAt: Long,
    val ownedGameId: String? = null
)
