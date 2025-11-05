package com.example.campuseventmanager.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.campuseventmanager.EventDecorator
import com.example.campuseventmanager.R
import com.example.campuseventmanager.StudentEventAdapter
import com.example.campuseventmanager.models.Event
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import services.FirebaseService
import ui.LoginActivity
import java.text.SimpleDateFormat
import java.util.Locale

class StudentDashboardActivity : AppCompatActivity() {

    private lateinit var calendarView: MaterialCalendarView
    private lateinit var selectedDateText: TextView
    private lateinit var firebaseService: FirebaseService
    private lateinit var btnLogout: ImageView
    private lateinit var btnProfile: ImageView
    private lateinit var btnScanQr: ImageView
    private val allEvents = mutableListOf<Event>()
    private var eventListAdapter: StudentEventAdapter? = null
    private var currentDialog: AlertDialog? = null
    private var selectedDate: CalendarDay? = null

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents == null) {
            Toast.makeText(this, "Cancelled", Toast.LENGTH_LONG).show()
        } else {
            val eventId = result.contents
            attendEvent(eventId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_dashboard)

        firebaseService = FirebaseService()
        calendarView = findViewById(R.id.calendarView)
        selectedDateText = findViewById(R.id.selectedDateText)
        btnLogout = findViewById(R.id.btnLogout)
        btnProfile = findViewById(R.id.btnProfile)
        btnScanQr = findViewById(R.id.btnScanQr)

        setupRealTimeListener()

        calendarView.setOnDateChangedListener { _, date, _ ->
            selectedDate = date
            val formattedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date.date)
            selectedDateText.text = "Events for $formattedDate"
            showEventsForDate(date)
        }

        btnLogout.setOnClickListener {
            showLogoutDialog()
        }

        btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        btnScanQr.setOnClickListener {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            options.setPrompt("Scan a QR code")
            options.setCameraId(0)
            options.setBeepEnabled(false)
            options.setBarcodeImageEnabled(true)
            barcodeLauncher.launch(options)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showLogoutDialog()
            }
        })
    }

    private fun attendEvent(eventId: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = firebaseService.attendEvent(eventId)
            if (result.isSuccess) {
                Toast.makeText(this@StudentDashboardActivity, "Attendance marked successfully!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@StudentDashboardActivity, result.exceptionOrNull()?.message, Toast.LENGTH_LONG).show()
            }
        }
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
                        eventListAdapter?.updateEvents(eventsForDate)
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

        eventListAdapter = StudentEventAdapter(
            context = this,
            events = eventsForDate,
            firebaseService = firebaseService,
            onOpenDetails = { event -> openEventDetails(event) }
        )
        eventListView.adapter = eventListAdapter

        currentDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .setOnDismissListener { 
                eventListAdapter?.cleanup()
                eventListAdapter = null
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

    private fun openEventDetails(event: Event) {
        val intent = Intent(this, EventDetailsActivity::class.java)
        intent.putExtra("eventId", event.id)
        startActivity(intent)
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