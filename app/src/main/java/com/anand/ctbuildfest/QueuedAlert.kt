package com.anand.ctbuildfest

import com.anand.ctbuildfest.network.Alert

data class QueuedAlert(
    val alert: Alert,
    val recipients: List<String>,
    val queuedAt: Long,
    val retryCount: Int
)
