package com.example.campuseventmanager.ui

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.campuseventmanager.AttendeeAdapter
import com.example.campuseventmanager.EventDecorator
import com.example.campuseventmanager.OrganizationEventAdapter
import com.example.campuseventmanager.R
import com.example.campuseventmanager.models.Attendee
import com.example.campuseventmanager.models.Event
import com.example.campuseventmanager.models.Organization
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.ListenerRegistration
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import services.FirebaseService
import ui.LoginActivity
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Locale

class OrganizationDashboardActivity : AppCompatActivity() {

    private lateinit var calendarView: MaterialCalendarView
    private lateinit var selectedDateText: TextView
    private lateinit var firebaseService: FirebaseService
    private lateinit var btnLogout: ImageView
    private lateinit var btnProfile: ImageView
    private lateinit var btnAddEvent: MaterialButton
    private val allEvents = mutableListOf<Event>()
    private var eventAdapter: OrganizationEventAdapter? = null
    private var organization: Organization? = null
    private var selectedDate: CalendarDay? = null
    private var currentDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_organization_dashboard)

        firebaseService = FirebaseService()
        calendarView = findViewById(R.id.calendarView)
        selectedDateText = findViewById(R.id.selectedDateText)
        btnLogout = findViewById(R.id.btnLogout)
        btnProfile = findViewById(R.id.btnProfile)
        btnAddEvent = findViewById(R.id.addEventBtn)

        CoroutineScope(Dispatchers.Main).launch {
            val currentUser = withContext(Dispatchers.IO) { firebaseService.getCurrentUser() }
            if (currentUser != null) {
                organization = Organization(currentUser)
                setupRealTimeListener()
            } else {
                // Handle user not found
            }
        }

        calendarView.setOnDateChangedListener { _, date, _ ->
            selectedDate = date
            val selectedDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date.date)
            selectedDateText.text = "Events for $selectedDateStr"
            showEventsForDate(date)
        }

        btnLogout.setOnClickListener {
            showLogoutDialog()
        }

        btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        btnAddEvent.setOnClickListener {
            startActivity(Intent(this, AddEditEventActivity::class.java))
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showLogoutDialog()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        eventAdapter?.cleanup()
    }

    private fun setupRealTimeListener() {
        firebaseService.getAllEventsRealTime { events ->
            val previouslySelectedDate = selectedDate
            allEvents.clear()
            allEvents.addAll(events)
            updateCalendarDecorators()

            previouslySelectedDate?.let { date ->
                if (currentDialog?.isShowing == true) {
                    val selectedDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date.date)
                    val eventsForDate = allEvents.filter { it.date == selectedDateStr }

                    if (eventsForDate.isEmpty()) {
                        currentDialog?.dismiss()
                    } else {
                        (eventAdapter as? OrganizationEventAdapter)?.updateEvents(eventsForDate)
                    }
                }
            }
        }
    }

    private fun updateCalendarDecorators() {
        calendarView.removeDecorators()
        val eventsByDate = allEvents.groupBy { it.date }
        val colorResources = listOf(
            R.drawable.event_background_1,
            R.drawable.event_background_2,
            R.drawable.event_background_3,
            R.drawable.event_background_4,
            R.drawable.event_background_5
        )

        var colorIndex = 0
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        for (dateString in eventsByDate.keys) {
            try {
                val date = dateFormat.parse(dateString)
                date?.let {
                    val day = CalendarDay.from(it)
                    val drawable = ContextCompat.getDrawable(this, colorResources[colorIndex % colorResources.size])!!
                    val dates = hashSetOf(day)
                    calendarView.addDecorator(EventDecorator(this, drawable, dates))
                    colorIndex++
                }
            } catch (e: Exception) {
                // Log error or handle gracefully
            }
        }
    }

    private fun showEventsForDate(date: CalendarDay) {
        val selectedDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date.date)
        val eventsForDate = allEvents.filter { it.date == selectedDateStr }

        if (eventsForDate.isEmpty()) {
            Toast.makeText(this, "No events for this date", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_event_list, null)
        val eventListView = dialogView.findViewById<ListView>(R.id.eventListView)
        val dialogTitle = dialogView.findViewById<TextView>(R.id.dialogTitle)
        dialogTitle.text = "Events for $selectedDateStr"

        eventAdapter = OrganizationEventAdapter(
            context = this,
            events = eventsForDate,
            firebaseService = firebaseService,
            onEdit = { event -> navigateToAddEdit(event) },
            onDelete = { event -> deleteEvent(event) },
            onViewAttendees = { event -> showAttendeesDialog(event) },
            onGenerateQr = { event -> generateQrCode(event) }
        )
        eventListView.adapter = eventAdapter

        currentDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .setOnDismissListener {
                eventAdapter?.cleanup()
                eventAdapter = null
                currentDialog = null
                selectedDate = null
            }
            .create()

        currentDialog?.show()

        val layoutParams = WindowManager.LayoutParams()
        layoutParams.copyFrom(currentDialog?.window?.attributes)
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
        currentDialog?.window?.attributes = layoutParams
    }

    private fun generateQrCode(event: Event) {
        val intent = Intent(this, GenerateQrCodeActivity::class.java)
        intent.putExtra("eventId", event.id)
        startActivity(intent)
    }

    private fun openEventDetails(event: Event) {
        val intent = Intent(this, EventDetailsActivity::class.java)
        intent.putExtra("eventId", event.id)
        startActivity(intent)
    }

    private fun navigateToAddEdit(event: Event) {
        val intent = Intent(this, AddEditEventActivity::class.java)
        intent.putExtra("eventId", event.id)
        startActivity(intent)
    }

    private fun deleteEvent(event: Event) {
        AlertDialog.Builder(this)
            .setTitle("Delete Event")
            .setMessage("Are you sure you want to delete '${event.title}'?")
            .setPositiveButton("Yes") { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    val result = withContext(Dispatchers.IO) {
                        firebaseService.deleteEvent(event.id)
                    }
                    if (result.isSuccess) {
                        Toast.makeText(this@OrganizationDashboardActivity, "Event deleted successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@OrganizationDashboardActivity, "Failed to delete event: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("No", null)
            .show()
    }


    private fun showAttendeesDialog(event: Event) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_attendees, null)
        val searchEditText = dialogView.findViewById<TextView>(R.id.searchEditText)
        val attendeesListView = dialogView.findViewById<ListView>(R.id.attendeesListView)
        val adapter = AttendeeAdapter(this, emptyList())
        attendeesListView.adapter = adapter

        var attendeesListener: ListenerRegistration? = null
        var currentAttendees = listOf<Attendee>()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Attendees for ${event.title}")
            .setView(dialogView)
            .setNeutralButton("Close", null)
            .setPositiveButton("Export") { _, _ ->
                exportAttendeesToCsv(event, currentAttendees)
            }
            .setNegativeButton("Delete Registered/Absent") { _, _ ->
                deleteRegisteredAndAbsentAttendees(event, currentAttendees)
            }
            .setOnDismissListener { attendeesListener?.remove() }
            .create()

        dialog.show()

        val exportButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val deleteButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        exportButton.isEnabled = false
        deleteButton.visibility = View.GONE

        attendeesListener = firebaseService.getEventAttendeesRealTime(event.id) { attendees ->
            currentAttendees = attendees.sortedWith(compareBy({ it.section }, { it.username }))
            adapter.updateAttendees(currentAttendees)

            exportButton.isEnabled = currentAttendees.isNotEmpty()
            val canDelete = currentAttendees.any { it.status == "registered" || it.status == "absent" }
            deleteButton.visibility = if (canDelete) View.VISIBLE else View.GONE
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter.filter(s)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        attendeesListView.setOnItemClickListener { _, _, position, _ ->
            val selectedAttendee = adapter.getItem(position)
            if (selectedAttendee != null) {
                showAttendeeActionDialog(event, selectedAttendee)
                dialog.dismiss()
            }
        }

        val layoutParams = WindowManager.LayoutParams()
        layoutParams.copyFrom(dialog.window?.attributes)
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
        dialog.window?.attributes = layoutParams
    }


    private fun deleteRegisteredAndAbsentAttendees(event: Event, attendees: List<Attendee>) {
        val userIdsToDelete = attendees
            .filter { it.status == "registered" || it.status == "absent" }
            .map { it.id }

        if (userIdsToDelete.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Confirm Deletion")
                .setMessage("Are you sure you want to delete all registered and absent attendees?")
                .setPositiveButton("Yes") { _, _ ->
                    CoroutineScope(Dispatchers.Main).launch {
                        val result = withContext(Dispatchers.IO) {
                            firebaseService.removeAttendeesFromEvent(event.id, userIdsToDelete)
                        }
                        if (result.isSuccess) {
                            Toast.makeText(this@OrganizationDashboardActivity, "Registered and absent attendees removed.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@OrganizationDashboardActivity, "Failed to remove attendees.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("No", null)
                .show()
        }
    }

    private fun showAttendeeActionDialog(event: Event, attendee: Attendee) {
        val actions = mutableListOf("Change Status")
        if (attendee.status == "registered" || attendee.status == "absent") {
            actions.add("Delete Attendee")
        }

        AlertDialog.Builder(this)
            .setTitle(attendee.username)
            .setItems(actions.toTypedArray()) { _, which ->
                when (actions[which]) {
                    "Change Status" -> {
                        val newStatus = when (attendee.status) {
                            "registered" -> "present"
                            "present" -> "absent"
                            "absent" -> "present"
                            else -> "present"
                        }
                        CoroutineScope(Dispatchers.Main).launch {
                            val result = withContext(Dispatchers.IO) {
                                firebaseService.updateAttendanceStatus(event.id, attendee.id, newStatus)
                            }
                            if (result.isSuccess) {
                                Toast.makeText(this@OrganizationDashboardActivity, "${attendee.username} marked as $newStatus", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@OrganizationDashboardActivity, "Failed to update status", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    "Delete Attendee" -> {
                        AlertDialog.Builder(this)
                            .setTitle("Delete Attendee")
                            .setMessage("Are you sure you want to delete ${attendee.username}?")
                            .setPositiveButton("Yes") { _, _ ->
                                CoroutineScope(Dispatchers.Main).launch {
                                    val result = withContext(Dispatchers.IO) {
                                        firebaseService.removeAttendeeFromEvent(event.id, attendee.id)
                                    }
                                    if (result.isSuccess) {
                                        Toast.makeText(this@OrganizationDashboardActivity, "${attendee.username} has been removed.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(this@OrganizationDashboardActivity, "Failed to remove attendee.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .setNegativeButton("No", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun exportAttendeesToCsv(event: Event, attendees: List<Attendee>) {
        if (attendees.isEmpty()) {
            Toast.makeText(this, "There are no attendees to export.", Toast.LENGTH_SHORT).show()
            return
        }
        val sanitizedTitle = event.title.replace(Regex("[^a-zA-Z0-9]"), "_")
        val fileName = "${sanitizedTitle}_attendees.csv"
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)

        try {
            FileWriter(file).use { writer ->
                writer.append("Section,Student Number,Full Name,Status\n")
                for (attendee in attendees) {
                    writer.append("\"${attendee.section ?: ""}\",\"${attendee.studentNumber ?: ""}\",\"${attendee.username}\",\"${attendee.status}\"\n")
                }
            }
            Toast.makeText(this, "Exported to Downloads folder", Toast.LENGTH_SHORT).show()

            val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "text/csv")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(intent, "Open CSV with"))

        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
