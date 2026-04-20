package dev.ravargs.applock.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart

/**
 * Repository for managing locked applications and trigger exclusions.
 * Handles all app-related locking functionality.
 */
class LockedAppsRepository(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val lockedAppsFlow: Flow<Set<String>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == KEY_LOCKED_APPS) {
                trySend(getLockedApps())
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.onStart { emit(getLockedApps()) }

    // Locked Apps Management
    fun getLockedApps(): Set<String> {
        return preferences.getStringSet(KEY_LOCKED_APPS, emptySet())?.toSet() ?: emptySet()
    }

    fun addLockedApp(packageName: String) {
        if (packageName.isBlank()) return
        val updated = getLockedApps() + packageName
        preferences.edit { putStringSet(KEY_LOCKED_APPS, updated) }
    }

    fun removeLockedApp(packageName: String) {
        val updated = getLockedApps() - packageName
        preferences.edit { putStringSet(KEY_LOCKED_APPS, updated) }
    }

    fun isAppLocked(packageName: String): Boolean {
        return getLockedApps().contains(packageName)
    }

    fun clearAllLockedApps() {
        preferences.edit { putStringSet(KEY_LOCKED_APPS, emptySet()) }
    }

    // Trigger Exclusions Management
    fun getTriggerExcludedApps(): Set<String> {
        return preferences.getStringSet(KEY_TRIGGER_EXCLUDED_APPS, emptySet())?.toSet()
            ?: emptySet()
    }

    fun addTriggerExcludedApp(packageName: String) {
        if (packageName.isBlank()) return
        val updated = getTriggerExcludedApps() + packageName
        preferences.edit { putStringSet(KEY_TRIGGER_EXCLUDED_APPS, updated) }
    }

    fun removeTriggerExcludedApp(packageName: String) {
        val updated = getTriggerExcludedApps() - packageName
        preferences.edit { putStringSet(KEY_TRIGGER_EXCLUDED_APPS, updated) }
    }

    fun isAppTriggerExcluded(packageName: String): Boolean {
        return getTriggerExcludedApps().contains(packageName)
    }

    fun clearAllTriggerExclusions() {
        preferences.edit { putStringSet(KEY_TRIGGER_EXCLUDED_APPS, emptySet()) }
    }

    // Anti-Uninstall Apps Management
    fun getAntiUninstallApps(): Set<String> {
        return preferences.getStringSet(KEY_ANTI_UNINSTALL_APPS, emptySet())?.toSet() ?: emptySet()
    }

    fun addAntiUninstallApp(packageName: String) {
        if (packageName.isBlank()) return
        val updated = getAntiUninstallApps() + packageName
        preferences.edit { putStringSet(KEY_ANTI_UNINSTALL_APPS, updated) }
    }

    fun removeAntiUninstallApp(packageName: String) {
        val updated = getAntiUninstallApps() - packageName
        preferences.edit { putStringSet(KEY_ANTI_UNINSTALL_APPS, updated) }
    }

    fun isAppAntiUninstall(packageName: String): Boolean {
        return getAntiUninstallApps().contains(packageName)
    }

    fun clearAllAntiUninstallApps() {
        preferences.edit { putStringSet(KEY_ANTI_UNINSTALL_APPS, emptySet()) }
    }

    // Bulk operations
    fun addMultipleLockedApps(packageNames: Set<String>) {
        val validPackageNames = packageNames.filter { it.isNotBlank() }.toSet()
        if (validPackageNames.isEmpty()) return
        val updated = getLockedApps() + validPackageNames
        preferences.edit { putStringSet(KEY_LOCKED_APPS, updated) }
    }

    fun removeMultipleLockedApps(packageNames: Set<String>) {
        val updated = getLockedApps() - packageNames
        preferences.edit { putStringSet(KEY_LOCKED_APPS, updated) }
    }

    // Time Limit & Usage Tracking
    fun setTimeLimit(packageName: String, minutes: Int) {
        preferences.edit { putInt("${KEY_TIME_LIMIT_PREFIX}$packageName", minutes) }
    }

    fun getTimeLimit(packageName: String): Int {
        return preferences.getInt("${KEY_TIME_LIMIT_PREFIX}$packageName", 0)
    }

    fun getDailyUsage(packageName: String): Long {
        checkAndResetDailyUsage()
        return preferences.getLong("${KEY_DAILY_USAGE_PREFIX}$packageName", 0L)
    }

    fun incrementDailyUsage(packageName: String, durationMs: Long) {
        checkAndResetDailyUsage()
        val currentUsage = getDailyUsage(packageName)
        preferences.edit { putLong("${KEY_DAILY_USAGE_PREFIX}$packageName", currentUsage + durationMs) }
    }

    private fun checkAndResetDailyUsage() {
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val lastResetDate = preferences.getString(KEY_LAST_RESET_DATE, "")

        if (today != lastResetDate) {
            // New day detected, reset all usage counts
            val editor = preferences.edit()
            preferences.all.keys.filter { it.startsWith(KEY_DAILY_USAGE_PREFIX) }.forEach {
                editor.remove(it)
            }
            editor.putString(KEY_LAST_RESET_DATE, today)
            editor.apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "app_lock_prefs"
        private const val KEY_LOCKED_APPS = "locked_apps"
        private const val KEY_TRIGGER_EXCLUDED_APPS = "trigger_excluded_apps"
        private const val KEY_ANTI_UNINSTALL_APPS = "anti_uninstall_apps"
        private const val KEY_TIME_LIMIT_PREFIX = "time_limit_"
        private const val KEY_DAILY_USAGE_PREFIX = "daily_usage_"
        private const val KEY_LAST_RESET_DATE = "last_reset_date"
    }
}
