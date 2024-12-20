package com.example.obdtwo

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.provider.Settings
import android.content.Intent
import android.net.Uri
import android.view.View
import java.util.Timer
import java.util.TimerTask

class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothHelper: BluetoothHelper
    private lateinit var deviceListAdapter: ArrayAdapter<String>

    // Progress bars
    private lateinit var green_left_progress_bar: ProgressBar
    private lateinit var orange_left_progress_bar: ProgressBar
    private lateinit var red_middle_progress_bar: ProgressBar
    private lateinit var orange_right_progress_bar: ProgressBar
    private lateinit var green_right_progress_bar: ProgressBar

    private lateinit var rpm_number: TextView
    private lateinit var startBubbleButton: Button
    private lateinit var stopBubbleButton: Button
    private lateinit var scanButton: Button
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var disconnectButton: Button

    private var pollingTimer: Timer? = null
    private var buttonsVisible = true

    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Find the progress bars
        green_left_progress_bar = findViewById(R.id.green_left_progress_bar)
        orange_left_progress_bar = findViewById(R.id.orange_left_progress_bar)
        red_middle_progress_bar = findViewById(R.id.red_middle_progress_bar)
        orange_right_progress_bar = findViewById(R.id.orange_right_progress_bar)
        green_right_progress_bar = findViewById(R.id.green_right_progress_bar)

        rpm_number = findViewById(R.id.rpm_number)
        // Find buttons
        startBubbleButton = findViewById(R.id.start_bubble_button)
        stopBubbleButton = findViewById(R.id.stop_bubble_button)
        scanButton = findViewById(R.id.scan_button)
        disconnectButton = findViewById(R.id.disconnect_button)
        startButton = findViewById(R.id.start_button)
        stopButton = findViewById(R.id.stop_button)

        // Initialize BluetoothHelper
        bluetoothHelper = BluetoothHelper(this)

        // Handle window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val scanButton: Button = findViewById(R.id.scan_button)
        scanButton.setOnClickListener {
            if (bluetoothHelper.hasLocationPermissions() && bluetoothHelper.hasBluetoothPermissions()) {
                bluetoothHelper.enableBluetoothAndScan { bluetoothHelper.startBluetoothDiscovery() }
            } else {
                requestPermissions()
            }
        }

        // Prepare the list adapter to show discovered devices
        deviceListAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        bluetoothHelper.setDeviceListAdapter(deviceListAdapter)

        // Register Bluetooth receivers
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(bluetoothHelper.bluetoothDiscoveryReceiver, filter)

        val bondFilter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        registerReceiver(bluetoothHelper.bluetoothBondReceiver, bondFilter)

        val startBubbleButton: Button = findViewById(R.id.start_bubble_button)
        val stopBubbleButton: Button = findViewById(R.id.stop_bubble_button)

        startBubbleButton.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                val intent = Intent(this, FloatingBubbleService::class.java)
                startService(intent)
            } else {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
            }
        }

        stopBubbleButton.setOnClickListener {
            val intent = Intent(this, FloatingBubbleService::class.java)
            stopService(intent)
        }

        startButton.setOnClickListener {
            if (!bluetoothHelper.isConnected()) {
                Toast.makeText(this, "Not connected to OBD-II adapter.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startPollingData()
            Toast.makeText(this, "Started polling data.", Toast.LENGTH_SHORT).show()
        }

        // Stop button: Stop polling
        stopButton.setOnClickListener {
            pollingTimer?.cancel()
            pollingTimer = null
            Toast.makeText(this, "Stopped polling data.", Toast.LENGTH_SHORT).show()
        }

        // Disconnect button: Disconnect from device
        disconnectButton.setOnClickListener {
            bluetoothHelper.disconnect()
            Toast.makeText(this, "Disconnected from device.", Toast.LENGTH_SHORT).show()
        }

        // Set OnClickListener on rpm_number to toggle buttons visibility
        rpm_number.setOnClickListener {
            toggleButtonsVisibility()
        }
    }

    private fun toggleButtonsVisibility() {
        buttonsVisible = !buttonsVisible
        val visibility = if (buttonsVisible) View.VISIBLE else View.GONE
        startBubbleButton.visibility = visibility
        stopBubbleButton.visibility = visibility
        scanButton.visibility = visibility
        disconnectButton.visibility = visibility
        startButton.visibility = visibility
        stopButton.visibility = visibility
    }

    // Register for permission results
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            bluetoothHelper.enableBluetoothAndScan { bluetoothHelper.startBluetoothDiscovery() }
        } else {
            Log.e("MainActivity", "Permissions were denied by the user.")
            Toast.makeText(this, "Permissions were denied. Please grant permissions to continue.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestPermissions() {
        requestPermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun startPollingData() {
        pollingTimer?.cancel()
        pollingTimer = Timer()
        pollingTimer?.schedule(object : TimerTask() {
            override fun run() {
                val rpmResponse = bluetoothHelper.sendObdCommand("010C")
                val rpm = parseRPM(rpmResponse)

                runOnUiThread {
                    // Update main bars here as before
                    updateProgressBars(rpm)

                    // Convert RPM to a single digit by dividing by 10
                    // If rpm = null, treat it as 0
                    val currentRpm = rpm ?: 0
                    val rpmDigit = currentRpm / 10  // integer division
                    rpm_number.setText(rpmDigit.toString())

                    // Send broadcast to update bubble service as before
                    val intent = Intent("com.example.obdtwo.UPDATE_RPM")
                    intent.putExtra("rpm", currentRpm)
                    sendBroadcast(intent)
                }
            }
        }, 0, 200) // every 250 ms or every 1 second as needed
    }



    private fun updateProgressBars(rpmValue: Int?) {
        if (rpmValue == null) {
            // If RPM is null, set all to zero
            green_left_progress_bar.progress = 0
            orange_left_progress_bar.progress = 0
            red_middle_progress_bar.progress = 0
            orange_right_progress_bar.progress = 0
            green_right_progress_bar.progress = 0
            return
        }

        val rpm = rpmValue.coerceIn(0, 60)

        when {
            rpm <= 30 -> {
                green_left_progress_bar.progress = rpm
                orange_left_progress_bar.progress = 0
                red_middle_progress_bar.progress = 0
                orange_right_progress_bar.progress = 0
                green_right_progress_bar.progress = rpm
            }
            rpm in 31..50 -> {
                green_left_progress_bar.progress = 30
                orange_left_progress_bar.progress = rpm - 30
                red_middle_progress_bar.progress = 0
                orange_right_progress_bar.progress = rpm - 30
                green_right_progress_bar.progress = 30
            }
            rpm in 51..60 -> {
                green_left_progress_bar.progress = 30
                orange_left_progress_bar.progress = 20
                red_middle_progress_bar.progress = rpm - 50
                orange_right_progress_bar.progress = 20
                green_right_progress_bar.progress = 30
            }
        }
    }

    private fun parseRPM(response: String): Int? {
        val parts = response.trim().split(" ")
        return if (parts.size >= 4 && parts[0] == "41" && parts[1] == "0C") {
            val A = parts[2].toIntOrNull(16) ?: return null
            val B = parts[3].toIntOrNull(16) ?: return null
            ((A * 256) + B) / 4
        } else null
    }

    private fun parseSpeed(response: String): Int? {
        val parts = response.trim().split(" ")
        return if (parts.size >= 3 && parts[0] == "41" && parts[1] == "0D") {
            parts[2].toIntOrNull(16)
        } else null
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothHelper.bluetoothDiscoveryReceiver)
        unregisterReceiver(bluetoothHelper.bluetoothBondReceiver)
        pollingTimer?.cancel()
    }
}
