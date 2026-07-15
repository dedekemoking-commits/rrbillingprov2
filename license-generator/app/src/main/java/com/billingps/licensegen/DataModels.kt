package com.billingps.licensegen

data class LicenseRecord(
    val id: String = "",
    val kode: String = "",
    val payload: String = "",
    val signature: String = "",
    val paket: String = "",
    val username: String = "",
    val email: String = "",
    val expiry: String = "",
    val generatedBy: String = "",
    val generatedAt: Long = 0,
    val activatedAt: Long = 0,
)

data class FirestoreUser(
    val username: String = "",
    val role: String = "",
    val email: String = "",
    val dibuat: String = "",
)
