package lovechain.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import lovechain.core.ActivityType
import lovechain.core.BluetoothPresenceSnapshot
import lovechain.core.LoveLocationSnapshot
import lovechain.core.LoveMapSnapshot

class LoveMapSQLiteStore(context: Context) : SQLiteOpenHelper(context, DatabaseName, null, DatabaseVersion) {
    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE love_map_snapshots (
                snapshot_timestamp INTEGER PRIMARY KEY,
                device_fingerprint TEXT NOT NULL,
                latitude REAL,
                longitude REAL,
                accuracy_meters REAL,
                speed_meters_per_second REAL,
                bearing REAL,
                location_timestamp INTEGER,
                battery_percent INTEGER,
                partner_device_seen INTEGER NOT NULL,
                rssi INTEGER,
                near_minutes INTEGER NOT NULL,
                bluetooth_timestamp INTEGER,
                activity_type TEXT NOT NULL,
                service_running INTEGER NOT NULL,
                sync_endpoint TEXT,
                last_sync_status TEXT
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 1) {
            database.execSQL("DROP TABLE IF EXISTS love_map_snapshots")
            onCreate(database)
        }
    }

    fun saveSnapshot(snapshot: LoveMapSnapshot) {
        writableDatabase.insertWithOnConflict(
            "love_map_snapshots",
            null,
            valuesFor(snapshot),
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun latestSnapshot(): LoveMapSnapshot? {
        val cursor = readableDatabase.query(
            "love_map_snapshots",
            null,
            null,
            null,
            null,
            null,
            "snapshot_timestamp DESC",
            "1"
        )

        cursor.use {
            if (!it.moveToFirst()) {
                return null
            }

            val locationSnapshot = if (it.isNull(it.getColumnIndexOrThrow("latitude"))) {
                null
            } else {
                LoveLocationSnapshot(
                    latitude = it.getDouble(it.getColumnIndexOrThrow("latitude")),
                    longitude = it.getDouble(it.getColumnIndexOrThrow("longitude")),
                    accuracyMeters = it.getFloat(it.getColumnIndexOrThrow("accuracy_meters")),
                    speedMetersPerSecond = it.getNullableFloat("speed_meters_per_second"),
                    bearing = it.getNullableFloat("bearing"),
                    timestamp = it.getLong(it.getColumnIndexOrThrow("location_timestamp")),
                    batteryPercent = it.getNullableInt("battery_percent")
                )
            }

            val bluetoothTimestamp = it.getNullableLong("bluetooth_timestamp")
            val bluetoothSnapshot = if (bluetoothTimestamp == null) {
                null
            } else {
                BluetoothPresenceSnapshot(
                    partnerDeviceSeen = it.getInt(it.getColumnIndexOrThrow("partner_device_seen")) == 1,
                    rssi = it.getNullableInt("rssi"),
                    nearMinutes = it.getInt(it.getColumnIndexOrThrow("near_minutes")),
                    timestamp = bluetoothTimestamp
                )
            }

            return LoveMapSnapshot(
                deviceFingerprint = it.getString(it.getColumnIndexOrThrow("device_fingerprint")),
                locationSnapshot = locationSnapshot,
                bluetoothPresenceSnapshot = bluetoothSnapshot,
                activityType = ActivityType.valueOf(it.getString(it.getColumnIndexOrThrow("activity_type"))),
                serviceRunning = it.getInt(it.getColumnIndexOrThrow("service_running")) == 1,
                syncEndpoint = it.getNullableString("sync_endpoint"),
                lastSyncStatus = it.getNullableString("last_sync_status"),
                timestamp = it.getLong(it.getColumnIndexOrThrow("snapshot_timestamp"))
            )
        }
    }

    private fun valuesFor(snapshot: LoveMapSnapshot): ContentValues {
        val values = ContentValues()
        val locationSnapshot = snapshot.locationSnapshot
        val bluetoothSnapshot = snapshot.bluetoothPresenceSnapshot

        values.put("snapshot_timestamp", snapshot.timestamp)
        values.put("device_fingerprint", snapshot.deviceFingerprint)
        values.putNullable("latitude", locationSnapshot?.latitude)
        values.putNullable("longitude", locationSnapshot?.longitude)
        values.putNullable("accuracy_meters", locationSnapshot?.accuracyMeters)
        values.putNullable("speed_meters_per_second", locationSnapshot?.speedMetersPerSecond)
        values.putNullable("bearing", locationSnapshot?.bearing)
        values.putNullable("location_timestamp", locationSnapshot?.timestamp)
        values.putNullable("battery_percent", locationSnapshot?.batteryPercent)
        values.put("partner_device_seen", if (bluetoothSnapshot?.partnerDeviceSeen == true) 1 else 0)
        values.putNullable("rssi", bluetoothSnapshot?.rssi)
        values.put("near_minutes", bluetoothSnapshot?.nearMinutes ?: 0)
        values.putNullable("bluetooth_timestamp", bluetoothSnapshot?.timestamp)
        values.put("activity_type", snapshot.activityType.name)
        values.put("service_running", if (snapshot.serviceRunning) 1 else 0)
        values.putNullable("sync_endpoint", snapshot.syncEndpoint)
        values.putNullable("last_sync_status", snapshot.lastSyncStatus)
        return values
    }

    private companion object {
        const val DatabaseName = "love_map.db"
        const val DatabaseVersion = 1
    }
}

private fun android.database.Cursor.getNullableString(columnName: String): String? {
    val columnIndex = getColumnIndexOrThrow(columnName)
    if (isNull(columnIndex)) {
        return null
    }

    return getString(columnIndex)
}

private fun android.database.Cursor.getNullableInt(columnName: String): Int? {
    val columnIndex = getColumnIndexOrThrow(columnName)
    if (isNull(columnIndex)) {
        return null
    }

    return getInt(columnIndex)
}

private fun android.database.Cursor.getNullableLong(columnName: String): Long? {
    val columnIndex = getColumnIndexOrThrow(columnName)
    if (isNull(columnIndex)) {
        return null
    }

    return getLong(columnIndex)
}

private fun android.database.Cursor.getNullableFloat(columnName: String): Float? {
    val columnIndex = getColumnIndexOrThrow(columnName)
    if (isNull(columnIndex)) {
        return null
    }

    return getFloat(columnIndex)
}

private fun ContentValues.putNullable(key: String, value: String?) {
    if (value == null) {
        putNull(key)
        return
    }

    put(key, value)
}

private fun ContentValues.putNullable(key: String, value: Long?) {
    if (value == null) {
        putNull(key)
        return
    }

    put(key, value)
}

private fun ContentValues.putNullable(key: String, value: Int?) {
    if (value == null) {
        putNull(key)
        return
    }

    put(key, value)
}

private fun ContentValues.putNullable(key: String, value: Double?) {
    if (value == null) {
        putNull(key)
        return
    }

    put(key, value)
}

private fun ContentValues.putNullable(key: String, value: Float?) {
    if (value == null) {
        putNull(key)
        return
    }

    put(key, value)
}
