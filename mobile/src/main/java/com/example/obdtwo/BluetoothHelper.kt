// In BluetoothHelper.kt
package com.example.obdtwo

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.*
import android.content.pm.PackageManager
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*

class BluetoothHelper(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }
    private val TAG = "OBD2Bluetooth"
    private val deviceList = mutableListOf<BluetoothDevice>()
    private lateinit var deviceListAdapter: ArrayAdapter<String>
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var pendingDeviceToConnect: BluetoothDevice? = null
    private var socket: BluetoothSocket? = null

    // Keep a reference to the dialog so we don't show multiple ones
    private var deviceSelectionDialog: AlertDialog? = null

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

    val bluetoothDiscoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_FOUND) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                device?.let {
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED) {
                        try {
                            val deviceName = it.name ?: "Unknown Device"
                            val deviceAddress = it.address
                            Log.d(TAG, "Device found: $deviceName, $deviceAddress")

                            deviceList.add(it)
                            deviceListAdapter.add("$deviceName ($deviceAddress)")
                            deviceListAdapter.notifyDataSetChanged()

                            // Show or update the device selection dialog
                            showOrUpdateDeviceSelectionDialog()

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

    val bluetoothBondReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)

            device?.let {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED) {
                    try {
                        when (bondState) {
                            BluetoothDevice.BOND_BONDING -> Log.d(TAG, "Pairing with ${it.name} in progress...")
                            BluetoothDevice.BOND_BONDED -> {
                                Log.d(TAG, "Successfully paired with ${it.name}")
                                if (it == pendingDeviceToConnect) {
                                    connectToDevice(it)
                                } else {}
                            }
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

    private fun showOrUpdateDeviceSelectionDialog() {
        if (deviceSelectionDialog == null) {
            // Create and show the dialog for the first time
            val builder = AlertDialog.Builder(context)
            builder.setTitle("Select a device to pair")
            builder.setAdapter(deviceListAdapter) { _, position ->
                val device = deviceList[position]

                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED) {
                    val deviceName = device.name ?: "Unknown Device"
                    val deviceAddress = device.address
                    Log.d(TAG, "Selected device: $deviceName, $deviceAddress")

                    Toast.makeText(context, "Selected device: $deviceName", Toast.LENGTH_SHORT).show()

                    pendingDeviceToConnect = device
                    if (device.bondState == BluetoothDevice.BOND_BONDED) {
                        connectToDevice(device)
                    } else {
                        Log.d(TAG, "Attempting to pair with $deviceName")
                        device.createBond()
                    }
                } else {
                    Toast.makeText(context, "No BLUETOOTH_CONNECT permission", Toast.LENGTH_SHORT).show()
                }
            }
            builder.setNegativeButton("Cancel", null)
            deviceSelectionDialog = builder.create()
            deviceSelectionDialog?.show()
        } else {
            // Dialog already showing, just update the adapter and UI if needed
            // adapter already updated and deviceListAdapter.notifyDataSetChanged() called
            // so no need to recreate dialog
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "No BLUETOOTH_CONNECT permission", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            bluetoothAdapter?.cancelDiscovery()
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket?.connect()
            Toast.makeText(context, "Connected to ${device.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to connect: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun disconnect() {
        try {
            socket?.close()
            socket = null
            Toast.makeText(context, "Disconnected.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket: ${e.message}", e)
        }
    }

    fun isConnected(): Boolean {
        return socket?.isConnected == true
    }

    fun sendObdCommand(cmd: String): String {
        if (!isConnected()) return ""
        sendCommand(cmd)
        return readFullResponse() // Reads until '>'
    }

    private fun sendCommand(command: String) {
        try {
            val out = socket?.outputStream ?: return
            out.write((command + "\r").toByteArray(Charsets.UTF_8))
            out.flush()
            Log.d(TAG, "Sent command: $command")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending command: ${e.message}", e)
        }
    }

    private fun readFullResponse(): String {
        // Reads until '>' prompt
        return try {
            val input = socket?.inputStream ?: return ""
            val sb = StringBuilder()
            while (true) {
                val c = input.read()
                if (c == -1) break
                val char = c.toChar()
                sb.append(char)
                if (char == '>') break
            }
            val resp = sb.toString().replace("\r", "").replace("\n", "").replace(">", "").trim()
            Log.d(TAG, "Full response: $resp")
            resp
        } catch (e: Exception) {
            Log.e(TAG, "Error reading full response: ${e.message}", e)
            ""
        }
    }
}
