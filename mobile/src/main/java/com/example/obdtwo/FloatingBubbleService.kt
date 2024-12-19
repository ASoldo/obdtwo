package com.example.obdtwo

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar

class FloatingBubbleService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: View
    private var params: WindowManager.LayoutParams? = null
    private lateinit var rpmProgressBar: ProgressBar

    // BroadcastReceiver to handle RPM updates
    private val rpmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.example.obdtwo.UPDATE_RPM") {
                val rpm = intent.getIntExtra("rpm", 0)
                updateCircularProgress(rpm)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Inflate the bubble layout
        bubbleView = LayoutInflater.from(this).inflate(R.layout.bubble_layout, null)
        rpmProgressBar = bubbleView.findViewById(R.id.rpm_progress_bar)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            x = 0
            y = 0
        }

        bubbleView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var touchX = 0f
            private var touchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params?.x ?: 0
                        initialY = params?.y ?: 0
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params?.x = initialX + (event.rawX - touchX).toInt()
                        params?.y = initialY + (event.rawY - touchY).toInt()

                        windowManager.updateViewLayout(bubbleView, params)
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(bubbleView, params)

        // Register BroadcastReceiver for RPM updates
        val filter = IntentFilter("com.example.obdtwo.UPDATE_RPM")
        registerReceiver(rpmReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(bubbleView)
        unregisterReceiver(rpmReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateCircularProgress(rpm: Int) {
        val value = rpm.coerceIn(0, 60)
        rpmProgressBar.progress = value
    }
}
