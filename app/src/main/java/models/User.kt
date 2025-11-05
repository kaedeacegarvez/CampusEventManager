package com.example.campuseventmanager.models

data class User(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val role: String = "",
    val section: String? = null,
    val studentNumber: String? = null
)
