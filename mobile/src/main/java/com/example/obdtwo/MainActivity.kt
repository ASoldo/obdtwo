package com.example.obdtwo

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.provider.Settings
import android.content.Intent
import android.net.Uri


class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothHelper: BluetoothHelper
    private lateinit var deviceListAdapter: ArrayAdapter<String>
    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 100
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize BluetoothHelper
        bluetoothHelper = BluetoothHelper(this)

        // Handling window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Find the scan button and set the click listener
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
                // Request permission to draw overlays
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

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothHelper.bluetoothDiscoveryReceiver)
        unregisterReceiver(bluetoothHelper.bluetoothBondReceiver)
    }
}
