package org.hn.wanderingplus.model

data class PlayerTraderInfo(
    val hasTrader: Boolean,
    val currentChance: Double,
    val currentStage: Int,
    val maxStage: Int,
    val queuePosition: Int?,
    val totalSummoned: Int
)
