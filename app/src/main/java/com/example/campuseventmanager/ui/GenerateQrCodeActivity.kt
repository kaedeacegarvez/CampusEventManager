package com.example.campuseventmanager.ui

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.campuseventmanager.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import services.FirebaseService

class GenerateQrCodeActivity : AppCompatActivity() {

    private lateinit var qrCodeImageView: ImageView
    private lateinit var tvEventName: TextView
    private lateinit var backButton: Button
    private val firebaseService = FirebaseService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_generate_qr_code)

        qrCodeImageView = findViewById(R.id.qrCodeImageView)
        tvEventName = findViewById(R.id.tvEventName)
        backButton = findViewById(R.id.backButton)

        val eventId = intent.getStringExtra("eventId")

        if (eventId != null) {
            fetchEventAndGenerateQrCode(eventId)
        } else {
            Toast.makeText(this, "Event ID is missing.", Toast.LENGTH_SHORT).show()
            finish()
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    private fun fetchEventAndGenerateQrCode(eventId: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val event = withContext(Dispatchers.IO) {
                firebaseService.getEventById(eventId)
            }
            if (event != null) {
                tvEventName.text = event.title
                generateQrCode(event.id)
            } else {
                Toast.makeText(this@GenerateQrCodeActivity, "Event not found.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun generateQrCode(text: String) {
        val multiFormatWriter = MultiFormatWriter()
        try {
            val bitMatrix = multiFormatWriter.encode(text, BarcodeFormat.QR_CODE, 600, 600)
            val barcodeEncoder = BarcodeEncoder()
            val bitmap: Bitmap = barcodeEncoder.createBitmap(bitMatrix)
            qrCodeImageView.setImageBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to generate QR code.", Toast.LENGTH_SHORT).show()
        }
    }
}