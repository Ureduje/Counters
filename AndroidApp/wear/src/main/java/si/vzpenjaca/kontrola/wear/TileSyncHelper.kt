package si.vzpenjaca.kontrola.wear

import android.content.Context
import android.content.SharedPreferences

object TileSyncHelper {
    private const val PREFS_NAME = "vzpenjaca_tile_cache"

    fun saveTelemetry(
        context: Context,
        zOnline: Boolean,
        zRemMs: Long,
        zSetMs: Long,
        zCur: Int,
        zMax: Int,
        sOnline: Boolean,
        sRemMs: Long,
        sSetMs: Long,
        sCur: Int,
        sMax: Int
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("ts", System.currentTimeMillis())
            .putBoolean("zOnline", zOnline)
            .putLong("zRem", zRemMs)
            .putLong("zSet", zSetMs)
            .putInt("zCur", zCur)
            .putInt("zMax", zMax)
            .putBoolean("sOnline", sOnline)
            .putLong("sRem", sRemMs)
            .putLong("sSet", sSetMs)
            .putInt("sCur", sCur)
            .putInt("sMax", sMax)
            .apply()
    }

    data class CachedTileData(
        val isZgoraj: Boolean,
        val isOnline: Boolean,
        val remainingMs: Long,
        val setTimeMs: Long,
        val currentPersons: Int,
        val maxPersons: Int
    )

    fun loadActiveStationData(context: Context): CachedTileData {
        val mainPrefs = context.getSharedPreferences("vzpenjaca_prefs", Context.MODE_PRIVATE)
        val activeStation = mainPrefs.getString("active_station", "zgoraj") ?: "zgoraj"
        val isZgoraj = !activeStation.equals("spodaj", ignoreCase = true)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ts = prefs.getLong("ts", 0L)
        val elapsed = if (ts > 0L) (System.currentTimeMillis() - ts).coerceAtLeast(0L) else 0L

        return if (isZgoraj) {
            val online = prefs.getBoolean("zOnline", false)
            val rem = (prefs.getLong("zRem", 0L) - elapsed).coerceAtLeast(0L)
            val set = prefs.getLong("zSet", 600000L)
            val cur = prefs.getInt("zCur", 0)
            val maxP = prefs.getInt("zMax", 28)
            CachedTileData(true, online, rem, set, cur, maxP)
        } else {
            val online = prefs.getBoolean("sOnline", false)
            val rem = (prefs.getLong("sRem", 0L) - elapsed).coerceAtLeast(0L)
            val set = prefs.getLong("sSet", 600000L)
            val cur = prefs.getInt("sCur", 0)
            val maxP = prefs.getInt("sMax", 28)
            CachedTileData(false, online, rem, set, cur, maxP)
        }
    }
}
