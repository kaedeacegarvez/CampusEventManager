package com.example.campuseventmanager.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.campuseventmanager.R
import com.example.campuseventmanager.models.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import services.FirebaseService
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddEditEventActivity : AppCompatActivity() {

    private lateinit var titleEditText: EditText
    private lateinit var descriptionEditText: EditText
    private lateinit var dateEditText: EditText
    private lateinit var startTimeEditText: EditText
    private lateinit var endTimeEditText: EditText
    private lateinit var venueEditText: EditText
    private lateinit var capacityEditText: EditText
    private lateinit var participantsEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
    private lateinit var backArrowIcon: ImageView

    private var eventId: String = ""
    private lateinit var firebaseService: FirebaseService
    private var originalEvent: Event? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_event)

        firebaseService = FirebaseService()

        titleEditText = findViewById(R.id.titleEdit)
        descriptionEditText = findViewById(R.id.descEdit)
        dateEditText = findViewById(R.id.dateEdit)
        startTimeEditText = findViewById(R.id.timeEdit)
        endTimeEditText = findViewById(R.id.endTimeEdit)
        venueEditText = findViewById(R.id.venueEdit)
        capacityEditText = findViewById(R.id.capacityEdit)
        participantsEditText = findViewById(R.id.participantsEdit)
        saveButton = findViewById(R.id.saveBtn)
        cancelButton = findViewById(R.id.cancelBtn)
        backArrowIcon = findViewById(R.id.back_arrow_icon)

        setupDatePicker()
        setupTimePicker(startTimeEditText)
        setupTimePicker(endTimeEditText)

        // Check if editing existing event
        eventId = intent.getStringExtra("eventId") ?: ""
        if (eventId.isNotEmpty()) {
            CoroutineScope(Dispatchers.Main).launch {
                val event = withContext(Dispatchers.IO) {
                    firebaseService.getEventById(eventId)
                }
                event?.let {
                    originalEvent = it.copy()
                    titleEditText.setText(it.title)
                    descriptionEditText.setText(it.description)
                    dateEditText.setText(it.date)
                    startTimeEditText.setText(it.time)
                    endTimeEditText.setText(it.endTime ?: "")
                    venueEditText.setText(it.venue)
                    capacityEditText.setText(it.capacity?.takeIf { it > 0 }?.toString() ?: "")
                    participantsEditText.setText(it.participants ?: "")
                }
            }
        }

        saveButton.setOnClickListener { saveEvent() }

        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasUnsavedChanges()) {
                    AlertDialog.Builder(this@AddEditEventActivity)
                        .setTitle("Unsaved Changes")
                        .setMessage("You have unsaved changes. Discard them?")
                        .setPositiveButton("Discard") { _, _ -> finish() }
                        .setNegativeButton("Keep Editing", null)
                        .show()
                } else {
                    finish()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        cancelButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        backArrowIcon.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupDatePicker() {
        dateEditText.isFocusable = false
        dateEditText.isClickable = true
        dateEditText.setOnClickListener {
            val calendar = Calendar.getInstance()
            val datePicker = DatePickerDialog(
                this,
                { _, y, m, d ->
                    val mm = String.format("%02d", m + 1)
                    val dd = String.format("%02d", d)
                    dateEditText.setText("$y-$mm-$dd")
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.datePicker.minDate = calendar.timeInMillis
            val max = Calendar.getInstance()
            max.add(Calendar.YEAR, 2)
            datePicker.datePicker.maxDate = max.timeInMillis
            datePicker.show()
        }
    }

    private fun setupTimePicker(timeEdit: EditText) {
        timeEdit.isFocusable = false
        timeEdit.isClickable = true
        timeEdit.setOnClickListener {
            val c = Calendar.getInstance()
            val hour = c.get(Calendar.HOUR_OF_DAY)
            val minute = c.get(Calendar.MINUTE)
            TimePickerDialog(
                this,
                { _, h, m ->
                    val hh = String.format("%02d", h)
                    val mm = String.format("%02d", m)
                    timeEdit.setText("$hh:$mm")
                },
                hour,
                minute,
                true
            ).show()
        }
    }

    private fun saveEvent() {
        val title = titleEditText.text.toString().trim()
        val description = descriptionEditText.text.toString().trim()
        val date = dateEditText.text.toString().trim()
        val startTime = startTimeEditText.text.toString().trim()
        val endTime = endTimeEditText.text.toString().trim()
        val venue = venueEditText.text.toString().trim()
        val capacityText = capacityEditText.text.toString().trim()
        val participants = participantsEditText.text.toString().trim()

        if (title.isEmpty() || date.isEmpty() || venue.isEmpty() || startTime.isEmpty() || endTime.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        val capacity = if (capacityText.isNotEmpty()) {
            try {
                capacityText.toInt().takeIf { it > 0 }
            } catch (e: NumberFormatException) {
                Toast.makeText(this, "Please enter a valid capacity number", Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            null
        }

        if (!isDateTimeValid(date, startTime, endTime)) return

        val event = Event(
            id = eventId,
            title = title,
            description = description,
            date = date,
            time = startTime,
            endTime = endTime,
            venue = venue,
            capacity = capacity,
            participants = participants
        )

        if (!event.isValid()) {
            Toast.makeText(this, "Please check event details", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            val success = if (eventId.isNotEmpty()) {
                // Update existing event
                val result = withContext(Dispatchers.IO) {
                    firebaseService.updateEvent(event)
                }
                result.isSuccess
            } else {
                // Create new event
                val result = withContext(Dispatchers.IO) {
                    firebaseService.createEvent(event)
                }
                result.isSuccess
            }

            if (success) {
                Toast.makeText(this@AddEditEventActivity, "Event saved successfully!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this@AddEditEventActivity, "Failed to save event", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isDateTimeValid(dateString: String, startTimeString: String, endTimeString: String): Boolean {
        return try {
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

            val startDateTime = dateFormat.parse("$dateString $startTimeString")
            val endDateTime = dateFormat.parse("$dateString $endTimeString")
            val now = Date()

            when {
                startDateTime == null || endDateTime == null -> {
                    Toast.makeText(this, "Invalid date/time format", Toast.LENGTH_SHORT).show()
                    false
                }
                startDateTime.before(now) -> {
                    Toast.makeText(this, "Please select a future start time", Toast.LENGTH_SHORT).show()
                    false
                }
                endDateTime.before(startDateTime) || endDateTime == startDateTime -> {
                    Toast.makeText(this, "End time must be after start time", Toast.LENGTH_SHORT).show()
                    false
                }
                else -> true
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid date/time format", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun hasUnsavedChanges(): Boolean {
        val title = titleEditText.text.toString()
        val description = descriptionEditText.text.toString()
        val date = dateEditText.text.toString()
        val startTime = startTimeEditText.text.toString()
        val endTime = endTimeEditText.text.toString()
        val venue = venueEditText.text.toString()
        val capacity = capacityEditText.text.toString()
        val participants = participantsEditText.text.toString()

        if (eventId.isEmpty()) {
            return title.isNotEmpty() || description.isNotEmpty() || date.isNotEmpty() ||
                    startTime.isNotEmpty() || endTime.isNotEmpty() || venue.isNotEmpty() || capacity.isNotEmpty() || participants.isNotEmpty()
        }

        originalEvent?.let {
            return title != it.title ||
                    description != it.description ||
                    date != it.date ||
                    startTime != it.time ||
                    endTime != (it.endTime ?: "") ||
                    venue != it.venue ||
                    capacity != (it.capacity?.toString() ?: "") ||
                    participants != (it.participants ?: "")
        }
        return true
    }
}
