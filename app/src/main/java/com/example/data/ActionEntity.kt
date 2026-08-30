package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "actions")
data class ActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val source: String = "Manual", // e.g. Voice, Image, Text
    val urgencyRisk: Int, // 0-30
    val financial: Int, // 0-25
    val goalFit: Int, // 0-20
    val unblock: Int, // 0-15
    val contextFit: Int, // 0-10
    val uncertainty: Int, // 0-20 (penalty)
    val effortMismatch: Int, // 0-15 (penalty)
    val isHardDeadline: Boolean,
    val deadlineMs: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false
) {
    val totalScore: Int
        get() = urgencyRisk + financial + goalFit + unblock + contextFit - uncertainty - effortMismatch
}
