package com.example.campuseventmanager.models

data class Attendee(
    val id: String,
    val username: String,
    val email: String,
    val status: String,
    val section: String? = null,
    val studentNumber: String? = null
)
