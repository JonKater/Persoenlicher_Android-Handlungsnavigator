package com.example.data

import kotlinx.coroutines.flow.Flow

class ActionRepository(private val actionDao: ActionDao) {
    val pendingActions: Flow<List<ActionEntity>> = actionDao.getPendingActions()
    val completedActions: Flow<List<ActionEntity>> = actionDao.getCompletedActions()

    suspend fun insert(action: ActionEntity) = actionDao.insertAction(action)
    suspend fun update(action: ActionEntity) = actionDao.updateAction(action)
    suspend fun deleteById(id: Int) = actionDao.deleteActionById(id)
}
