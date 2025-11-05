package com.example.campuseventmanager

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.example.campuseventmanager.models.Event
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import services.FirebaseService

class OrganizationEventAdapter(
    private val context: Context,
    private var events: List<Event>,
    private val firebaseService: FirebaseService,
    private val onEdit: (event: Event) -> Unit,
    private val onDelete: (event: Event) -> Unit,
    private val onViewAttendees: (event: Event) -> Unit,
    private val onGenerateQr: (event: Event) -> Unit
) : BaseAdapter() {

    private val inflater = LayoutInflater.from(context)
    private val colors: IntArray = context.resources.getIntArray(R.array.event_colors)
    private val currentUserId: String? = FirebaseAuth.getInstance().currentUser?.uid
    private val listenerRegistrations = mutableMapOf<String, ListenerRegistration>()

    fun updateEvents(newEvents: List<Event>) {
        this.events = newEvents
        notifyDataSetChanged()
    }

    override fun getCount(): Int = events.size
    override fun getItem(position: Int): Any = events[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View
        val holder: ViewHolder

        if (convertView == null) {
            view = inflater.inflate(R.layout.item_organization_event_with_header, parent, false)
            holder = ViewHolder(
                cardView = view.findViewById(R.id.cardView),
                headerDate = view.findViewById(R.id.headerDate),
                titleView = view.findViewById(R.id.eventTitle),
                descView = view.findViewById(R.id.eventDesc),
                dateView = view.findViewById(R.id.eventDate),
                timeView = view.findViewById(R.id.eventTime),
                endTimeView = view.findViewById(R.id.eventEndTime),
                venueView = view.findViewById(R.id.eventVenue),
                capacityView = view.findViewById(R.id.eventCapacity),
                editButton = view.findViewById(R.id.btnEdit),
                deleteButton = view.findViewById(R.id.btnDelete),
                viewAttendeesButton = view.findViewById(R.id.btnViewAttendees),
                generateQrButton = view.findViewById(R.id.btnGenerateQr)
            )
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        val event = events[position]

        // Remove any existing listener for this view holder
        holder.listenerRegistration?.remove()

        // Set background color
        val color = colors[position % colors.size]
        holder.cardView.setCardBackgroundColor(color)

        // Set Date Header
        if (position == 0 || events[position - 1].date != event.date) {
            holder.headerDate.visibility = View.VISIBLE
            holder.headerDate.text = event.date
        } else {
            holder.headerDate.visibility = View.GONE
        }

        // Set Event Details
        holder.titleView.text = event.title
        holder.descView.text = event.description
        holder.dateView.text = event.date
        holder.timeView.text = "Starts: ${event.time}"
        holder.endTimeView.text = "Ends: ${event.endTime ?: "N/A"}"
        holder.venueView.text = "@ ${event.venue}"

        // Set button listeners
        if (currentUserId == event.organizerId) {
            holder.editButton.isEnabled = true
            holder.deleteButton.isEnabled = true
            holder.viewAttendeesButton.isEnabled = true
            holder.generateQrButton.isEnabled = true
            holder.editButton.setOnClickListener { onEdit(event) }
            holder.deleteButton.setOnClickListener { onDelete(event) }
            holder.viewAttendeesButton.setOnClickListener { onViewAttendees(event) }
            holder.generateQrButton.setOnClickListener { onGenerateQr(event) }
        } else {
            holder.editButton.isEnabled = false
            holder.deleteButton.isEnabled = false
            holder.viewAttendeesButton.isEnabled = false
            holder.generateQrButton.isEnabled = false
            holder.editButton.setOnClickListener(null)
            holder.deleteButton.setOnClickListener(null)
            holder.viewAttendeesButton.setOnClickListener(null)
            holder.generateQrButton.setOnClickListener(null)
        }

        val listener = firebaseService.getAttendanceCountRealTime(event.id) { attendance ->
            holder.capacityView.text = context.getString(R.string.event_capacity, attendance, event.capacity?.toString() ?: "∞")
        }
        holder.listenerRegistration = listener
        listenerRegistrations[event.id] = listener


        return view
    }

    fun cleanup() {
        for (listener in listenerRegistrations.values) {
            listener.remove()
        }
        listenerRegistrations.clear()
    }

    private class ViewHolder(
        val cardView: CardView,
        val headerDate: TextView,
        val titleView: TextView,
        val descView: TextView,
        val dateView: TextView,
        val timeView: TextView,
        val endTimeView: TextView,
        val venueView: TextView,
        val capacityView: TextView,
        val editButton: Button,
        val deleteButton: Button,
        val viewAttendeesButton: Button,
        val generateQrButton: Button,
        var listenerRegistration: ListenerRegistration? = null
    )
}
