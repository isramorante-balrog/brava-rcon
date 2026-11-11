package io.github.isramorante.brava.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** Everything is kept in an encrypted preferences file backed by the Android keystore. */
class Store(context: Context) {
    private val prefs: SharedPreferences = open(context)

    private fun open(context: Context): SharedPreferences {
        fun create(): SharedPreferences = EncryptedSharedPreferences.create(
            FILE,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        return try {
            create()
        } catch (e: Exception) {
            // The keystore entry can be lost (for example after a device restore). Start clean.
            context.deleteSharedPreferences(FILE)
            create()
        }
    }

    fun loadServers(): List<ServerProfile> = read("servers") { J.decodeFromString<List<ServerProfile>>(it) } ?: emptyList()
    fun saveServers(list: List<ServerProfile>) = write("servers", J.encodeToString(list))

    fun loadQuick(): List<QuickCmd> = read("quick") { J.decodeFromString<List<QuickCmd>>(it) } ?: DEFAULT_QUICK
    fun saveQuick(list: List<QuickCmd>) = write("quick", J.encodeToString(list))

    fun loadNotes(): Map<String, String> = read("notes") { J.decodeFromString<Map<String, String>>(it) } ?: emptyMap()
    fun saveNotes(map: Map<String, String>) = write("notes", J.encodeToString(map))

    fun loadHistory(): List<String> = read("history") { J.decodeFromString<List<String>>(it) } ?: emptyList()
    fun saveHistory(list: List<String>) = write("history", J.encodeToString(list))

    fun loadLock(): Boolean = prefs.getBoolean("lock", false)
    fun saveLock(on: Boolean) = prefs.edit().putBoolean("lock", on).apply()

    fun loadSkippedBuild(): Int = prefs.getInt("skipped_build", 0)
    fun saveSkippedBuild(build: Int) = prefs.edit().putInt("skipped_build", build).apply()

    private fun <T> read(key: String, parse: (String) -> T): T? {
        val raw = prefs.getString(key, null) ?: return null
        return try {
            parse(raw)
        } catch (e: Exception) {
            null
        }
    }

    private fun write(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    companion object {
        private const val FILE = "brava_secure"
        val DEFAULT_QUICK = listOf(
            QuickCmd("Airdrop", "supply.call"),
            QuickCmd("Patrol heli", "heli.call"),
            QuickCmd("Status", "status"),
            QuickCmd("Plugins", "c.plugins"),
        )
    }
}
