package com.example.campuseventmanager.models

import java.util.Date

data class Event(
    val id: String = "",
    val title: String,
    val description: String,
    val date: String,
    val time: String,
    val endTime: String?,
    val venue: String,
    val capacity: Int? = null,
    val organizerId: String? = null,
    val organizationName: String? = null, // Added for organization name
    val participants: String? = null,
    val attendees: String? = null,
    val createdAt: Date? = null,
    val updatedAt: Date? = null
) {
    fun isValid(): Boolean {
        return title.isNotBlank() && date.isNotBlank() && venue.isNotBlank() && time.isNotBlank()
    }
}
