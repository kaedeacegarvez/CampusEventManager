package com.example.campuseventmanager

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import com.example.campuseventmanager.models.Event
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import services.FirebaseService
import java.util.concurrent.ConcurrentHashMap

class StudentEventAdapter(
    private val context: Context,
    private var events: List<Event>,
    private val firebaseService: FirebaseService,
    private val onOpenDetails: (event: Event) -> Unit
) : BaseAdapter() {

    private val inflater = LayoutInflater.from(context)
    private val colors: IntArray = context.resources.getIntArray(R.array.event_colors)

    // Use ConcurrentHashMap for thread safety, as listeners fire on different threads.
    private val listeners = ConcurrentHashMap<String, ListenerRegistration>()
    private val coroutineJobs = ConcurrentHashMap<String, Job>()
    private val viewStateCache = ConcurrentHashMap<String, EventViewState>()

    private data class EventViewState(
        val isRegistered: Boolean? = null,
        val attendance: Int? = null,
        val eventData: Event? = null,
        val orgName: String? = null
    )

    fun updateEvents(newEvents: List<Event>) {
        val oldEventIds = events.map { it.id }.toSet()
        val newEventIds = newEvents.map { it.id }.toSet()
        val removedEventIds = oldEventIds - newEventIds

        removedEventIds.forEach { eventId ->
            cleanupForEvent(eventId)
        }

        events = newEvents
        notifyDataSetChanged()
    }

    private fun cleanupForEvent(eventId: String) {
        listeners.remove("registration_$eventId")?.remove()
        listeners.remove("attendance_$eventId")?.remove()
        listeners.remove("event_$eventId")?.remove()
        coroutineJobs.remove("org_$eventId")?.cancel()
        viewStateCache.remove(eventId)
    }

    fun cleanup() {
        listeners.values.forEach { it.remove() }
        listeners.clear()
        coroutineJobs.values.forEach { it.cancel() }
        coroutineJobs.clear()
        viewStateCache.clear()
    }

    override fun getCount(): Int = events.size
    override fun getItem(position: Int): Any = events[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View
        val holder: ViewHolder

        if (convertView == null) {
            view = inflater.inflate(R.layout.item_student_event_with_header, parent, false)
            holder = ViewHolder.from(view)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        val event = getItem(position) as Event

        // Always ensure listeners are attached for the current event.
        // The attachListeners function is now smart enough to not re-attach.
        attachListeners(event)

        // Get the most recent state from the cache.
        val state = viewStateCache.getOrPut(event.id) { EventViewState(eventData = event) }

        // Bind basic data and click listeners
        holder.headerDate.visibility = if (position == 0 || events[position - 1].date != event.date) {
            holder.headerDate.text = event.date
            View.VISIBLE
        } else {
            View.GONE
        }
        holder.cardView.setCardBackgroundColor(colors[position % colors.size])
        holder.detailsButton.setOnClickListener { onOpenDetails(event) }
        holder.joinButton.setOnClickListener { showConfirmationDialog(event) }

        // Update the UI from the state cache
        updateViewHolderUI(holder, state, event)

        return view
    }

    // --- MODIFIED: Listeners are now decoupled from the ViewHolder ---
    private fun attachListeners(event: Event) {
        // Only attach listeners if they don't already exist for this event ID.
        if (listeners.containsKey("registration_${event.id}")) return

        // Listener for registration status
        listeners["registration_${event.id}"] = firebaseService.isUserRegisteredRealTime(event.id) { isRegistered ->
            handleStateUpdate(event.id) { it.copy(isRegistered = isRegistered) }
        }

        // Listener for event data changes (e.g., title, capacity)
        listeners["event_${event.id}"] = firebaseService.getEventRealTime(event.id) { updatedEvent ->
            if (updatedEvent != null) {
                handleStateUpdate(event.id) { it.copy(eventData = updatedEvent) }
            }
        }

        // Listener for attendance count
        listeners["attendance_${event.id}"] = firebaseService.getAttendanceCountRealTime(event.id) { attendance ->
            handleStateUpdate(event.id) { it.copy(attendance = attendance) }
        }

        // Fetch organization name (one-time fetch per event)
        event.organizerId?.let { orgId ->
            coroutineJobs["org_${event.id}"] = CoroutineScope(Dispatchers.Main).launch {
                val orgName = withContext(Dispatchers.IO) { firebaseService.getOrganizationName(orgId) }
                handleStateUpdate(event.id) { it.copy(orgName = orgName) }
            }
        }
    }

    // --- MODIFIED: This function now only updates the cache and triggers a redraw ---
    private fun handleStateUpdate(eventId: String, updateAction: (EventViewState) -> EventViewState) {
        val currentState = viewStateCache.getOrDefault(eventId, EventViewState())
        val newState = updateAction(currentState)
        viewStateCache[eventId] = newState

        // Trigger a redraw of the entire list. getView will use the updated cache.
        // This must be run on the main thread.
        CoroutineScope(Dispatchers.Main).launch {
            notifyDataSetChanged()
        }
    }

    // Renamed to make its purpose clear: only updates the UI, no logic.
    private fun updateViewHolderUI(holder: ViewHolder, state: EventViewState, baseEvent: Event) {
        val event = state.eventData ?: baseEvent

        holder.titleView.text = event.title
        holder.descView.text = event.description
        holder.dateView.text = event.date
        holder.timeView.text = context.getString(R.string.event_starts, event.time)
        holder.endTimeView.text = context.getString(R.string.event_ends, event.endTime ?: "N/A")
        holder.venueView.text = context.getString(R.string.event_venue, event.venue)

        if (event.organizerId != null) {
            holder.organizationView.visibility = View.VISIBLE
            holder.organizationView.text = "Organization: ${state.orgName ?: "Loading..."}"
        } else {
            holder.organizationView.visibility = View.GONE
        }

        holder.eventParticipants.visibility = if (!event.participants.isNullOrEmpty()) {
            holder.eventParticipants.text = context.getString(R.string.event_for, event.participants)
            View.VISIBLE
        } else {
            View.GONE
        }

        val attendance = state.attendance
        val isRegistered = state.isRegistered

        if (attendance != null) {
            val capacity = event.capacity
            val isFull = capacity != null && capacity > 0 && attendance >= capacity

            val capacityText = if (capacity == null || capacity == 0) "∞" else capacity.toString()
            holder.capacityView.text = context.getString(R.string.event_capacity, attendance, capacityText)
            holder.capacityView.visibility = View.VISIBLE

            if (isFull) {
                holder.joinButton.text = context.getString(R.string.full)
                holder.joinButton.isEnabled = false
            } else {
                if (isRegistered != null) {
                    holder.joinButton.text = if (isRegistered) context.getString(R.string.leave) else context.getString(R.string.join)
                    holder.joinButton.isEnabled = true
                } else {
                    holder.joinButton.isEnabled = false // Disabled until we know registration status
                }
            }
        } else {
            holder.capacityView.visibility = View.INVISIBLE // Hide until we have data
            holder.joinButton.isEnabled = false
        }
    }

    // No changes to the functions below
    private fun showConfirmationDialog(event: Event) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val isRegistered = withContext(Dispatchers.IO) { firebaseService.isUserRegistered(event.id) }
                val action = if (isRegistered) "leave" else "join"

                AlertDialog.Builder(context)
                    .setTitle("Confirm Action")
                    .setMessage("Are you sure you want to $action this event?")
                    .setPositiveButton("Yes") { _, _ ->
                        toggleEventRegistration(event, isRegistered)
                    }
                    .setNegativeButton("No", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(context, "Could not verify registration status: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleEventRegistration(event: Event, isRegistered: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = if (isRegistered) {
                withContext(Dispatchers.IO) { firebaseService.unregisterFromEvent(event.id) }
            } else {
                withContext(Dispatchers.IO) { firebaseService.registerForEvent(event.id) }
            }

            if (result.isSuccess) {
                val action = if (isRegistered) "left" else "joined"
                Toast.makeText(context, "You have $action the event.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Action failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private class ViewHolder(
        val cardView: CardView,
        val headerDate: TextView,
        val titleView: TextView,
        val organizationView: TextView,
        val descView: TextView,
        val dateView: TextView,
        val timeView: TextView,
        val endTimeView: TextView,
        val venueView: TextView,
        val eventParticipants: TextView,
        val capacityView: TextView,
        val joinButton: Button,
        val detailsButton: Button
    ) {
        companion object {
            fun from(view: View): ViewHolder {
                return ViewHolder(
                    cardView = view.findViewById(R.id.cardView),
                    headerDate = view.findViewById(R.id.headerDate),
                    titleView = view.findViewById(R.id.eventTitle),
                    organizationView = view.findViewById(R.id.tvOrganization),
                    descView = view.findViewById(R.id.eventDesc),
                    dateView = view.findViewById(R.id.eventDate),
                    timeView = view.findViewById(R.id.eventTime),
                    endTimeView = view.findViewById(R.id.eventEndTime),
                    venueView = view.findViewById(R.id.eventVenue),
                    eventParticipants = view.findViewById(R.id.eventParticipants),
                    capacityView = view.findViewById(R.id.eventCapacity),
                    joinButton = view.findViewById(R.id.btnJoin),
                    detailsButton = view.findViewById(R.id.btnDetails)
                )
            }
        }
    }
}
