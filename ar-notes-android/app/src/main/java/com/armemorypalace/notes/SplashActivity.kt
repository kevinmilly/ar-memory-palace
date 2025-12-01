package com.armemorypalace.notes

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AlphaAnimation
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Get views
        val icon = findViewById<ImageView>(R.id.splashIcon)
        val mainTagline = findViewById<TextView>(R.id.mainTagline)
        val subTagline = findViewById<TextView>(R.id.subTagline)
        val loading = findViewById<ProgressBar>(R.id.loadingIndicator)

        // Fade-in animations
        val fadeIn = AlphaAnimation(0f, 1f)
        fadeIn.duration = 800

        // Animate elements sequentially
        icon.startAnimation(fadeIn)
        icon.alpha = 1f

        Handler(Looper.getMainLooper()).postDelayed({
            mainTagline.startAnimation(fadeIn)
            mainTagline.alpha = 1f
        }, 300)

        Handler(Looper.getMainLooper()).postDelayed({
            subTagline.startAnimation(fadeIn)
            subTagline.alpha = 1f
        }, 600)

        Handler(Looper.getMainLooper()).postDelayed({
            loading.startAnimation(fadeIn)
            loading.alpha = 1f
        }, 900)

        // Navigate to MainActivity after 3 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            // Fade transition
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, 3000)
    }
}
