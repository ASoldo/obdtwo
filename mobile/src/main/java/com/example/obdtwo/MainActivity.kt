package com.example.obdtwo

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.provider.Settings
import android.content.Intent
import android.net.Uri
import java.util.Timer
import java.util.TimerTask

class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothHelper: BluetoothHelper
    private lateinit var deviceListAdapter: ArrayAdapter<String>

    private lateinit var rpmProgressBar: ProgressBar
    private lateinit var kmhNumber: EditText
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var disconnectButton: Button

    private var pollingTimer: Timer? = null

    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rpmProgressBar = findViewById(R.id.rpm_progress_bar)
        kmhNumber = findViewById(R.id.kmh_number)
        startButton = findViewById(R.id.start_button)
        stopButton = findViewById(R.id.stop_button)
        disconnectButton = findViewById(R.id.disconnect_button)

        // Initialize BluetoothHelper
        bluetoothHelper = BluetoothHelper(this)

        // Handling window insets
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

        // Register Bluetooth discovery receiver
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(bluetoothHelper.bluetoothDiscoveryReceiver, filter)

        // Register bonding state change receiver
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

                val speedResponse = bluetoothHelper.sendObdCommand("010D")
                val speed = parseSpeed(speedResponse)

                runOnUiThread {
                    rpm?.let {
                        rpmProgressBar.progress = it.coerceAtMost(rpmProgressBar.max)
                    }
                    speed?.let {
                        kmhNumber.setText(it.toString())
                    }
                }
            }
        }, 0, 1000) // every 1 second
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
