package com.example.campuseventmanager.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.campuseventmanager.OrganizationApplicationFormActivity
import com.example.campuseventmanager.R
import com.example.campuseventmanager.models.User
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import services.FirebaseService

class ProfileActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var studentNumberInputLayout: TextInputLayout
    private lateinit var studentNumberEditText: EditText
    private lateinit var sectionInputLayout: TextInputLayout
    private lateinit var sectionEditText: EditText
    private lateinit var currentPasswordEditText: EditText
    private lateinit var newPasswordEditText: EditText
    private lateinit var updateProfileButton: Button
    private lateinit var beAnOrganizerButton: MaterialButton
    private lateinit var backButton: ImageView
    private lateinit var firebaseService: FirebaseService
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        firebaseService = FirebaseService()
        auth = FirebaseAuth.getInstance()

        usernameEditText = findViewById(R.id.usernameEditText)
        studentNumberInputLayout = findViewById(R.id.studentNumberInputLayout)
        studentNumberEditText = findViewById(R.id.studentNumberEditText)
        sectionInputLayout = findViewById(R.id.sectionInputLayout)
        sectionEditText = findViewById(R.id.sectionEditText)
        currentPasswordEditText = findViewById(R.id.currentPasswordEditText)
        newPasswordEditText = findViewById(R.id.newPasswordEditText)
        updateProfileButton = findViewById(R.id.updateProfileButton)
        beAnOrganizerButton = findViewById(R.id.beAnOrganizerButton)
        backButton = findViewById(R.id.backButton)

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated. Please log in again.", Toast.LENGTH_LONG).show()
            finish() // Close the activity if the user is not authenticated
            return
        }

        // Check user role and hide fields if organization
        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val role = sharedPreferences.getString("role", "student")
        if (role == "organization") {
            studentNumberInputLayout.visibility = View.GONE
            sectionInputLayout.visibility = View.GONE
            beAnOrganizerButton.visibility = View.GONE
        }

        // Load initial user data
        CoroutineScope(Dispatchers.Main).launch {
            val user: User? = withContext(Dispatchers.IO) { firebaseService.getUser(currentUser.uid) }
            user?.let { loadedUser ->
                usernameEditText.setText(loadedUser.username)
                if (role != "organization") {
                    studentNumberEditText.setText(loadedUser.studentNumber)
                    sectionEditText.setText(loadedUser.section)
                }
            }
        }

        updateProfileButton.setOnClickListener {
            val newUsername = usernameEditText.text.toString().trim()
            val newStudentNumber = studentNumberEditText.text.toString().trim()
            val newSection = sectionEditText.text.toString().trim()
            val currentPassword = currentPasswordEditText.text.toString()
            val newPassword = newPasswordEditText.text.toString()

            if (!newUsername.matches(Regex("^[a-zA-Z ,_.-]+"))) {
                Toast.makeText(this, "Full name can only contain letters, commas, underscores, hyphens, and dots.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Determine what the user is trying to do
            val isUpdatingProfile = newUsername.isNotBlank() || (newStudentNumber.isNotBlank() && role != "organization") || (newSection.isNotBlank() && role != "organization")
            val isUpdatingPassword = newPassword.isNotBlank()

            if (!isUpdatingProfile && !isUpdatingPassword) {
                Toast.makeText(this, "Nothing to update.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            CoroutineScope(Dispatchers.Main).launch {
                // If the user wants to update their password, they MUST provide their current one
                if (isUpdatingPassword && currentPassword.isBlank()) {
                    Toast.makeText(this@ProfileActivity, "Please enter your current password to set a new one.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // If a password is provided, re-authenticate first
                if (currentPassword.isNotBlank()) {
                    val email = currentUser.email
                    if (email == null) {
                        Toast.makeText(this@ProfileActivity, "Cannot update profile without a user email.", Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    val reauthResult = withContext(Dispatchers.IO) {
                        firebaseService.reauthenticate(email, currentPassword)
                    }

                    if (reauthResult.isFailure) {
                        val reauthErrorMessage = reauthResult.exceptionOrNull()?.message ?: "Authentication failed"
                        Toast.makeText(this@ProfileActivity, reauthErrorMessage, Toast.LENGTH_LONG).show()
                        return@launch // Stop the entire process
                    }
                }

                // At this point, re-authentication was successful, or it wasn't needed.
                // Proceed with the updates.
                var profileUpdateSuccess = true
                var passwordUpdateSuccess = true

                // 1. Update Profile Data (if needed)
                if (isUpdatingProfile) {
                    val userData = mutableMapOf<String, Any>()
                    if (newUsername.isNotBlank()) userData["username"] = newUsername
                    if (newStudentNumber.isNotBlank() && role != "organization") userData["studentNumber"] = newStudentNumber
                    if (newSection.isNotBlank() && role != "organization") userData["section"] = newSection

                    val updateResult = withContext(Dispatchers.IO) {
                        firebaseService.updateUser(currentUser.uid, userData)
                    }
                    if (updateResult.isFailure) {
                        profileUpdateSuccess = false
                        val errorMessage = updateResult.exceptionOrNull()?.message ?: "Failed to update profile"
                        Toast.makeText(this@ProfileActivity, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }

                // 2. Update Password (if needed)
                if (isUpdatingPassword) {
                    val passwordResult = withContext(Dispatchers.IO) {
                        firebaseService.updatePassword(newPassword)
                    }
                    if (passwordResult.isFailure) {
                        passwordUpdateSuccess = false
                        val passErrorMessage = passwordResult.exceptionOrNull()?.message ?: "Failed to update password"
                        Toast.makeText(this@ProfileActivity, passErrorMessage, Toast.LENGTH_LONG).show()
                    }
                }

                // 3. Show final success message
                if (profileUpdateSuccess && passwordUpdateSuccess) {
                    Toast.makeText(this@ProfileActivity, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                    currentPasswordEditText.text.clear()
                    newPasswordEditText.text.clear()
                }
            }
        }

        beAnOrganizerButton.setOnClickListener {
            val intent = Intent(this, OrganizationApplicationFormActivity::class.java)
            startActivity(intent)
        }

        backButton.setOnClickListener {
            finish()
        }
    }
}