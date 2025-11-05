package com.example.campuseventmanager

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.campuseventmanager.databinding.ActivityAdminDashboardBinding
import com.example.campuseventmanager.models.OrganizationApplication
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import services.FirebaseService
import ui.LoginActivity

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: OrganizationApplicationAdapter
    private lateinit var firebaseService: FirebaseService
    private var applicationList = mutableListOf<OrganizationApplication>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        firebaseService = FirebaseService()

        setupRecyclerView()
        loadApplications()

        binding.btnLogoutAdmin.setOnClickListener {
            showLogoutDialog()
        }

        binding.rejectAllButton.setOnClickListener {
            rejectAllApplications()
        }
    }

    private fun setupRecyclerView() {
        adapter = OrganizationApplicationAdapter(this, applicationList)
        binding.applicationsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.applicationsRecyclerView.adapter = adapter
    }

    private fun loadApplications() {
        firestore.collection("orgApplications")
            .get()
            .addOnSuccessListener { documents ->
                val applications = documents.toObjects(OrganizationApplication::class.java)
                applicationList.clear()
                applicationList.addAll(applications)
                adapter.updateApplications(applicationList)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading applications: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun rejectAllApplications() {
        if (applicationList.isEmpty()) {
            Toast.makeText(this, "No applications to reject.", Toast.LENGTH_SHORT).show()
            return
        }

        val batch: WriteBatch = firestore.batch()
        val collectionRef = firestore.collection("orgApplications")

        for (application in applicationList) {
            val docRef = collectionRef.document(application.id)
            batch.delete(docRef)
        }

        batch.commit()
            .addOnSuccessListener {
                applicationList.clear()
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "All applications have been rejected.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error rejecting applications: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Yes") { _, _ ->
                firebaseService.logout()
                val sharedPref = getSharedPreferences("user_prefs", MODE_PRIVATE)
                sharedPref.edit().clear().apply()
                Toast.makeText(this, "Logout Successful", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("No", null)
            .show()
    }
}