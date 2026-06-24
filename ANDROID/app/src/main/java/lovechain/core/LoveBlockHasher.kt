package lovechain.core

import java.security.MessageDigest

object LoveBlockHasher {
    fun hashBlock(
        index: Int,
        timestamp: Long,
        type: LoveBlockType,
        title: String,
        message: String?,
        previousHash: String,
        rewardCoins: Int,
        proximityMinutes: Int?,
        movingMinutes: Int?,
        distanceMeters: Int?,
        activityType: ActivityType?,
        placeLabel: String?,
        photoHashes: List<String>
    ): String {
        val canonicalText = listOf(
            index.toString(),
            timestamp.toString(),
            type.name,
            title,
            message.orEmpty(),
            previousHash,
            rewardCoins.toString(),
            proximityMinutes?.toString().orEmpty(),
            movingMinutes?.toString().orEmpty(),
            distanceMeters?.toString().orEmpty(),
            activityType?.name.orEmpty(),
            placeLabel.orEmpty(),
            photoHashes.joinToString(separator = ",")
        ).joinToString(separator = "|")

        return sha256(canonicalText)
    }

    private fun sha256(text: String): String {
        val digestBytes = MessageDigest
            .getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))

        return digestBytes.joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }
}
