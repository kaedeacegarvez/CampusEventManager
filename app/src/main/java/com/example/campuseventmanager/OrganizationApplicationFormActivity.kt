package com.example.campuseventmanager

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.example.campuseventmanager.databinding.ActivityOrganizationApplicationFormBinding
import com.example.campuseventmanager.models.OrganizationApplication
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class OrganizationApplicationFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrganizationApplicationFormBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrganizationApplicationFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        binding.submitApplicationButton.setOnClickListener {
            submitApplication()
        }

        binding.backButton.setOnClickListener {
            finish()
        }
    }

    private fun submitApplication() {
        val orgName = binding.orgNameEditText.text.toString().trim()
        val orgContact = binding.orgContactEditText.text.toString().trim()
        val orgDocuments = binding.orgDocumentsEditText.text.toString().trim()
        val userId = auth.currentUser?.uid

        if (orgName.isNotEmpty() && orgContact.isNotEmpty() && orgDocuments.isNotEmpty() && userId != null) {
            val application = OrganizationApplication(
                userId = userId,
                orgName = orgName,
                orgContact = orgContact,
                orgDocuments = orgDocuments
            )

            firestore.collection("orgApplications")
                .add(application)
                .addOnSuccessListener {
                    Toast.makeText(this, "Application submitted successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
        }
    }
}