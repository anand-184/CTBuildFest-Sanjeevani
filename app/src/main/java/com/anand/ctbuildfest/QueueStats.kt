package com.anand.ctbuildfest

data class QueueStats(
    val totalQueued: Int,
    val oldestAlert: Long?,
    val highestRetryCount: Int
)
