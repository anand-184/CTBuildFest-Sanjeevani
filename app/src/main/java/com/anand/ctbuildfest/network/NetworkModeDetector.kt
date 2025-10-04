package com.anand.ctbuildfest.network

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.anand.ctbuildfest.NetworkMode


class NetworkModeDetector(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private val nearbyDevices = mutableSetOf<String>()
    private var currentScanCallback: ScanCallback? = null
    private var isScanning = false

    companion object {
        private const val TAG = "NetworkModeDetector"
        private const val SCAN_TIMEOUT = 10000L // 10 seconds
    }

    /**
     * Get current network mode
     */
    fun getCurrentMode(): NetworkMode {
        return when {
            hasInternetConnection() -> NetworkMode.ONLINE
            nearbyDevices.size >= 5 -> NetworkMode.MESH_DENSE
            nearbyDevices.size >= 1 -> NetworkMode.MESH_SPARSE
            else -> NetworkMode.OFFLINE_ISOLATED
        }
    }

    /**
     * Start scanning for nearby devices
     */
    fun startScanning(onModeChanged: (NetworkMode) -> Unit) {
        if (isScanning) {
            Log.w(TAG, "Already scanning")
            return
        }

        if (bleScanner == null) {
            Log.e(TAG, "BLE not available")
            onModeChanged(NetworkMode.OFFLINE_ISOLATED)
            return
        }

        // Create scan settings for better performance
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0) // Real-time results
            .build()

        currentScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.address?.let { address ->
                    val wasAdded = nearbyDevices.add(address)
                    if (wasAdded) {
                        Log.d(TAG, "New device found: ${address.take(8)}... (Total: ${nearbyDevices.size})")
                        onModeChanged(getCurrentMode())
                    }
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { result ->
                    result.device?.address?.let { address ->
                        nearbyDevices.add(address)
                    }
                }
                if (!results.isNullOrEmpty()) {
                    Log.d(TAG, "Batch scan: ${results.size} devices found")
                    onModeChanged(getCurrentMode())
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE scan failed: ${getScanErrorMessage(errorCode)}")
                isScanning = false
                onModeChanged(NetworkMode.OFFLINE_ISOLATED)
            }
        }

        try {
            bleScanner.startScan(null, scanSettings, currentScanCallback)
            isScanning = true
            Log.i(TAG, "✓ BLE scanning started")

            // Auto-stop scan after timeout to save battery
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (isScanning) {
                    stopScanning()
                    Log.i(TAG, "Scan timeout reached, stopping")
                }
            }, SCAN_TIMEOUT)

        } catch (e: SecurityException) {
            Log.e(TAG, "BLE permission denied", e)
            onModeChanged(NetworkMode.OFFLINE_ISOLATED)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start scan", e)
            onModeChanged(NetworkMode.OFFLINE_ISOLATED)
        }
    }

    /**
     * Stop scanning
     */
    fun stopScanning() {
        if (!isScanning) {
            return
        }

        try {
            currentScanCallback?.let {
                bleScanner?.stopScan(it)
                Log.i(TAG, "BLE scanning stopped")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission error stopping scan", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan", e)
        } finally {
            isScanning = false
            currentScanCallback = null
        }
    }

    /**
     * Clear cached devices
     */
    fun clearDevices() {
        nearbyDevices.clear()
        Log.d(TAG, "Device cache cleared")
    }

    /**
     * Get nearby device count
     */
    fun getNearbyDeviceCount(): Int {
        return nearbyDevices.size
    }

    /**
     * Check if internet is available
     */
    private fun hasInternetConnection(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking internet", e)
            false
        }
    }

    /**
     * Get human-readable scan error message
     */
    private fun getScanErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Already started"
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed"
            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
            ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Internal error"
            else -> "Unknown error ($errorCode)"
        }
    }

    /**
     * Check if Bluetooth is enabled
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Get network mode as string
     */
    fun getCurrentModeString(): String {
        return when (getCurrentMode()) {
            NetworkMode.ONLINE -> "Online (Internet)"
            NetworkMode.MESH_DENSE -> "Mesh Strong (${nearbyDevices.size} devices)"
            NetworkMode.MESH_SPARSE -> "Mesh Weak (${nearbyDevices.size} devices)"
            NetworkMode.OFFLINE_ISOLATED -> "Offline (No connection)"
        }
    }
}
