package com.example.campuseventmanager.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.campuseventmanager.R
import com.example.campuseventmanager.models.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import services.FirebaseService

class EventAdapter(
    private val events: List<Event>,
    private val firebaseService: FirebaseService, // Add FirebaseService
    private val onItemClick: (Event) -> Unit = {}
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val headerDate: TextView = itemView.findViewById(R.id.headerDate)
        val tvTitle: TextView = itemView.findViewById(R.id.eventTitle)
        val tvTime: TextView = itemView.findViewById(R.id.eventTime)
        val tvDate: TextView = itemView.findViewById(R.id.eventDate)
        val tvVenue: TextView = itemView.findViewById(R.id.eventVenue)
        val tvParticipants: TextView = itemView.findViewById(R.id.eventParticipants)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]

        // Bold Header (e.g., OCT 25, 2025)
        holder.headerDate.text = event.date.uppercase()

        // Fields (blank if not filled)
        holder.tvTitle.text = "Event Title: ${event.title.ifBlank { "" }}"
        holder.tvTime.text = "Time: ${event.time.ifBlank { "" }}"
        holder.tvDate.text = "Date: ${event.date.ifBlank { "" }}"
        holder.tvVenue.text = "Venue: ${event.venue.ifBlank { "" }}"

        // Load participant count from Firebase
        CoroutineScope(Dispatchers.Main).launch {
            val participantCount = withContext(Dispatchers.IO) {
                firebaseService.getAttendanceCount(event.id)
            }
            holder.tvParticipants.text = "Participants: $participantCount"
        }

        holder.itemView.setOnClickListener {
            onItemClick(event)
        }
    }

    override fun getItemCount(): Int = events.size
}