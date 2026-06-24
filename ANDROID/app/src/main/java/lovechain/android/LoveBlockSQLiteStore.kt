package lovechain.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import lovechain.core.ActivityType
import lovechain.core.ConfirmationStatus
import lovechain.core.LoveBlock
import lovechain.core.LoveBlockType
import org.json.JSONArray

class LoveBlockSQLiteStore(context: Context) : SQLiteOpenHelper(context, DatabaseName, null, DatabaseVersion) {
    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE love_blocks (
                block_index INTEGER PRIMARY KEY,
                timestamp INTEGER NOT NULL,
                type TEXT NOT NULL,
                title TEXT NOT NULL,
                message TEXT,
                previous_hash TEXT NOT NULL,
                hash TEXT NOT NULL,
                reward_coins INTEGER NOT NULL,
                confirmation_status TEXT NOT NULL,
                signatures_json TEXT NOT NULL,
                proximity_minutes INTEGER,
                moving_minutes INTEGER,
                distance_meters INTEGER,
                activity_type TEXT,
                place_label TEXT,
                photo_hashes_json TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            database.execSQL("DROP TABLE IF EXISTS love_blocks")
            onCreate(database)
        }
    }

    fun loadBlocks(): List<LoveBlock> {
        val blocks = mutableListOf<LoveBlock>()
        val cursor = readableDatabase.query(
            "love_blocks",
            null,
            null,
            null,
            null,
            null,
            "block_index ASC"
        )

        cursor.use {
            while (it.moveToNext()) {
                blocks.add(
                    LoveBlock(
                        index = it.getInt(it.getColumnIndexOrThrow("block_index")),
                        timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp")),
                        type = LoveBlockType.valueOf(it.getString(it.getColumnIndexOrThrow("type"))),
                        title = it.getString(it.getColumnIndexOrThrow("title")),
                        message = it.getNullableString("message"),
                        previousHash = it.getString(it.getColumnIndexOrThrow("previous_hash")),
                        hash = it.getString(it.getColumnIndexOrThrow("hash")),
                        rewardCoins = it.getInt(it.getColumnIndexOrThrow("reward_coins")),
                        confirmationStatus = ConfirmationStatus.valueOf(
                            it.getString(it.getColumnIndexOrThrow("confirmation_status"))
                        ),
                        signatures = LoveChainJsonCodec.parseSignatures(
                            JSONArray(it.getString(it.getColumnIndexOrThrow("signatures_json")))
                        ),
                        proximityMinutes = it.getNullableInt("proximity_minutes"),
                        movingMinutes = it.getNullableInt("moving_minutes"),
                        distanceMeters = it.getNullableInt("distance_meters"),
                        activityType = it.getNullableString("activity_type")?.let { name ->
                            ActivityType.valueOf(name)
                        },
                        placeLabel = it.getNullableString("place_label"),
                        photoHashes = LoveChainJsonCodec.parsePhotoHashes(
                            JSONArray(it.getString(it.getColumnIndexOrThrow("photo_hashes_json")))
                        )
                    )
                )
            }
        }

        return blocks
    }

    fun replaceAll(blocks: List<LoveBlock>) {
        writableDatabase.transaction {
            delete("love_blocks", null, null)
            for (block in blocks) {
                insertOrThrow("love_blocks", null, valuesFor(block))
            }
        }
    }

    fun appendBlock(block: LoveBlock) {
        writableDatabase.insertOrThrow("love_blocks", null, valuesFor(block))
    }

    private fun valuesFor(block: LoveBlock): ContentValues {
        val values = ContentValues()
        values.put("block_index", block.index)
        values.put("timestamp", block.timestamp)
        values.put("type", block.type.name)
        values.put("title", block.title)
        values.putNullable("message", block.message)
        values.put("previous_hash", block.previousHash)
        values.put("hash", block.hash)
        values.put("reward_coins", block.rewardCoins)
        values.put("confirmation_status", block.confirmationStatus.name)
        values.put("signatures_json", LoveChainJsonCodec.formatSignatures(block.signatures).toString())
        values.putNullable("proximity_minutes", block.proximityMinutes)
        values.putNullable("moving_minutes", block.movingMinutes)
        values.putNullable("distance_meters", block.distanceMeters)
        values.putNullable("activity_type", block.activityType?.name)
        values.putNullable("place_label", block.placeLabel)
        values.put("photo_hashes_json", LoveChainJsonCodec.formatPhotoHashes(block.photoHashes).toString())
        return values
    }

    private companion object {
        const val DatabaseName = "lovechain.db"
        const val DatabaseVersion = 2
    }
}

private fun SQLiteDatabase.transaction(block: SQLiteDatabase.() -> Unit) {
    beginTransaction()
    try {
        block()
        setTransactionSuccessful()
    } finally {
        endTransaction()
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

private fun ContentValues.putNullable(key: String, value: String?) {
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
