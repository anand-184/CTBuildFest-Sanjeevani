package com.anand.ctbuildfest

enum class NetworkMode {
    ONLINE,           // Internet available
    MESH_DENSE,       // 5+ nearby devices
    MESH_SPARSE,      // 1-4 nearby devices
    OFFLINE_ISOLATED  // No connection
}