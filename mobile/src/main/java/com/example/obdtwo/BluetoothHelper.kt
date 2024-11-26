package com.example.obdtwo

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class BluetoothHelper(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }
    private val TAG = "OBD2Bluetooth"
    private val deviceList = mutableListOf<BluetoothDevice>()
    private lateinit var deviceListAdapter: ArrayAdapter<String>

    fun setDeviceListAdapter(adapter: ArrayAdapter<String>) {
        deviceListAdapter = adapter
    }

    fun hasBluetoothPermissions(): Boolean {
        val bluetoothScanPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
        val bluetoothConnectPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
        return bluetoothScanPermission == PackageManager.PERMISSION_GRANTED &&
                bluetoothConnectPermission == PackageManager.PERMISSION_GRANTED
    }

    fun hasLocationPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fineLocation == PackageManager.PERMISSION_GRANTED && coarseLocation == PackageManager.PERMISSION_GRANTED
    }

    fun enableBluetoothAndScan(startBluetoothDiscovery: () -> Unit) {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth is not supported on this device.")
            Toast.makeText(context, "Bluetooth not supported on this device", Toast.LENGTH_SHORT).show()
            return
        }

        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            context.startActivity(enableBtIntent)
        } else {
            startBluetoothDiscovery()
        }
    }

    fun startBluetoothDiscovery() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Bluetooth SCAN permission not granted.")
            Toast.makeText(context, "Bluetooth SCAN permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        bluetoothAdapter?.startDiscovery()
        Toast.makeText(context, "Scanning for devices...", Toast.LENGTH_SHORT).show()

        // Clear the list of previously discovered devices
        deviceList.clear()
        deviceListAdapter.clear()
    }

    // Bluetooth discovery receiver
    val bluetoothDiscoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_FOUND) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                device?.let {
                    if (ContextCompat.checkSelfPermission(
                            context,
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

    // Bluetooth bond state receiver
    val bluetoothBondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)

            device?.let {
                if (ContextCompat.checkSelfPermission(
                        context,
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

    // Show a dialog with the list of discovered devices
    private fun showDeviceSelectionDialog() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Select a device to pair")
        builder.setAdapter(deviceListAdapter) { _, position ->
            val device = deviceList[position]

            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val deviceName = device.name ?: "Unknown Device"
                val deviceAddress = device.address
                Log.d(TAG, "Selected device: $deviceName, $deviceAddress")

                Toast.makeText(context, "Selected device: $deviceName", Toast.LENGTH_SHORT).show()

                if (device.bondState != BluetoothDevice.BOND_BONDED) {
                    Log.d(TAG, "Attempting to pair with $deviceName")
                    device.createBond()
                } else {
                    Log.d(TAG, "Device is already paired.")
                    Toast.makeText(context, "Device is already paired.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }
}
