package com.anand.ctbuildfest

import com.google.android.gms.maps.model.LatLng

data class Report(
    val id: Int,
    val title: String,
    val description: String,
    val location: String,
    val severity: String,
    val timestamp: String,
    val reporterName: String,
    val type: String,
    val latLng: LatLng?
)
