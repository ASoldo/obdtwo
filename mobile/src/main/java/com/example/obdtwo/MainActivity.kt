package com.example.obdtwo

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }
    private val TAG = "OBD2Bluetooth"
    private lateinit var deviceListAdapter: ArrayAdapter<String>
    private val deviceList = mutableListOf<BluetoothDevice>()

    // Register the activity result launcher to enable Bluetooth
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startBluetoothDiscovery()
        } else {
            Log.e(TAG, "Bluetooth enabling was denied by the user.")
        }
    }

    // Register for permission results
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            enableBluetoothAndScan()
        } else {
            Log.e(TAG, "Permissions were denied by the user.")
            Toast.makeText(this, "Permissions were denied. Please grant permissions to continue.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Handling window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Find the scan button and set the click listener
        val scanButton: Button = findViewById(R.id.scan_button)
        scanButton.setOnClickListener {
            if (hasLocationPermissions() && hasBluetoothPermissions()) {
                enableBluetoothAndScan()
            } else {
                requestPermissions()
            }
        }

        // Register Bluetooth discovery receiver
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(bluetoothDiscoveryReceiver, filter)

        // Register bonding state change receiver
        val bondFilter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        registerReceiver(bluetoothBondReceiver, bondFilter)

        // Prepare the list adapter to show discovered devices
        deviceListAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)
    }

    private fun hasBluetoothPermissions(): Boolean {
        val bluetoothScanPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
        val bluetoothConnectPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
        return bluetoothScanPermission == PackageManager.PERMISSION_GRANTED &&
                bluetoothConnectPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun hasLocationPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fineLocation == PackageManager.PERMISSION_GRANTED && coarseLocation == PackageManager.PERMISSION_GRANTED
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

    private fun enableBluetoothAndScan() {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth is not supported on this device.")
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_SHORT).show()
            return
        }

        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        } else {
            startBluetoothDiscovery()
        }
    }

    private fun startBluetoothDiscovery() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Bluetooth SCAN permission not granted.")
            Toast.makeText(this, "Bluetooth SCAN permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        bluetoothAdapter?.startDiscovery()
        Toast.makeText(this, "Scanning for devices...", Toast.LENGTH_SHORT).show()

        // Clear the list of previously discovered devices
        deviceList.clear()
        deviceListAdapter.clear()
    }

    // Bluetooth discovery receiver
    private val bluetoothDiscoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_FOUND) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                device?.let {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        try {
                            val deviceName = it.name ?: "Unknown Device"
                            val deviceAddress = it.address
                            Log.d(TAG, "Device found: $deviceName, $deviceAddress")

                            // Add the discovered device to the list
                            deviceList.add(it)
                            deviceListAdapter.add("$deviceName ($deviceAddress)")
                            deviceListAdapter.notifyDataSetChanged()

                            // Show the device selection dialog if new devices are found
                            showDeviceSelectionDialog()
                        } catch (e: SecurityException) {
                            Log.e(TAG, "Permission denied to access device properties: ${e.message}")
                        }
                    } else {
                        Log.e(TAG, "BLUETOOTH_CONNECT permission is not granted. Cannot access device properties.")
                    }
                }
            }
        }
    }

    // Show a dialog with the list of discovered devices
    private fun showDeviceSelectionDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select a device to pair")
        builder.setAdapter(deviceListAdapter) { _, position ->
            val device = deviceList[position]

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val deviceName = device.name ?: "Unknown Device"
                val deviceAddress = device.address
                Log.d(TAG, "Selected device: $deviceName, $deviceAddress")

                Toast.makeText(this, "Selected device: $deviceName", Toast.LENGTH_SHORT).show()

                if (device.bondState != BluetoothDevice.BOND_BONDED) {
                    Log.d(TAG, "Attempting to pair with $deviceName")
                    device.createBond()
                } else {
                    Log.d(TAG, "Device is already paired.")
                    Toast.makeText(this, "Device is already paired.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    // Bluetooth bond state receiver
    private val bluetoothBondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)

            device?.let {
                if (ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    try {
                        when (bondState) {
                            BluetoothDevice.BOND_BONDING -> Log.d(TAG, "Pairing with ${it.name} in progress...")
                            BluetoothDevice.BOND_BONDED -> Log.d(TAG, "Successfully paired with ${it.name}")
                            BluetoothDevice.BOND_NONE -> Log.d(TAG, "Pairing with ${it.name} failed or canceled.")
                            else -> Log.d(TAG, "Unknown bonding state.")
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Permission denied to access device properties: ${e.message}")
                    }
                } else {
                    Log.e(TAG, "BLUETOOTH_CONNECT permission is not granted. Cannot access device name.")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothDiscoveryReceiver)
        unregisterReceiver(bluetoothBondReceiver)
    }
}
