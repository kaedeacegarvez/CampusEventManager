package com.example.campuseventmanager

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import com.example.campuseventmanager.models.Attendee
import java.util.Locale

//                                                                    --- FIX IS HERE ---
// Wrap the 'attendees' parameter with `.toMutableList()` to ensure the superclass gets a modifiable list.
class AttendeeAdapter(context: Context, attendees: List<Attendee>) : ArrayAdapter<Attendee>(context, 0, attendees.toMutableList()) {

    // A copy of the original, unfiltered list for filtering logic.
    private var originalAttendees: List<Attendee> = ArrayList(attendees)

    // This method replaces the internal list of the ArrayAdapter.
    // It is the correct way to update data in a custom ArrayAdapter.
    fun updateAttendees(newAttendees: List<Attendee>) {
        // 1. Clear the adapter's internal list. This will now succeed.
        clear()

        // 2. Update the reference list for filtering
        originalAttendees = ArrayList(newAttendees)

        // 3. Add all new items to the adapter's internal list
        addAll(newAttendees)

        // 4. Notify that the data has changed (addAll often does this, but being explicit is safe)
        notifyDataSetChanged()
    }

    // No need to override getCount() or getItem() if you manage data with clear()/addAll().
    // The parent ArrayAdapter correctly handles this.

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_attendee, parent, false)

        // The parent getItem() will correctly retrieve the attendee from the filtered list.
        val attendee = getItem(position)

        val sectionTextView = view.findViewById<TextView>(R.id.attendeeSection)
        val studentNumberTextView = view.findViewById<TextView>(R.id.attendeeStudentNumber)
        val nameTextView = view.findViewById<TextView>(R.id.attendeeName)
        val statusTextView = view.findViewById<TextView>(R.id.attendeeStatus)

        // Set text, with null checks for safety
        sectionTextView.text = attendee?.section ?: "N/A"
        studentNumberTextView.text = attendee?.studentNumber ?: "N/A"
        nameTextView.text = attendee?.username ?: "No Name"
        statusTextView.text = attendee?.status ?: "No Status"

        return view
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                val query = constraint?.toString()?.trim()?.lowercase(Locale.getDefault())

                val filteredList = if (query.isNullOrEmpty()) {
                    // If the query is empty, return the original full list
                    originalAttendees
                } else {
                    // Otherwise, filter the original list
                    originalAttendees.filter { attendee ->
                        (attendee.username?.lowercase(Locale.getDefault())?.contains(query) == true) ||
                                (attendee.section?.lowercase(Locale.getDefault())?.contains(query) == true) ||
                                (attendee.studentNumber?.lowercase(Locale.getDefault())?.contains(query) == true)
                    }
                }

                results.values = filteredList
                results.count = filteredList.size
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                // Clear the adapter's existing data
                clear()
                // Add the new filtered results
                if (results?.values != null) {
                    val filteredData = results.values as List<Attendee>
                    addAll(filteredData)
                }
                // Notify the adapter that the dataset has changed
                notifyDataSetChanged()
            }
        }
    }
}
