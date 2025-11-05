package com.example.campuseventmanager.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.campuseventmanager.R

class WelcomeActivity : AppCompatActivity() {

    private lateinit var welcomeMessageText: TextView
    private lateinit var welcomeRoot: RelativeLayout

    private val handler = Handler(Looper.getMainLooper())
    private var intentHasBeenSent = false
    private lateinit var userRole: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        welcomeMessageText = findViewById(R.id.welcome_message_text)
        welcomeRoot = findViewById(R.id.welcome_root)

        val username = intent.getStringExtra("USER_NAME") ?: "User"
        userRole = intent.getStringExtra("USER_ROLE") ?: "student"
        welcomeMessageText.text = "Hello, $username!"

        welcomeRoot.setOnClickListener {
            navigateToDashboard(userRole)
        }

        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        val fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out)

        fadeIn.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                handler.postDelayed({
                    welcomeMessageText.startAnimation(fadeOut)
                }, 1000)
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })

        fadeOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                welcomeMessageText.visibility = View.GONE
                navigateToDashboard(userRole)
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })

        welcomeMessageText.visibility = View.VISIBLE
        welcomeMessageText.startAnimation(fadeIn)
    }

    private fun navigateToDashboard(role: String) {
        if (!intentHasBeenSent) {
            intentHasBeenSent = true
            handler.removeCallbacksAndMessages(null) // Cancel any pending transitions
            val intent = when (role) {
                "organization" -> Intent(this, OrganizationDashboardActivity::class.java)
                else -> Intent(this, StudentDashboardActivity::class.java)
            }
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
