package com.example.campuseventmanager.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.campuseventmanager.R
import com.example.campuseventmanager.databinding.ActivityEventDetailsBinding
import com.example.campuseventmanager.models.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import services.FirebaseService

class EventDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEventDetailsBinding
    private lateinit var firebaseService: FirebaseService
    private var eventId: String = ""
    private var event: Event? = null
    private var fromNotification = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseService = FirebaseService()
        eventId = intent.getStringExtra("eventId") ?: ""
        fromNotification = intent.getBooleanExtra("from_notification", false)

        if (eventId.isNotEmpty()) {
            loadEvent()
        } else {
            Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show()
            handleBackPressed()
        }

        binding.btnJoinLeave.setOnClickListener {
            event?.let { event ->
                showConfirmationDialog(event)
            }
        }

        binding.btnGenerateQr.setOnClickListener {
            val intent = Intent(this, GenerateQrCodeActivity::class.java)
            intent.putExtra("eventId", eventId)
            startActivity(intent)
        }

        val backArrowIcon = findViewById<ImageView>(R.id.back_arrow_icon)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                this@EventDetailsActivity.handleBackPressed()
            }
        })
        backArrowIcon.setOnClickListener { handleBackPressed() }
    }

    private fun handleBackPressed() {
        if (fromNotification) {
            val intent = Intent(this, StudentDashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        } else {
            finish()
        }
    }

    private fun loadEvent() {
        CoroutineScope(Dispatchers.Main).launch {
            val loadedEvent = withContext(Dispatchers.IO) {
                firebaseService.getEventById(eventId)
            }
            event = loadedEvent
            loadedEvent?.let { event ->
                binding.headerDate.text = event.date.uppercase()
                binding.tvTitle.text = event.title
                binding.tvDesc.text = event.description
                binding.tvTime.text = "Time: ${event.time}"
                binding.tvEndTime.text = "End Time: ${event.endTime ?: "-"}"
                binding.tvDate.text = "Date: ${event.date}"
                binding.tvVenue.text = "Venue: ${event.venue}"
                binding.tvParticipants.text = "For: ${event.participants ?: "Everyone"}"
                val capacityText = if (event.capacity == null || event.capacity == 0) "Unlimited" else event.capacity.toString()
                binding.tvCapacity.text = "Capacity: $capacityText"

                // Fetch and set the organization name
                event.organizerId?.let {
                    val organizationName = withContext(Dispatchers.IO) { firebaseService.getOrganizationName(it) }
                    binding.tvOrganization.text = "Organization: ${organizationName ?: "N/A"}"
                }

                // Load registration status
                val isRegistered = withContext(Dispatchers.IO) {
                    firebaseService.isUserRegistered(event.id)
                }
                binding.btnJoinLeave.text = if (isRegistered) "Leave" else "Join"

                // Show/Hide Generate QR button based on user role
                val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                val role = sharedPreferences.getString("role", "student")
                if (role == "organization") {
                    binding.btnGenerateQr.visibility = View.VISIBLE
                }

            } ?: run {
                Toast.makeText(this@EventDetailsActivity, "Event not found", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun showConfirmationDialog(event: Event) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val isRegistered = withContext(Dispatchers.IO) { firebaseService.isUserRegistered(event.id) }
                val action = if (isRegistered) "leave" else "join"

                AlertDialog.Builder(this@EventDetailsActivity)
                    .setTitle("Confirm Action")
                    .setMessage("Are you sure you want to $action this event?")
                    .setPositiveButton("Yes") { _, _ ->
                        toggleEventRegistration(event, isRegistered)
                    }
                    .setNegativeButton("No", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this@EventDetailsActivity, "Could not verify registration status. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleEventRegistration(event: Event, isRegistered: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = if (isRegistered) {
                withContext(Dispatchers.IO) {
                    firebaseService.unregisterFromEvent(event.id)
                }
            } else {
                withContext(Dispatchers.IO) {
                    firebaseService.registerForEvent(event.id)
                }
            }

            if (result.isSuccess) {
                val newIsRegistered = !isRegistered
                val action = if (newIsRegistered) "joined" else "left"
                Toast.makeText(this@EventDetailsActivity, "You have $action the event.", Toast.LENGTH_SHORT).show()
                binding.btnJoinLeave.text = if (newIsRegistered) "Leave" else "Join"
            } else {
                Toast.makeText(this@EventDetailsActivity, "Action failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}