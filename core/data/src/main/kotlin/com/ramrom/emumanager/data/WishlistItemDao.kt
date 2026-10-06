package com.ramrom.emumanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for WishlistItem.
 */
@Dao
interface WishlistItemDao {
    @Query("SELECT * FROM wishlist_items WHERE id = :id")
    fun getItemById(id: String): Flow<WishlistItem?>

    @Query("SELECT * FROM wishlist_items WHERE system = :system ORDER BY name")
    fun getItemsBySystem(system: String): Flow<List<WishlistItem>>

    @Query("SELECT * FROM wishlist_items WHERE status = :status ORDER BY addedAt DESC")
    fun getItemsByStatus(status: WishlistItemStatus): Flow<List<WishlistItem>>

    @Query("SELECT * FROM wishlist_items ORDER BY system, name")
    fun getAllItems(): Flow<List<WishlistItem>>

    @Query("SELECT * FROM wishlist_items WHERE name LIKE :query ORDER BY name LIMIT :limit")
    fun searchByName(query: String, limit: Int = 50): Flow<List<WishlistItem>>

    @Insert
    suspend fun insert(item: WishlistItem)

    @Update
    suspend fun update(item: WishlistItem)

    @Delete
    suspend fun delete(item: WishlistItem)
}
