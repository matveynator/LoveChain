package lovechain.android

import lovechain.core.ActivityType
import lovechain.core.BlockSignature
import lovechain.core.ConfirmationStatus
import lovechain.core.CoupleProfile
import lovechain.core.LoveBlock
import lovechain.core.LoveBlockType
import org.json.JSONArray
import org.json.JSONObject

object LoveChainJsonCodec {
    fun formatExport(blocks: List<LoveBlock>, coupleProfile: CoupleProfile): String {
        return JSONObject()
            .put("formatVersion", 2)
            .put("app", "LoveChain")
            .put(
                "coupleProfile",
                JSONObject()
                    .put("partnerAName", coupleProfile.partnerAName)
                    .put("partnerBName", coupleProfile.partnerBName)
            )
            .put("blocks", formatBlocks(blocks))
            .toString(2)
    }

    fun parseExport(exportJson: String): List<LoveBlock> {
        val exportObject = JSONObject(exportJson)
        return parseBlocks(exportObject.getJSONArray("blocks"))
    }

    fun formatBlocks(blocks: List<LoveBlock>): JSONArray {
        val blocksArray = JSONArray()

        for (block in blocks) {
            blocksArray.put(formatBlock(block))
        }

        return blocksArray
    }

    fun parseBlocks(blocksArray: JSONArray): List<LoveBlock> {
        val blocks = mutableListOf<LoveBlock>()

        for (blockIndex in 0 until blocksArray.length()) {
            blocks.add(parseBlock(blocksArray.getJSONObject(blockIndex)))
        }

        return blocks
    }

    fun formatSignatures(signatures: List<BlockSignature>): JSONArray {
        val signaturesArray = JSONArray()

        for (signature in signatures) {
            signaturesArray.put(
                JSONObject()
                    .put("signerFingerprint", signature.signerFingerprint)
                    .put("publicKeyBase64", signature.publicKeyBase64)
                    .put("signatureBase64", signature.signatureBase64)
                    .put("timestamp", signature.timestamp)
            )
        }

        return signaturesArray
    }

    fun parseSignatures(signaturesArray: JSONArray): List<BlockSignature> {
        val signatures = mutableListOf<BlockSignature>()

        for (signatureIndex in 0 until signaturesArray.length()) {
            val signatureObject = signaturesArray.getJSONObject(signatureIndex)
            signatures.add(
                BlockSignature(
                    signerFingerprint = signatureObject.getString("signerFingerprint"),
                    publicKeyBase64 = signatureObject.getString("publicKeyBase64"),
                    signatureBase64 = signatureObject.getString("signatureBase64"),
                    timestamp = signatureObject.getLong("timestamp")
                )
            )
        }

        return signatures
    }

    fun formatPhotoHashes(photoHashes: List<String>): JSONArray {
        val photoHashesArray = JSONArray()

        for (photoHash in photoHashes) {
            photoHashesArray.put(photoHash)
        }

        return photoHashesArray
    }

    fun parsePhotoHashes(photoHashesArray: JSONArray): List<String> {
        val photoHashes = mutableListOf<String>()

        for (photoHashIndex in 0 until photoHashesArray.length()) {
            photoHashes.add(photoHashesArray.getString(photoHashIndex))
        }

        return photoHashes
    }

    private fun parseBlock(blockObject: JSONObject): LoveBlock {
        val signatures = parseSignatures(blockObject.optJSONArray("signatures") ?: JSONArray())
        val confirmationStatus = blockObject.optStringOrNull("confirmationStatus")
            ?.let { status -> ConfirmationStatus.valueOf(status) }
            ?: if (signatures.isEmpty()) ConfirmationStatus.DRAFT else ConfirmationStatus.SIGNED_BY_ME

        return LoveBlock(
            index = blockObject.getInt("index"),
            timestamp = blockObject.getLong("timestamp"),
            type = LoveBlockType.valueOf(blockObject.getString("type")),
            title = blockObject.getString("title"),
            message = blockObject.optStringOrNull("message"),
            previousHash = blockObject.getString("previousHash"),
            hash = blockObject.getString("hash"),
            rewardCoins = blockObject.getInt("rewardCoins"),
            confirmationStatus = confirmationStatus,
            signatures = signatures,
            proximityMinutes = blockObject.optIntOrNull("proximityMinutes"),
            movingMinutes = blockObject.optIntOrNull("movingMinutes"),
            distanceMeters = blockObject.optIntOrNull("distanceMeters"),
            activityType = blockObject.optStringOrNull("activityType")?.let { name -> ActivityType.valueOf(name) },
            placeLabel = blockObject.optStringOrNull("placeLabel"),
            photoHashes = parsePhotoHashes(blockObject.optJSONArray("photoHashes") ?: JSONArray())
        )
    }

    private fun formatBlock(block: LoveBlock): JSONObject {
        return JSONObject()
            .put("index", block.index)
            .put("timestamp", block.timestamp)
            .put("type", block.type.name)
            .put("title", block.title)
            .putNullable("message", block.message)
            .put("previousHash", block.previousHash)
            .put("hash", block.hash)
            .put("rewardCoins", block.rewardCoins)
            .put("confirmationStatus", block.confirmationStatus.name)
            .put("signatures", formatSignatures(block.signatures))
            .putNullable("proximityMinutes", block.proximityMinutes)
            .putNullable("movingMinutes", block.movingMinutes)
            .putNullable("distanceMeters", block.distanceMeters)
            .putNullable("activityType", block.activityType?.name)
            .putNullable("placeLabel", block.placeLabel)
            .put("photoHashes", formatPhotoHashes(block.photoHashes))
    }
}

fun JSONObject.optStringOrNull(name: String): String? {
    if (!has(name) || isNull(name)) {
        return null
    }

    return getString(name)
}

fun JSONObject.optIntOrNull(name: String): Int? {
    if (!has(name) || isNull(name)) {
        return null
    }

    return getInt(name)
}

fun JSONObject.putNullable(name: String, value: Any?): JSONObject {
    if (value == null) {
        put(name, JSONObject.NULL)
        return this
    }

    put(name, value)
    return this
}
