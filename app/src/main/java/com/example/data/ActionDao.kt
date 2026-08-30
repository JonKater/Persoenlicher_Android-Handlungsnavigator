package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionDao {
    @Query("SELECT * FROM actions WHERE isCompleted = 0 ORDER BY timestamp DESC")
    fun getPendingActions(): Flow<List<ActionEntity>>

    @Query("SELECT * FROM actions WHERE isCompleted = 1 ORDER BY timestamp DESC")
    fun getCompletedActions(): Flow<List<ActionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: ActionEntity)

    @Update
    suspend fun updateAction(action: ActionEntity)

    @Query("DELETE FROM actions WHERE id = :id")
    suspend fun deleteActionById(id: Int)
}
