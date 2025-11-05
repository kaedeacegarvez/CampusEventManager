package com.example.campuseventmanager.ui

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.campuseventmanager.R
import services.FirebaseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.LoginActivity

class RegisterActivity : AppCompatActivity() {

    private lateinit var fullNameEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var registerButton: Button
    private lateinit var loginTextView: TextView
    private lateinit var firebaseService: FirebaseService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        firebaseService = FirebaseService()

        // Initialize views
        fullNameEditText = findViewById(R.id.et_full_name)
        emailEditText = findViewById(R.id.et_email)
        passwordEditText = findViewById(R.id.et_password)
        confirmPasswordEditText = findViewById(R.id.et_confirm_password)
        registerButton = findViewById(R.id.btn_register)
        loginTextView = findViewById(R.id.tv_login)

        registerButton.setOnClickListener {
            registerUser()
        }

        loginTextView.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun registerUser() {
        val fullName = fullNameEditText.text.toString().trim()
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString()
        val confirmPassword = confirmPasswordEditText.text.toString()
        val role = "student"

        // Validation
        if (fullName.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showError("Please fill all fields")
            return
        }

        if (!fullName.matches(Regex("^[a-zA-Z ,_.-]+"))) {
            showError("Full name can only contain letters, commas, underscores, hyphens, and dots.")
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Please enter a valid email address")
            return
        }

        if (password.length < 6) {
            showError("Password must be at least 6 characters")
            return
        }

        if (password != confirmPassword) {
            showError("Passwords do not match")
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    firebaseService.registerUser(email, password, fullName, role)
                }

                if (result.isSuccess) {
                    // Set a flag to indicate that this is a new user
                    val sharedPrefs = getSharedPreferences("user_prefs", MODE_PRIVATE).edit()
                    sharedPrefs.putBoolean("isNewUser", true)
                    sharedPrefs.apply()

                    Toast.makeText(this@RegisterActivity, "Registration successful! Please login.", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@RegisterActivity, LoginActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    showError("Registration failed: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                showError("Registration error: ${e.message}")
            }
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}