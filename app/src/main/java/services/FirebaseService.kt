package services

import android.util.Log
import com.example.campuseventmanager.models.Attendee
import com.example.campuseventmanager.models.Event
import com.example.campuseventmanager.models.User
import com.google.firebase.Timestamp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FirebaseService {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    private var eventsListener: ListenerRegistration? = null
    private var organizerEventsListener: ListenerRegistration? = null

    companion object {
        private const val TAG = "FirebaseService"
    }

    // ===== USER MANAGEMENT =====
    suspend fun registerUser(email: String, password: String, fullName: String, role: String): Result<Boolean> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val user = authResult.user

            if (user != null) {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(fullName)
                    .build()
                user.updateProfile(profileUpdates).await()

                val userData = hashMapOf(
                    "username" to fullName, // Storing full name in the 'username' field
                    "email" to email,
                    "role" to role,
                    "createdAt" to Date()
                )

                db.collection("users").document(user.uid).set(userData).await()
                Result.success(true)
            } else {
                Result.failure(Exception("User creation failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginUser(email: String, password: String): Result<Boolean> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            Result.success(authResult.user != null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Boolean> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUser(uid: String): User? {
        val userDoc = db.collection("users").document(uid).get().await()
        return if (userDoc.exists()) {
            User(
                uid = uid,
                username = userDoc.getString("username") ?: "",
                email = userDoc.getString("email") ?: "",
                role = userDoc.getString("role") ?: "student",
                section = userDoc.getString("section"),
                studentNumber = userDoc.getString("studentNumber")
            )
        } else {
            null
        }
    }

    suspend fun getOrganizationName(organizerId: String): String? {
        return try {
            val userDoc = db.collection("users").document(organizerId).get().await()
            if (userDoc.exists()) {
                userDoc.getString("organizationName") ?: userDoc.getString("username")
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching organization name for ID: $organizerId", e)
            null
        }
    }

    suspend fun getCurrentUser(): User? {
        val firebaseUser = auth.currentUser
        return if (firebaseUser != null) {
            val userDoc = db.collection("users").document(firebaseUser.uid).get().await()
            if (userDoc.exists()) {
                User(
                    uid = firebaseUser.uid,
                    username = userDoc.getString("username") ?: "",
                    email = firebaseUser.email ?: "",
                    role = userDoc.getString("role") ?: "student",
                    section = userDoc.getString("section"),
                    studentNumber = userDoc.getString("studentNumber")
                )
            } else {
                null
            }
        } else {
            null
        }
    }

    fun logout() {
        eventsListener?.remove()
        organizerEventsListener?.remove()
        auth.signOut()
    }

    suspend fun updateUserToken(userId: String, token: String) {
        try {
            db.collection("users").document(userId).update("fcmToken", token).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user token", e)
        }
    }

    suspend fun getOrganizations(): List<User> {
        return try {
            val querySnapshot = db.collection("users")
                .whereEqualTo("role", "organization")
                .get()
                .await()

            querySnapshot.documents.map { document ->
                User(
                    uid = document.id,
                    username = document.getString("username") ?: "",
                    email = document.getString("email") ?: "",
                    role = document.getString("role") ?: ""
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun updateUser(userId: String, userData: Map<String, Any>): Result<Boolean> {
        return try {
            db.collection("users").document(userId).update(userData).await()
            if (userData.containsKey("username")) {
                val newUsername = userData["username"] as String
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(newUsername)
                    .build()
                auth.currentUser?.updateProfile(profileUpdates)?.await()
            }
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reauthenticate(email: String, currentPassword: String): Result<Boolean> {
        return try {
            val user = auth.currentUser
            if (user != null) {
                val credential = EmailAuthProvider.getCredential(email, currentPassword)
                user.reauthenticate(credential).await()
                Result.success(true)
            } else {
                Result.failure(Exception("User not found"))
            }
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(Exception("Incorrect current password."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePassword(newPassword: String): Result<Boolean> {
        return try {
            auth.currentUser?.updatePassword(newPassword)?.await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ===== EVENT MANAGEMENT =====
    suspend fun createEvent(event: Event): Result<String> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                return Result.failure(Exception("User not authenticated"))
            }

            val eventData = hashMapOf<String, Any?>(
                "title" to event.title,
                "description" to event.description,
                "date" to event.date,
                "time" to event.time,
                "venue" to event.venue,
                "organizerId" to currentUser.uid,
                "createdAt" to Date(),
                "updatedAt" to Date(),
                "endTime" to event.endTime,
                "capacity" to event.capacity,
                "participants" to event.participants
            )

            val documentRef = db.collection("events").add(eventData).await()
            Log.d(TAG, "✅ Event created: ${documentRef.id}")
            Result.success(documentRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating event: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun updateEvent(event: Event): Result<Boolean> {
        return try {
            val eventData = hashMapOf<String, Any?>(
                "title" to event.title,
                "description" to event.description,
                "date" to event.date,
                "time" to event.time,
                "venue" to event.venue,
                "updatedAt" to Date(),
                "endTime" to event.endTime,
                "capacity" to event.capacity,
                "participants" to event.participants
            )

            db.collection("events").document(event.id).update(eventData).await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteEvent(eventId: String): Result<Boolean> {
        return try {
            db.collection("attendance")
                .whereEqualTo("eventId", eventId)
                .get()
                .await()
                .documents
                .forEach { it.reference.delete().await() }
            db.collection("events").document(eventId).delete().await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getEventById(eventId: String): Event? {
        return try {
            val document = db.collection("events").document(eventId).get().await()
            if (document.exists()) {
                val dateField = document.get("date")
                val dateStr = if (dateField is Timestamp) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    sdf.format(dateField.toDate())
                } else {
                    dateField as? String ?: ""
                }
                val timeField = document.get("time")
                val timeStr = if (timeField is Timestamp) {
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    sdf.format(timeField.toDate())
                } else {
                    timeField as? String ?: ""
                }
                val endTimeField = document.get("endTime")
                val endTimeStr = if (endTimeField is Timestamp) {
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    sdf.format(endTimeField.toDate())
                } else {
                    endTimeField as? String
                }
                Event(
                    id = document.id,
                    title = document.getString("title") ?: "",
                    description = document.getString("description") ?: "",
                    date = dateStr,
                    time = timeStr,
                    endTime = endTimeStr,
                    venue = document.getString("venue") ?: "",
                    capacity = document.getLong("capacity")?.toInt(),
                    organizerId = document.getString("organizerId"),
                    participants = document.getString("participants")
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // ===== REAL-TIME LISTENERS =====
    fun getAllEventsRealTime(onEventsChanged: (List<Event>) -> Unit) {
        Log.d(TAG, "🚀 STARTING real-time listener for all events")

        eventsListener?.remove()

        eventsListener = db.collection("events")
            .addSnapshotListener { querySnapshot, error ->
                Log.d(TAG, "📡 REAL-TIME SNAPSHOT RECEIVED - All Events")

                if (error != null) {
                    Log.e(TAG, "❌ REAL-TIME ERROR: ${error.message}")
                    return@addSnapshotListener
                }

                if (querySnapshot == null) {
                    Log.d(TAG, "❌ Query snapshot is null")
                    return@addSnapshotListener
                }

                val events = querySnapshot.documents.mapNotNull { document ->
                    try {
                        val dateField = document.get("date")
                        val dateStr = if (dateField is Timestamp) {
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            sdf.format(dateField.toDate())
                        } else {
                            dateField as? String ?: "No Date"
                        }
                        val timeField = document.get("time")
                        val timeStr = if (timeField is Timestamp) {
                            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                            sdf.format(timeField.toDate())
                        } else {
                            timeField as? String ?: "No Time"
                        }
                        val endTimeField = document.get("endTime")
                        val endTimeStr = if (endTimeField is Timestamp) {
                            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                            sdf.format(endTimeField.toDate())
                        } else {
                            endTimeField as? String
                        }
                        Event(
                            id = document.id,
                            title = document.getString("title") ?: "No Title",
                            description = document.getString("description") ?: "",
                            date = dateStr,
                            time = timeStr,
                            endTime = endTimeStr,
                            venue = document.getString("venue") ?: "No Venue",
                            capacity = document.getLong("capacity")?.toInt(),
                            organizerId = document.getString("organizerId"),
                            participants = document.getString("participants")
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing event document: ${document.id}", e)
                        null
                    }
                }
                val sortedEvents = events.sortedWith(compareBy({ it.date }, { it.time }))
                onEventsChanged(sortedEvents)
            }

        Log.d(TAG, "🎯 Real-time listener registered successfully")
    }

    fun getOrganizerEventsRealTime(organizerId: String, onEventsChanged: (List<Event>) -> Unit) {
        Log.d(TAG, "🚀 STARTING real-time listener for organizer: $organizerId")

        organizerEventsListener?.remove()

        organizerEventsListener = db.collection("events")
            .whereEqualTo("organizerId", organizerId)
            .addSnapshotListener { querySnapshot, error ->
                Log.d(TAG, "📡 REAL-TIME SNAPSHOT RECEIVED - Organizer Events")

                if (error != null) {
                    Log.e(TAG, "❌ REAL-TIME ERROR: ${error.message}")
                    return@addSnapshotListener
                }

                if (querySnapshot == null) {
                    Log.d(TAG, "❌ Query snapshot is null")
                    return@addSnapshotListener
                }

                val events = querySnapshot.documents.mapNotNull { document ->
                    try {
                        val dateField = document.get("date")
                        val dateStr = if (dateField is Timestamp) {
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            sdf.format(dateField.toDate())
                        } else {
                            dateField as? String ?: "No Date"
                        }
                        val timeField = document.get("time")
                        val timeStr = if (timeField is Timestamp) {
                            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                            sdf.format(timeField.toDate())
                        } else {
                            timeField as? String ?: "No Time"
                        }
                        val endTimeField = document.get("endTime")
                        val endTimeStr = if (endTimeField is Timestamp) {
                            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                            sdf.format(endTimeField.toDate())
                        } else {
                            endTimeField as? String
                        }
                        Event(
                            id = document.id,
                            title = document.getString("title") ?: "No Title",
                            description = document.getString("description") ?: "",
                            date = dateStr,
                            time = timeStr,
                            endTime = endTimeStr,
                            venue = document.getString("venue") ?: "No Venue",
                            capacity = document.getLong("capacity")?.toInt(),
                            organizerId = document.getString("organizerId"),
                            participants = document.getString("participants")
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing event document: ${document.id}", e)
                        null
                    }
                }
                val sortedEvents = events.sortedWith(compareBy({ it.date }, { it.time }))
                onEventsChanged(sortedEvents)
            }
    }

    fun getEventRealTime(eventId: String, onEventChanged: (Event?) -> Unit): ListenerRegistration {
        return db.collection("events").document(eventId)
            .addSnapshotListener { document, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for event changes", error)
                    onEventChanged(null)
                    return@addSnapshotListener
                }

                if (document != null && document.exists()) {
                    val dateField = document.get("date")
                    val dateStr = if (dateField is Timestamp) {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        sdf.format(dateField.toDate())
                    } else {
                        dateField as? String ?: ""
                    }
                    val timeField = document.get("time")
                    val timeStr = if (timeField is Timestamp) {
                        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                        sdf.format(timeField.toDate())
                    } else {
                        timeField as? String ?: ""
                    }
                    val endTimeField = document.get("endTime")
                    val endTimeStr = if (endTimeField is Timestamp) {
                        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                        sdf.format(endTimeField.toDate())
                    } else {
                        endTimeField as? String
                    }
                    val event = Event(
                        id = document.id,
                        title = document.getString("title") ?: "",
                        description = document.getString("description") ?: "",
                        date = dateStr,
                        time = timeStr,
                        endTime = endTimeStr,
                        venue = document.getString("venue") ?: "",
                        capacity = document.getLong("capacity")?.toInt(),
                        organizerId = document.getString("organizerId"),
                        participants = document.getString("participants")
                    )
                    onEventChanged(event)
                } else {
                    onEventChanged(null)
                }
            }
    }

    // ===== ATTENDANCE MANAGEMENT =====

    suspend fun attendEvent(eventId: String): Result<Boolean> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("User not authenticated"))

            val attendanceId = "${eventId}_${currentUser.uid}"
            val attendanceDocRef = db.collection("attendance").document(attendanceId)
            val attendanceDoc = attendanceDocRef.get().await()

            if (attendanceDoc.exists()) {
                val currentStatus = attendanceDoc.getString("status")
                if (currentStatus == "present") {
                    return Result.failure(Exception("You are already marked as present for this event."))
                } else {
                    attendanceDocRef.update("status", "present", "checkedInAt", Date()).await()
                    return Result.success(true)
                }
            } else {
                val event = getEventById(eventId)
                val currentAttendance = getAttendanceCount(eventId)

                if (event?.capacity != null && event.capacity > 0 && currentAttendance >= event.capacity) {
                    return Result.failure(Exception("Event is full. Cannot mark attendance."))
                }

                val attendanceData = hashMapOf(
                    "eventId" to eventId,
                    "userId" to currentUser.uid,
                    "status" to "present",
                    "checkedInAt" to Date()
                )
                attendanceDocRef.set(attendanceData).await()
                return Result.success(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in attendEvent", e)
            return Result.failure(e)
        }
    }
    suspend fun registerForEvent(eventId: String): Result<Boolean> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                return Result.failure(Exception("User not authenticated"))
            }

            val event = getEventById(eventId)
            val currentAttendance = getAttendanceCount(eventId)

            if (event != null && event.capacity != null && currentAttendance >= event.capacity) {
                return Result.failure(Exception("Event is full"))
            }

            val attendanceData = hashMapOf(
                "eventId" to eventId,
                "userId" to currentUser.uid,
                "status" to "registered",
                "checkedInAt" to Date()
            )

            val attendanceId = "${eventId}_${currentUser.uid}"
            db.collection("attendance").document(attendanceId).set(attendanceData).await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unregisterFromEvent(eventId: String): Result<Boolean> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                return Result.failure(Exception("User not authenticated"))
            }

            val attendanceId = "${eventId}_${currentUser.uid}"
            db.collection("attendance").document(attendanceId).delete().await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun isUserRegistered(eventId: String): Boolean {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) return false

            val attendanceId = "${eventId}_${currentUser.uid}"
            val document = db.collection("attendance").document(attendanceId).get().await()
            document.exists()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getAttendanceCount(eventId: String): Int {
        return try {
            val querySnapshot = db.collection("attendance")
                .whereEqualTo("eventId", eventId)
                .get()
                .await()
            querySnapshot.documents.size
        } catch (e: Exception) {
            0
        }
    }

    fun getAttendanceCountRealTime(eventId: String, onCountChanged: (Int) -> Unit): ListenerRegistration {
        return db.collection("attendance")
            .whereEqualTo("eventId", eventId)
            .addSnapshotListener { querySnapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Attendance listener error for event $eventId", error)
                    return@addSnapshotListener
                }

                val count = querySnapshot?.size() ?: 0
                onCountChanged(count)
            }
    }

    fun isUserRegisteredRealTime(eventId: String, onResult: (Boolean) -> Unit): ListenerRegistration {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            onResult(false)
            // Return a no-op listener registration if the user is not logged in.
            return ListenerRegistration { }
        }

        val attendanceId = "${eventId}_${currentUser.uid}"
        return db.collection("attendance").document(attendanceId)
            .addSnapshotListener { documentSnapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Listen failed for isUserRegisteredRealTime", error)
                    onResult(false)
                    return@addSnapshotListener
                }

                onResult(documentSnapshot != null && documentSnapshot.exists())
            }
    }

    fun getEventAttendeesRealTime(eventId: String, onAttendeesChanged: (List<Attendee>) -> Unit): ListenerRegistration {
    return db.collection("attendance")
        .whereEqualTo("eventId", eventId)
        .addSnapshotListener { querySnapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening for event attendees", error)
                return@addSnapshotListener
            }

            if (querySnapshot == null) {
                return@addSnapshotListener
            }

            val attendees = mutableListOf<Attendee>()
            val userIds = querySnapshot.documents.mapNotNull { it.getString("userId") }

            if (userIds.isNotEmpty()) {
                db.collection("users").whereIn(FieldPath.documentId(), userIds).get()
                    .addOnSuccessListener { usersSnapshot ->
                        val userMap = usersSnapshot.documents.associateBy { it.id }
                        for (document in querySnapshot.documents) {
                            val userId = document.getString("userId")
                            val status = document.getString("status") ?: "registered"

                            if (userId != null) {
                                val userDoc = userMap[userId]
                                if (userDoc != null) {
                                    attendees.add(
                                        Attendee(
                                            id = userId,
                                            username = userDoc.getString("username") ?: "Unknown",
                                            email = userDoc.getString("email") ?: "",
                                            status = status,
                                            section = userDoc.getString("section"),
                                            studentNumber = userDoc.getString("studentNumber")
                                        )
                                    )
                                }
                            }
                        }
                        onAttendeesChanged(attendees)
                    }
            } else {
                onAttendeesChanged(emptyList())
            }
        }
    }

    suspend fun getRegisteredEvents(): List<Event> {
        return try {
            val currentUser = auth.currentUser ?: return emptyList()

            val attendanceSnapshot = db.collection("attendance")
                .whereEqualTo("userId", currentUser.uid)
                .get()
                .await()

            val eventIds = attendanceSnapshot.documents.mapNotNull { it.getString("eventId") }

            if (eventIds.isEmpty()) return emptyList()

            val eventsSnapshot = db.collection("events")
                .whereIn(FieldPath.documentId(), eventIds)
                .get()
                .await()

            eventsSnapshot.documents.mapNotNull { document ->
                try {
                    val dateField = document.get("date")
                    val dateStr = if (dateField is Timestamp) {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        sdf.format(dateField.toDate())
                    } else {
                        dateField as? String ?: ""
                    }
                    val timeField = document.get("time")
                    val timeStr = if (timeField is Timestamp) {
                        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                        sdf.format(timeField.toDate())
                    } else {
                        timeField as? String ?: ""
                    }
                    val endTimeField = document.get("endTime")
                    val endTimeStr = if (endTimeField is Timestamp) {
                        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                        sdf.format(endTimeField.toDate())
                    } else {
                        endTimeField as? String
                    }
                    Event(
                        id = document.id,
                        title = document.getString("title") ?: "",
                        description = document.getString("description") ?: "",
                        date = dateStr,
                        time = timeStr,
                        endTime = endTimeStr,
                        venue = document.getString("venue") ?: "",
                        capacity = document.getLong("capacity")?.toInt(),
                        organizerId = document.getString("organizerId"),
                        participants = document.getString("participants")
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing registered event document: ${document.id}", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching registered events", e)
            emptyList()
        }
    }

    suspend fun getEventAttendees(eventId: String): List<Attendee> {
        return try {
            val querySnapshot = db.collection("attendance")
                .whereEqualTo("eventId", eventId)
                .get()
                .await()

            val attendees = mutableListOf<Attendee>()
            for (document in querySnapshot.documents) {
                try {
                    val userId = document.getString("userId")
                    val status = document.getString("status") ?: "registered"

                    if (userId == null) {
                        Log.e(TAG, "Skipping attendance record with null userId")
                        continue
                    }

                    val userDoc = db.collection("users").document(userId).get().await()
                    if (userDoc.exists()) {
                        attendees.add(Attendee(
                            id = userId,
                            username = userDoc.getString("username") ?: "Unknown",
                            email = userDoc.getString("email") ?: "",
                            status = status,
                            section = userDoc.getString("section"),
                            studentNumber = userDoc.getString("studentNumber")
                        ))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing attendee record: ${e.message}")
                }
            }
            attendees
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching event attendees: ${e.message}")
            emptyList()
        }
    }

    suspend fun updateAttendanceStatus(eventId: String, userId: String, newStatus: String): Result<Boolean> {
        return try {
            val attendanceId = "${eventId}_${userId}"
            db.collection("attendance").document(attendanceId)
                .update("status", newStatus)
                .await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeAttendeeFromEvent(eventId: String, userId: String): Result<Boolean> {
        return try {
            val attendanceId = "${eventId}_${userId}"
            db.collection("attendance").document(attendanceId).delete().await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeAttendeesFromEvent(eventId: String, userIds: List<String>): Result<Boolean> {
        return try {
            val batch = db.batch()
            for (userId in userIds) {
                val attendanceId = "${eventId}_${userId}"
                val docRef = db.collection("attendance").document(attendanceId)
                batch.delete(docRef)
            }
            batch.commit().await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
