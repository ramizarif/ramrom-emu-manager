package com.ramrom.emumanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for LibraryGame.
 */
@Dao
interface LibraryGameDao {
    @Query("SELECT * FROM library_games WHERE id = :id")
    fun getGameById(id: String): Flow<LibraryGame?>

    @Query("SELECT * FROM library_games WHERE system = :system ORDER BY displayName")
    fun getGamesBySystem(system: String): Flow<List<LibraryGame>>

    @Query("SELECT * FROM library_games ORDER BY system, displayName")
    fun getAllGames(): Flow<List<LibraryGame>>

    @Query("SELECT * FROM library_games WHERE displayName LIKE :query ORDER BY displayName LIMIT :limit")
    fun searchByName(query: String, limit: Int = 50): Flow<List<LibraryGame>>

    @Insert
    suspend fun insert(game: LibraryGame)

    @Update
    suspend fun update(game: LibraryGame)

    @Delete
    suspend fun delete(game: LibraryGame)

    @Query("DELETE FROM library_games WHERE system = :system")
    suspend fun deleteBySystem(system: String)
}
