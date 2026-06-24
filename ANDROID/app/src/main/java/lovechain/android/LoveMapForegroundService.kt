package lovechain.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import lovechain.core.ActivityType
import lovechain.core.BluetoothPresenceSnapshot
import lovechain.core.LoveLocationSnapshot
import lovechain.core.LoveMapSnapshot
import java.util.UUID

class LoveMapForegroundService : Service() {
    private val syncClient = LoveMapSyncClient()

    private lateinit var deviceKeyStore: DeviceKeyStore
    private lateinit var loveMapStore: LoveMapSQLiteStore
    private lateinit var locationManager: LocationManager

    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var latestLocationSnapshot: LoveLocationSnapshot? = null
    private var latestBluetoothSnapshot: BluetoothPresenceSnapshot? = null
    private var latestSyncStatus: String? = null
    private var serviceRunning = false
    private var nearStartedAt: Long? = null
    private var lastSyncAttemptAt: Long = 0L

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            latestLocationSnapshot = LoveLocationSnapshot(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = location.accuracy,
                speedMetersPerSecond = if (location.hasSpeed()) location.speed else null,
                bearing = if (location.hasBearing()) location.bearing else null,
                timestamp = location.time,
                batteryPercent = batteryPercent()
            )
            publishSnapshot()
        }

        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    private val advertiseCallback = object : AdvertiseCallback() {}

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            recordBluetoothHit(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (result in results) {
                recordBluetoothHit(result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            latestBluetoothSnapshot = BluetoothPresenceSnapshot(
                partnerDeviceSeen = false,
                rssi = null,
                nearMinutes = 0,
                timestamp = System.currentTimeMillis()
            )
            latestSyncStatus = "BLE scan failed $errorCode"
            publishSnapshot()
        }
    }

    override fun onCreate() {
        super.onCreate()
        deviceKeyStore = DeviceKeyStore()
        loveMapStore = LoveMapSQLiteStore(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ActionStop -> stopLoveMap()
            else -> startLoveMap()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        stopSensors()
        super.onDestroy()
    }

    private fun startLoveMap() {
        if (serviceRunning) {
            return
        }

        serviceRunning = true
        createNotificationChannel()
        startForeground(NotificationId, notification())
        startLocationUpdates()
        startBluetoothPresence()
        publishSnapshot()
    }

    private fun stopLoveMap() {
        serviceRunning = false
        stopSensors()
        publishSnapshot()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            latestSyncStatus = "location permission missing"
            publishSnapshot()
            return
        }

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }

        if (provider == null) {
            latestSyncStatus = "location provider disabled"
            publishSnapshot()
            return
        }

        locationManager.requestLocationUpdates(
            provider,
            LocationIntervalMillis,
            LocationMinDistanceMeters,
            locationListener,
            Looper.getMainLooper()
        )
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothPresence() {
        if (!hasBluetoothPermission()) {
            latestSyncStatus = "bluetooth permission missing"
            publishSnapshot()
            return
        }

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            latestSyncStatus = "bluetooth disabled"
            publishSnapshot()
            return
        }

        bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        val serviceUuid = ParcelUuid(LoveChainServiceUuid)
        val fingerprintBytes = bluetoothPresenceId().toByteArray(Charsets.UTF_8)

        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .build()
        val advertiseData = AdvertiseData.Builder()
            .addServiceUuid(serviceUuid)
            .addServiceData(serviceUuid, fingerprintBytes)
            .build()
        bluetoothLeAdvertiser?.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(serviceUuid)
            .build()
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()
        bluetoothLeScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun stopSensors() {
        runCatching { locationManager.removeUpdates(locationListener) }

        if (hasBluetoothPermission()) {
            runCatching { bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback) }
            runCatching { bluetoothLeScanner?.stopScan(scanCallback) }
        }

        bluetoothLeAdvertiser = null
        bluetoothLeScanner = null
    }

    private fun recordBluetoothHit(rssi: Int) {
        val now = System.currentTimeMillis()
        val nearStart = nearStartedAt ?: now
        nearStartedAt = nearStart
        latestBluetoothSnapshot = BluetoothPresenceSnapshot(
            partnerDeviceSeen = true,
            rssi = rssi,
            nearMinutes = ((now - nearStart) / 60_000L).toInt(),
            timestamp = now
        )
        publishSnapshot()
    }

    private fun recordBluetoothHit(result: ScanResult) {
        val serviceData = result.scanRecord?.getServiceData(ParcelUuid(LoveChainServiceUuid))
        if (serviceData != null && serviceData.contentEquals(bluetoothPresenceId().toByteArray(Charsets.UTF_8))) {
            return
        }

        recordBluetoothHit(result.rssi)
    }

    private fun publishSnapshot() {
        val snapshot = LoveMapSnapshot(
            deviceFingerprint = deviceKeyStore.fingerprint(),
            locationSnapshot = latestLocationSnapshot,
            bluetoothPresenceSnapshot = freshBluetoothSnapshot(),
            activityType = ActivityType.STANDING,
            serviceRunning = serviceRunning,
            syncEndpoint = syncEndpoint(),
            lastSyncStatus = latestSyncStatus,
            timestamp = System.currentTimeMillis()
        )

        val syncStatus = if (snapshot.syncEndpoint.isNullOrBlank()) {
            "sync disabled"
        } else {
            "sync queued"
        }
        val snapshotWithSync = snapshot.copy(lastSyncStatus = syncStatus)
        latestSyncStatus = syncStatus
        loveMapStore.saveSnapshot(snapshotWithSync)
        sendBroadcast(Intent(ActionSnapshotUpdated).setPackage(packageName))
        syncSnapshotAsync(snapshotWithSync)
    }

    private fun freshBluetoothSnapshot(): BluetoothPresenceSnapshot? {
        val snapshot = latestBluetoothSnapshot ?: return null
        val now = System.currentTimeMillis()
        if (now - snapshot.timestamp <= BluetoothFreshMillis) {
            return snapshot
        }

        nearStartedAt = null
        return BluetoothPresenceSnapshot(
            partnerDeviceSeen = false,
            rssi = snapshot.rssi,
            nearMinutes = 0,
            timestamp = now
        )
    }

    private fun syncSnapshotAsync(snapshot: LoveMapSnapshot) {
        val endpoint = syncEndpoint()
        if (endpoint.isNullOrBlank()) {
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastSyncAttemptAt < SyncMinIntervalMillis) {
            return
        }
        lastSyncAttemptAt = now

        Thread {
            val syncStatus = runCatching {
                val canonicalText = syncClient.canonicalSnapshotText(snapshot)
                val signature = deviceKeyStore.signBlockHash(canonicalText)
                syncClient.syncSnapshot(endpoint, snapshot, signature)
            }.getOrElse { error ->
                error.message ?: "sync failed"
            }

            latestSyncStatus = syncStatus
            loveMapStore.saveSnapshot(snapshot.copy(lastSyncStatus = syncStatus))
            sendBroadcast(Intent(ActionSnapshotUpdated).setPackage(packageName))
        }
            .apply { name = "LoveMapSync" }
            .start()
    }

    private fun batteryPercent(): Int? {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) {
            return null
        }

        return (level * 100) / scale
    }

    private fun syncEndpoint(): String? {
        return getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .getString(SyncEndpointKey, "")
            ?.trim()
            ?.takeIf { endpoint -> endpoint.isNotEmpty() }
    }

    private fun bluetoothPresenceId(): String {
        return deviceKeyStore.fingerprint().take(8)
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }

        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            NotificationChannelId,
            "LoveMap",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun notification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NotificationChannelId)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("LoveMap active")
            .setContentText("Location and Bluetooth presence are shared only while LoveMap is on.")
            .setSmallIcon(R.drawable.lovechain)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ActionStart = "lovechain.android.action.START_LOVEMAP"
        const val ActionStop = "lovechain.android.action.STOP_LOVEMAP"
        const val ActionSnapshotUpdated = "lovechain.android.action.LOVEMAP_SNAPSHOT_UPDATED"
        const val PreferencesName = "love_map_preferences"
        const val SyncEndpointKey = "sync_endpoint"

        private const val NotificationChannelId = "love_map"
        private const val NotificationId = 42
        private const val LocationIntervalMillis = 15_000L
        private const val LocationMinDistanceMeters = 5f
        private const val BluetoothFreshMillis = 60_000L
        private const val SyncMinIntervalMillis = 15_000L
        private val LoveChainServiceUuid: UUID = UUID.fromString("4df447d8-30b1-4c79-9a12-5e2a8ed6f5d1")
    }
}
