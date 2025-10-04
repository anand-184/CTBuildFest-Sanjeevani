package com.anand.ctbuildfest

data class SignedData(
    val data: String,
    val signature: String,
    val publicKey: String
)
