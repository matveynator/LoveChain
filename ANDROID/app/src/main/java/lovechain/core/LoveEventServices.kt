package lovechain.core

interface LocationService {
    fun latestSnapshot(): LoveLocationSnapshot?
}

interface BluetoothPresenceService {
    fun latestPresence(): BluetoothPresenceSnapshot?
}

interface MotionDetector {
    fun latestActivityType(): ActivityType
}

interface LoveEventMiner {
    fun mineCandidate(
        localSnapshot: LoveLocationSnapshot?,
        partnerSnapshot: LoveLocationSnapshot?,
        bluetoothPresenceSnapshot: BluetoothPresenceSnapshot?,
        activityType: ActivityType
    ): LoveBlockDraft?
}

data class BluetoothPresenceSnapshot(
    val partnerDeviceSeen: Boolean,
    val rssi: Int?,
    val nearMinutes: Int,
    val timestamp: Long = System.currentTimeMillis()
)

class PlaceholderLocationService : LocationService {
    override fun latestSnapshot(): LoveLocationSnapshot? {
        return null
    }
}

class PlaceholderBluetoothPresenceService : BluetoothPresenceService {
    override fun latestPresence(): BluetoothPresenceSnapshot? {
        return null
    }
}

class PlaceholderMotionDetector : MotionDetector {
    override fun latestActivityType(): ActivityType {
        return ActivityType.STANDING
    }
}

class PlaceholderLoveEventMiner : LoveEventMiner {
    override fun mineCandidate(
        localSnapshot: LoveLocationSnapshot?,
        partnerSnapshot: LoveLocationSnapshot?,
        bluetoothPresenceSnapshot: BluetoothPresenceSnapshot?,
        activityType: ActivityType
    ): LoveBlockDraft? {
        return null
    }
}
