package lovechain.android

import android.content.Context
import lovechain.core.LoveBlock
import org.json.JSONArray

class LoveBlockJsonStore(context: Context) {
    private val preferences = context.getSharedPreferences("lovechain_store", Context.MODE_PRIVATE)

    fun loadBlocks(): List<LoveBlock> {
        val blocksJson = preferences.getString("blocks_json", null) ?: return emptyList()
        return LoveChainJsonCodec.parseBlocks(JSONArray(blocksJson))
    }

    fun saveBlocks(blocks: List<LoveBlock>) {
        preferences.edit()
            .putString("blocks_json", LoveChainJsonCodec.formatBlocks(blocks).toString())
            .apply()
    }

    fun markMigratedToSQLite() {
        preferences.edit()
            .putBoolean("migrated_to_sqlite", true)
            .apply()
    }

    fun wasMigratedToSQLite(): Boolean {
        return preferences.getBoolean("migrated_to_sqlite", false)
    }
}
