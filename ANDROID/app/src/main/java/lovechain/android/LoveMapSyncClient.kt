package lovechain.android

import lovechain.core.BlockSignature
import lovechain.core.LoveMapSnapshot
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LoveMapSyncClient {
    fun syncSnapshot(
        endpoint: String?,
        snapshot: LoveMapSnapshot,
        signature: BlockSignature
    ): String {
        if (endpoint.isNullOrBlank()) {
            return "sync disabled"
        }

        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")

        val requestBody = formatSnapshot(snapshot, signature).toString()
        connection.outputStream.bufferedWriter().use { writer ->
            writer.write(requestBody)
        }

        val statusCode = connection.responseCode
        connection.disconnect()
        return "HTTP $statusCode"
    }

    fun canonicalSnapshotText(snapshot: LoveMapSnapshot): String {
        val locationSnapshot = snapshot.locationSnapshot
        val bluetoothSnapshot = snapshot.bluetoothPresenceSnapshot

        return listOf(
            snapshot.deviceFingerprint,
            snapshot.timestamp.toString(),
            locationSnapshot?.latitude?.toString().orEmpty(),
            locationSnapshot?.longitude?.toString().orEmpty(),
            locationSnapshot?.accuracyMeters?.toString().orEmpty(),
            locationSnapshot?.speedMetersPerSecond?.toString().orEmpty(),
            locationSnapshot?.bearing?.toString().orEmpty(),
            locationSnapshot?.batteryPercent?.toString().orEmpty(),
            bluetoothSnapshot?.partnerDeviceSeen?.toString().orEmpty(),
            bluetoothSnapshot?.rssi?.toString().orEmpty(),
            bluetoothSnapshot?.nearMinutes?.toString().orEmpty(),
            snapshot.activityType.name
        ).joinToString(separator = "|")
    }

    private fun formatSnapshot(snapshot: LoveMapSnapshot, signature: BlockSignature): JSONObject {
        val locationSnapshot = snapshot.locationSnapshot
        val bluetoothSnapshot = snapshot.bluetoothPresenceSnapshot

        return JSONObject()
            .put("deviceFingerprint", snapshot.deviceFingerprint)
            .put("timestamp", snapshot.timestamp)
            .put("activityType", snapshot.activityType.name)
            .put("serviceRunning", snapshot.serviceRunning)
            .put(
                "location",
                if (locationSnapshot == null) {
                    JSONObject.NULL
                } else {
                    JSONObject()
                        .put("latitude", locationSnapshot.latitude)
                        .put("longitude", locationSnapshot.longitude)
                        .put("accuracyMeters", locationSnapshot.accuracyMeters)
                        .putNullable("speedMetersPerSecond", locationSnapshot.speedMetersPerSecond)
                        .putNullable("bearing", locationSnapshot.bearing)
                        .put("timestamp", locationSnapshot.timestamp)
                        .putNullable("batteryPercent", locationSnapshot.batteryPercent)
                }
            )
            .put(
                "bluetoothPresence",
                if (bluetoothSnapshot == null) {
                    JSONObject.NULL
                } else {
                    JSONObject()
                        .put("partnerDeviceSeen", bluetoothSnapshot.partnerDeviceSeen)
                        .putNullable("rssi", bluetoothSnapshot.rssi)
                        .put("nearMinutes", bluetoothSnapshot.nearMinutes)
                        .put("timestamp", bluetoothSnapshot.timestamp)
                }
            )
            .put(
                "signature",
                JSONObject()
                    .put("signerFingerprint", signature.signerFingerprint)
                    .put("publicKeyBase64", signature.publicKeyBase64)
                    .put("signatureBase64", signature.signatureBase64)
                    .put("timestamp", signature.timestamp)
            )
    }
}
