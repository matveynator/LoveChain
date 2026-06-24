package lovechain.core

enum class LoveBlockType(val title: String, val rewardCoins: Int) {
    GENESIS("Genesis Block", 100),
    TOGETHERNESS("Together Block", 32),
    WALK("Walk Block", 48),
    TRAVEL("Travel Block", 80),
    CARE("Care Block", 24),
    GRATITUDE("Gratitude Block", 16),
    INTIMACY("Intimacy Block", 64),
    RECONCILIATION("Reconciliation Block", 40),
    MEMORY("Memory Block", 36),
    ETERNITY("Eternity Block", 144),
    NEAR("Near Block", 20),
    ADVENTURE("Adventure Block", 64),
    RETURN_HOME("Return Home Block", 28)
}

enum class ActivityType {
    STANDING,
    WALKING,
    RUNNING,
    CYCLING,
    DRIVING,
    TRAVELING
}

data class CoupleProfile(
    val partnerAName: String,
    val partnerBName: String
) {
    fun displayName(): String {
        return "$partnerAName ❤ $partnerBName"
    }
}

data class LoveLocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMetersPerSecond: Float?,
    val bearing: Float?,
    val timestamp: Long,
    val batteryPercent: Int?
)

data class PartnerPresence(
    val partnerName: String,
    val visibilityMode: VisibilityMode,
    val movementStatus: String,
    val batteryPercent: Int?,
    val distanceMeters: Int?,
    val lastUpdateText: String,
    val bluetoothConfirmed: Boolean,
    val gpsConfirmed: Boolean
)

enum class VisibilityMode(val label: String) {
    ALWAYS_OPEN("Open always"),
    ONLY_WHEN_MOVING("Only when moving"),
    ONLY_PARTNER("Only partner"),
    PAUSED_ONE_HOUR("Paused for 1 hour"),
    PAUSED_UNTIL_TOMORROW("Paused until tomorrow"),
    EMERGENCY("Emergency mode")
}

enum class ConfirmationStatus {
    DRAFT,
    SIGNED_BY_ME,
    SIGNED_BY_PARTNER,
    CONFIRMED_BY_BOTH
}

data class BlockSignature(
    val signerFingerprint: String,
    val publicKeyBase64: String,
    val signatureBase64: String,
    val timestamp: Long
)

data class LoveBlock(
    val index: Int,
    val timestamp: Long,
    val type: LoveBlockType,
    val title: String,
    val message: String?,
    val previousHash: String,
    val hash: String,
    val rewardCoins: Int,
    val confirmationStatus: ConfirmationStatus,
    val signatures: List<BlockSignature>,
    val proximityMinutes: Int?,
    val movingMinutes: Int?,
    val distanceMeters: Int?,
    val activityType: ActivityType?,
    val placeLabel: String?,
    val photoHashes: List<String>
)

data class LoveBlockDraft(
    val type: LoveBlockType,
    val title: String,
    val message: String?,
    val proximityMinutes: Int? = null,
    val movingMinutes: Int? = null,
    val distanceMeters: Int? = null,
    val activityType: ActivityType? = null,
    val placeLabel: String? = null,
    val photoHashes: List<String> = emptyList()
)
