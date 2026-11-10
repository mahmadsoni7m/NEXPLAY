package com.nexplay.game.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.dataStore by preferencesDataStore(name = "nexplay_prefs")

data class GameStat(val wins: Int = 0, val losses: Int = 0, val draws: Int = 0) {
    val total get() = wins + losses + draws
}

data class MatchRecord(val game: String, val mode: String, val result: String, val opponent: String, val ts: Long)

data class Daily(val date: String = "", val played: Int = 0, val wins: Int = 0, val beatMedium: Int = 0, val claimed: Int = 0)

data class Profile(
    val username: String = "Player",
    val avatar: Int = 0,
    val xp: Int = 0,
    val lang: String = "tg",
    val sound: Boolean = true,
    val serverUrl: String = "",
    val token: String = "",
    val onlineUser: String = "",
    val stats: Map<String, GameStat> = emptyMap(),
    val matches: List<MatchRecord> = emptyList(),
    val notifs: List<String> = emptyList(),
    val daily: Daily = Daily(),
    val coins: Int = 0,
    val streak: Int = 0,
    val lastLogin: String = "",
    val canClaimLogin: Boolean = true
) {
    val level get() = 1 + xp / 100
    val levelProgress get() = (xp % 100) / 100f
    val totalWins get() = stats.values.sumOf { it.wins }
    val totalLosses get() = stats.values.sumOf { it.losses }
}

class Prefs(private val ctx: Context) {
    private val kUser = stringPreferencesKey("username")
    private val kAvatar = intPreferencesKey("avatar")
    private val kXp = intPreferencesKey("xp")
    private val kLang = stringPreferencesKey("lang")
    private val kSound = booleanPreferencesKey("sound")
    private val kServer = stringPreferencesKey("server")
    private val kToken = stringPreferencesKey("token")
    private val kOnlineUser = stringPreferencesKey("online_user")
    private val kStats = stringPreferencesKey("stats")
    private val kMatches = stringPreferencesKey("matches")
    private val kNotifs = stringPreferencesKey("notifs")
    private val kDaily = stringPreferencesKey("daily")
    private val kCoins = intPreferencesKey("coins")
    private val kStreak = intPreferencesKey("streak")
    private val kLastLogin = stringPreferencesKey("last_login")

    val profile: Flow<Profile> = ctx.dataStore.data.map { toProfile(it) }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun toProfile(p: Preferences): Profile {
        val stats = HashMap<String, GameStat>()
        try {
            val o = JSONObject(p[kStats] ?: "{}")
            for (k in o.keys()) {
                val s = o.getJSONObject(k)
                stats[k] = GameStat(s.optInt("w"), s.optInt("l"), s.optInt("d"))
            }
        } catch (_: Exception) {}
        val matches = ArrayList<MatchRecord>()
        try {
            val a = JSONArray(p[kMatches] ?: "[]")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                matches.add(MatchRecord(o.optString("g"), o.optString("m"), o.optString("r"), o.optString("o"), o.optLong("t")))
            }
        } catch (_: Exception) {}
        val notifs = ArrayList<String>()
        try {
            val a = JSONArray(p[kNotifs] ?: "[]")
            for (i in 0 until a.length()) notifs.add(a.getString(i))
        } catch (_: Exception) {}
        var daily = Daily()
        try {
            val o = JSONObject(p[kDaily] ?: "{}")
            daily = Daily(o.optString("date"), o.optInt("played"), o.optInt("wins"), o.optInt("med"), o.optInt("claimed"))
        } catch (_: Exception) {}
        if (daily.date != today()) daily = Daily(date = today())
        return Profile(
            username = p[kUser] ?: "Player",
            avatar = p[kAvatar] ?: 0,
            xp = p[kXp] ?: 0,
            lang = p[kLang] ?: "tg",
            sound = p[kSound] ?: true,
            serverUrl = p[kServer] ?: "",
            token = p[kToken] ?: "",
            onlineUser = p[kOnlineUser] ?: "",
            stats = stats,
            matches = matches,
            notifs = notifs,
            daily = daily,
            coins = p[kCoins] ?: 0,
            streak = p[kStreak] ?: 0,
            lastLogin = p[kLastLogin] ?: "",
            canClaimLogin = (p[kLastLogin] ?: "") != today()
        )
    }

    suspend fun setLang(v: String) = ctx.dataStore.edit { it[kLang] = v }
    suspend fun setUsername(v: String) = ctx.dataStore.edit { it[kUser] = v.trim().take(20).ifEmpty { "Player" } }
    suspend fun setAvatar(v: Int) = ctx.dataStore.edit { it[kAvatar] = v }
    suspend fun setSound(v: Boolean) = ctx.dataStore.edit { it[kSound] = v }
    suspend fun setServer(v: String) = ctx.dataStore.edit { it[kServer] = v.trim() }
    suspend fun setAuth(token: String, user: String) = ctx.dataStore.edit { it[kToken] = token; it[kOnlineUser] = user }

    suspend fun addNotif(text: String) = ctx.dataStore.edit {
        val cur = toProfile(it).notifs
        val a = JSONArray()
        (listOf(text) + cur).take(20).forEach { s -> a.put(s) }
        it[kNotifs] = a.toString()
    }

    suspend fun clearNotifs() = ctx.dataStore.edit { it[kNotifs] = "[]" }

    suspend fun claimDaily(bit: Int, xp: Int) = ctx.dataStore.edit {
        val pr = toProfile(it)
        if (pr.daily.claimed and bit != 0) return@edit
        it[kDaily] = dailyJson(pr.daily.copy(claimed = pr.daily.claimed or bit)).toString()
        it[kXp] = pr.xp + xp
    }

    /** result: "win" | "loss" | "draw". aiLevel: -1 when not versus AI. */
    suspend fun record(game: String, mode: String, result: String, opponent: String, aiLevel: Int) = ctx.dataStore.edit {
        val pr = toProfile(it)
        val st = pr.stats.toMutableMap()
        val cur = st[game] ?: GameStat()
        st[game] = when (result) {
            "win" -> cur.copy(wins = cur.wins + 1)
            "loss" -> cur.copy(losses = cur.losses + 1)
            else -> cur.copy(draws = cur.draws + 1)
        }
        val so = JSONObject()
        for ((k, v) in st) so.put(k, JSONObject().put("w", v.wins).put("l", v.losses).put("d", v.draws))
        it[kStats] = so.toString()

        val ma = JSONArray()
        val rec = MatchRecord(game, mode, result, opponent, System.currentTimeMillis())
        (listOf(rec) + pr.matches).take(30).forEach { m ->
            ma.put(JSONObject().put("g", m.game).put("m", m.mode).put("r", m.result).put("o", m.opponent).put("t", m.ts))
        }
        it[kMatches] = ma.toString()

        it[kXp] = pr.xp + Rewards.xp(result)
        it[kCoins] = pr.coins + Rewards.coins(result)

        val d = pr.daily
        val nd = d.copy(
            played = d.played + 1,
            wins = d.wins + if (result == "win") 1 else 0,
            beatMedium = d.beatMedium + if (result == "win" && mode == "ai" && aiLevel >= 1) 1 else 0
        )
        it[kDaily] = dailyJson(nd).toString()
    }

    private fun dailyJson(d: Daily) = JSONObject().put("date", d.date).put("played", d.played)
        .put("wins", d.wins).put("med", d.beatMedium).put("claimed", d.claimed)

    /** Daily login chest: streak grows when claimed on consecutive days. Returns coins granted (0 if already claimed). */
    suspend fun claimLogin(): Int {
        var granted = 0
        ctx.dataStore.edit {
            val pr = toProfile(it)
            if (!pr.canClaimLogin) return@edit
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
            val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            val streak = if (pr.lastLogin == yesterday) pr.streak + 1 else 1
            granted = Rewards.loginCoins(streak)
            it[kStreak] = streak
            it[kLastLogin] = today()
            it[kCoins] = pr.coins + granted
            it[kXp] = pr.xp + Rewards.LOGIN_XP
        }
        return granted
    }

    suspend fun resetLocal() = ctx.dataStore.edit {
        it.remove(kCoins); it.remove(kStreak); it.remove(kLastLogin)
        it.remove(kStats); it.remove(kMatches); it.remove(kXp); it.remove(kDaily); it.remove(kNotifs)
    }
}
