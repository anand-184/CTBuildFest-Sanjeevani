package com.anand.ctbuildfest

import com.google.android.gms.maps.model.LatLng

data class DisasterData(
    val type: String,
    val position: LatLng,
    val severity: String,
    val location: String,
    val description: String
)
