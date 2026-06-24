package lovechain.core

class LoveChain(
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    fun ensureGenesisBlock(existingBlocks: List<LoveBlock>): List<LoveBlock> {
        if (existingBlocks.isNotEmpty()) {
            return existingBlocks
        }

        return listOf(
            createBlock(
                existingBlocks = emptyList(),
                draft = LoveBlockDraft(
                    type = LoveBlockType.GENESIS,
                    title = "Genesis Block",
                    message = "The chain is open. Two hearts agreed to remember life together.",
                    placeLabel = "LoveChain"
                )
            )
        )
    }

    fun createBlock(
        existingBlocks: List<LoveBlock>,
        draft: LoveBlockDraft,
        signatures: List<BlockSignature> = emptyList(),
        localFingerprint: String? = null
    ): LoveBlock {
        val nextIndex = existingBlocks.size
        val previousHash = existingBlocks.lastOrNull()?.hash ?: "0"
        val timestamp = clock()
        val rewardCoins = draft.type.rewardCoins
        val hash = LoveBlockHasher.hashBlock(
            index = nextIndex,
            timestamp = timestamp,
            type = draft.type,
            title = draft.title,
            message = draft.message,
            previousHash = previousHash,
            rewardCoins = rewardCoins,
            proximityMinutes = draft.proximityMinutes,
            movingMinutes = draft.movingMinutes,
            distanceMeters = draft.distanceMeters,
            activityType = draft.activityType,
            placeLabel = draft.placeLabel,
            photoHashes = draft.photoHashes
        )

        return LoveBlock(
            index = nextIndex,
            timestamp = timestamp,
            type = draft.type,
            title = draft.title,
            message = draft.message,
            previousHash = previousHash,
            hash = hash,
            rewardCoins = rewardCoins,
            confirmationStatus = confirmationStatus(signatures, localFingerprint),
            signatures = signatures,
            proximityMinutes = draft.proximityMinutes,
            movingMinutes = draft.movingMinutes,
            distanceMeters = draft.distanceMeters,
            activityType = draft.activityType,
            placeLabel = draft.placeLabel,
            photoHashes = draft.photoHashes
        )
    }

    fun totalLoveCoins(blocks: List<LoveBlock>): Int {
        return blocks.sumOf { block -> block.rewardCoins }
    }

    fun isValid(blocks: List<LoveBlock>): Boolean {
        for (index in blocks.indices) {
            val block = blocks[index]
            val expectedPreviousHash = if (index == 0) "0" else blocks[index - 1].hash

            if (block.index != index) {
                return false
            }

            if (block.previousHash != expectedPreviousHash) {
                return false
            }

            val expectedHash = LoveBlockHasher.hashBlock(
                index = block.index,
                timestamp = block.timestamp,
                type = block.type,
                title = block.title,
                message = block.message,
                previousHash = block.previousHash,
                rewardCoins = block.rewardCoins,
                proximityMinutes = block.proximityMinutes,
                movingMinutes = block.movingMinutes,
                distanceMeters = block.distanceMeters,
                activityType = block.activityType,
                placeLabel = block.placeLabel,
                photoHashes = block.photoHashes
            )

            if (block.hash != expectedHash) {
                return false
            }
        }

        return true
    }

    fun updateSignatures(
        block: LoveBlock,
        signatures: List<BlockSignature>,
        localFingerprint: String?
    ): LoveBlock {
        return block.copy(
            signatures = signatures,
            confirmationStatus = confirmationStatus(signatures, localFingerprint)
        )
    }

    private fun confirmationStatus(
        signatures: List<BlockSignature>,
        localFingerprint: String?
    ): ConfirmationStatus {
        if (signatures.isEmpty()) {
            return ConfirmationStatus.DRAFT
        }

        val signerFingerprints = signatures.map { signature -> signature.signerFingerprint }.toSet()

        if (signerFingerprints.size >= 2) {
            return ConfirmationStatus.CONFIRMED_BY_BOTH
        }

        if (localFingerprint != null && signerFingerprints.contains(localFingerprint)) {
            return ConfirmationStatus.SIGNED_BY_ME
        }

        return ConfirmationStatus.SIGNED_BY_PARTNER
    }
}
