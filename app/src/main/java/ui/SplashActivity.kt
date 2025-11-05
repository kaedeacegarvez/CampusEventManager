package ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.campuseventmanager.R

class SplashActivity : AppCompatActivity() {

    private lateinit var welcomeText: TextView
    private lateinit var appNameText: TextView
    private lateinit var logoImage: ImageView
    private lateinit var splashRoot: RelativeLayout

    private val handler = Handler(Looper.getMainLooper())
    private var intentHasBeenSent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        welcomeText = findViewById(R.id.welcome_text)
        appNameText = findViewById(R.id.app_name_text)
        logoImage = findViewById(R.id.logo_image)
        splashRoot = findViewById(R.id.splash_root)

        splashRoot.setOnClickListener {
            goToLogin()
        }

        // Start the first animation
        animateWelcome()
    }

    private fun goToLogin() {
        if (!intentHasBeenSent) {
            intentHasBeenSent = true
            handler.removeCallbacksAndMessages(null) // Cancel any pending transitions
            startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
            finish()
        }
    }

    private fun animateWelcome() {
        // 1. Animate "Welcome"
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        val fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out)

        fadeIn.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                // After fade in, wait 1 second, then fade out
                handler.postDelayed({
                    welcomeText.startAnimation(fadeOut)
                }, 1000)
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })

        fadeOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                welcomeText.visibility = View.GONE
                // When "Welcome" has faded out, start the next animation
                animateAppName()
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })

        welcomeText.visibility = View.VISIBLE
        welcomeText.startAnimation(fadeIn)
    }

    private fun animateAppName() {
        // 2. Animate "This is Campus Event Management"
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        val fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out)

        fadeIn.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                // After fade in, wait 1 second, then fade out
                handler.postDelayed({
                    appNameText.startAnimation(fadeOut)
                }, 1000)
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })

        fadeOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                appNameText.visibility = View.GONE
                // When app name has faded out, start the logo animation
                animateLogo()
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })

        appNameText.visibility = View.VISIBLE
        appNameText.startAnimation(fadeIn)
    }

    private fun animateLogo() {
        // 3. Animate the logo
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        fadeIn.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                // After logo fades in, wait 1 second, then go to Login
                handler.postDelayed({
                    goToLogin()
                }, 1000)
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })

        logoImage.visibility = View.VISIBLE
        logoImage.startAnimation(fadeIn)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
