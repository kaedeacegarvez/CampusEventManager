package com.example.campuseventmanager.models

import com.google.firebase.firestore.DocumentId

data class OrganizationApplication(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val orgName: String = "",
    val orgContact: String = "",
    val orgDocuments: String = ""
)