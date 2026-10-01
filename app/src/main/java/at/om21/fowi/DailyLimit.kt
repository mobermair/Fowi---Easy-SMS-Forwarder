package at.om21.fowi

import android.content.Context
import java.time.LocalDate

/**
 * Caps the number of SMS sent per calendar day to protect against unexpected costs, e.g. from a
 * flood of incoming messages. Long messages count once per part, as the carrier bills them.
 */
object DailyLimit {
    const val PREFERENCES_NAME = "daily_limit"
    const val DEFAULT_LIMIT = 100
    const val MAX_LIMIT = 9999
    private const val LIMIT_KEY = "limit"
    private const val DAY_KEY = "day"
    private const val COUNT_KEY = "count"

    /** Maximum SMS per day; 0 means no limit. */
    fun limit(context: Context): Int = preferences(context).getInt(LIMIT_KEY, DEFAULT_LIMIT)

    fun setLimit(context: Context, limit: Int) {
        preferences(context).edit().putInt(LIMIT_KEY, limit.coerceIn(0, MAX_LIMIT)).apply()
    }

    /** SMS sent today, including resends and sends that failed afterwards. */
    fun sentToday(context: Context): Int {
        val preferences = preferences(context)
        return if (preferences.getLong(DAY_KEY, -1) == today()) preferences.getInt(COUNT_KEY, 0) else 0
    }

    /** Counts [parts] SMS for today and returns true, or returns false if they would exceed the limit. */
    @Synchronized
    fun tryReserve(context: Context, parts: Int): Boolean {
        val sent = sentToday(context)
        if (!allows(limit(context), sent, parts)) return false
        preferences(context).edit().putLong(DAY_KEY, today()).putInt(COUNT_KEY, sent + parts).apply()
        return true
    }

    internal fun allows(limit: Int, sentToday: Int, parts: Int): Boolean = limit <= 0 || sentToday + parts <= limit

    private fun today(): Long = LocalDate.now().toEpochDay()

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
