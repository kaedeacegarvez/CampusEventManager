package ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.campuseventmanager.AdminDashboardActivity
import com.example.campuseventmanager.R
import com.example.campuseventmanager.models.User
import com.example.campuseventmanager.ui.OrganizationDashboardActivity
import com.example.campuseventmanager.ui.RegisterActivity
import com.example.campuseventmanager.ui.StudentDashboardActivity
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import services.FirebaseService

class LoginActivity : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var registerTextView: TextView
    private lateinit var forgotPasswordTextView: TextView
    private lateinit var contactAdminIcon: ImageView
    private lateinit var infoIcon: ImageView
    private lateinit var firebaseService: FirebaseService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        firebaseService = FirebaseService()

        emailEditText = findViewById(R.id.et_email)
        passwordEditText = findViewById(R.id.et_password)
        loginButton = findViewById(R.id.btn_login)
        registerTextView = findViewById(R.id.tv_register)
        forgotPasswordTextView = findViewById(R.id.tv_forgot_password)
        contactAdminIcon = findViewById(R.id.ic_contact_admin)
        infoIcon = findViewById(R.id.ic_info)

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginUser(email, password)
        }

        registerTextView.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        forgotPasswordTextView.setOnClickListener {
            showForgotPasswordDialog()
        }

        contactAdminIcon.setOnClickListener {
            showContactAdminDialog()
        }

        infoIcon.setOnClickListener {
            showAboutDialog()
        }
    }

    private fun showForgotPasswordDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Forgot Password")
        val view = layoutInflater.inflate(R.layout.dialog_forgot_password, null)
        val emailEditText = view.findViewById<EditText>(R.id.et_email)
        builder.setView(view)
        builder.setPositiveButton("Send") { _, _ ->
            val email = emailEditText.text.toString().trim()
            if (email.isNotEmpty()) {
                sendPasswordResetEmail(email)
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun sendPasswordResetEmail(email: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                firebaseService.sendPasswordResetEmail(email)
            }
            if (result.isSuccess) {
                Toast.makeText(this@LoginActivity, "Password reset email sent successfully.", Toast.LENGTH_LONG).show()
            } else {
                val exception = result.exceptionOrNull()
                val errorMessage = when (exception) {
                    is FirebaseAuthInvalidUserException -> "No account is registered with this email address."
                    else -> exception?.message ?: "An unknown error occurred. Please try again."
                }
                Toast.makeText(this@LoginActivity, "Failed to send email: $errorMessage", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loginUser(email: String, password: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    firebaseService.loginUser(email, password)
                }

                if (result.isSuccess) {
                    val user = withContext(Dispatchers.IO) {
                        firebaseService.getCurrentUser()
                    }

                    if (user != null) {
                        loginSuccess(user)
                    } else {
                        Toast.makeText(this@LoginActivity, "Failed to get user data.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val exception = result.exceptionOrNull()
                    val errorMessage = when (exception) {
                        is FirebaseAuthInvalidUserException -> "No account found with this email."
                        is FirebaseAuthInvalidCredentialsException -> "Incorrect password. Please try again."
                        else -> exception?.message ?: "Login failed. Please try again."
                    }
                    Toast.makeText(this@LoginActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "An unexpected error occurred: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loginSuccess(user: User) {
        Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show()
        val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("userId", user.uid)
        editor.putString("username", user.username)
        editor.putString("email", user.email)
        editor.putString("role", user.role)
        editor.apply()

        val intent = when (user.role) {
            "admin" -> Intent(this, AdminDashboardActivity::class.java)
            "organization" -> Intent(this, OrganizationDashboardActivity::class.java)
            else -> Intent(this, StudentDashboardActivity::class.java)
        }
        startActivity(intent)
        finish()
    }

    private fun showContactAdminDialog() {
        AlertDialog.Builder(this)
            .setTitle("Organization Registration")
            .setMessage("To register as an organization, please reach out through:\n\n" +
                    "📘 Facebook: Ace Garvez\n" +
                    "📧 Email: allancedrickgarvez@gmail.com")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("About This Application")
            .setMessage("The Campus Event Management application was developed as part of an academic project. This system aims to streamline the organization, coordination, and monitoring of campus events within the academic community. It serves as a practical demonstration of applying information technology concepts to enhance event management efficiency and user engagement.\n\nDeveloped by BS Computer Engineering students of ICCT Colleges.")
            .setPositiveButton("Close", null)
            .show()
    }
}