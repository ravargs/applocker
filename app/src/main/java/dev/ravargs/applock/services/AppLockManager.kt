package dev.ravargs.applock.services

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import dev.ravargs.applock.core.utils.LogUtils
import dev.ravargs.applock.data.repository.BackendImplementation
import dev.ravargs.applock.data.repository.LockedAppsRepository
import dev.ravargs.applock.data.repository.PreferencesRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object AppLockConstants {
    val KNOWN_RECENTS_CLASSES = setOf(
        "com.android.systemui.recents.RecentsActivity",
        "com.android.quickstep.RecentsActivity",
        "com.android.systemui.recents.RecentsView",
        "com.android.systemui.recents.RecentsPanelView",
    )

    val EXCLUDED_APPS = setOf(
        "com.android.systemui",
        "com.android.intentresolver",
        "com.google.android.permissioncontroller",
        "android.uid.system:1000",
        "com.google.android.googlequicksearchbox",
        "android",
        "com.google.android.gms",
        "com.google.android.webview"
    )

    val ACCESSIBILITY_SETTINGS_CLASSES = setOf(
        "com.android.settings.accessibility.AccessibilitySettings",
        "com.android.settings.accessibility.AccessibilityMenuActivity",
        "com.android.settings.accessibility.AccessibilityShortcutActivity",
        "com.android.settings.Settings\$AccessibilitySettingsActivity"
    )

    const val MAX_RESTART_ATTEMPTS = 3
    const val RESTART_COOLDOWN_MS = 30000L
    const val RESTART_INTERVAL_MS = 5000L
}

enum class BiometricState {
    IDLE, AUTH_STARTED
}

fun Context.isDeviceLocked(): Boolean {
    val keyguardManager = getSystemService(KeyguardManager::class.java)
    return keyguardManager?.isKeyguardLocked ?: false
}

@Suppress("DEPRECATION")
fun Context.isServiceRunning(serviceClass: Class<*>): Boolean {
    val manager = getSystemService(ActivityManager::class.java) ?: return false
    return manager.getRunningServices(Int.MAX_VALUE)
        .any { serviceClass.name == it.service.className }
}

object AppLockManager {
    private const val TAG = "AppLockManager"

    var temporarilyUnlockedApp: String = ""
    val appUnlockTimes = ConcurrentHashMap<String, Long>()
    val isLockScreenShown = AtomicBoolean(false)
    var currentBiometricState: BiometricState = BiometricState.IDLE
    private val appStartTimeMap = ConcurrentHashMap<String, Long>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var periodicCheckRunnable: Runnable? = null
    private var periodicCheckPackageName: String? = null

    // Grace period tracking
    private var recentlyLeftApp: String = ""
    private var recentlyLeftTime: Long = 0L
    private const val GRACE_PERIOD_MS = 300L

    fun setRecentlyLeftApp(packageName: String) {
        recentlyLeftApp = packageName
        recentlyLeftTime = System.currentTimeMillis()
        LogUtils.d(TAG, "Left app $packageName at $recentlyLeftTime")
    }

    fun checkAndRestoreRecentlyLeftApp(packageName: String): Boolean {
        // If we are returning to the same app we just left within the grace period
        if (packageName == recentlyLeftApp && packageName.isNotEmpty()) {
            val elapsed = System.currentTimeMillis() - recentlyLeftTime
            if (elapsed <= GRACE_PERIOD_MS) {
                LogUtils.d(TAG, "Restoring unlock state for $packageName (elapsed: ${elapsed}ms)")
                temporarilyUnlockedApp = packageName
                // Clear the tracking so it doesn't trigger again inappropriately
                recentlyLeftApp = ""
                recentlyLeftTime = 0L
                return true
            } else {
                LogUtils.d(TAG, "Grace period expired for $packageName (elapsed: ${elapsed}ms)")
                recentlyLeftApp = "" // Expired
            }
        }
        return false
    }

    private val serviceRestartAttempts = ConcurrentHashMap<String, Int>()
    private val lastRestartTime = ConcurrentHashMap<String, Long>()

    private val ALL_APP_LOCK_SERVICES = setOf(
        ShizukuAppLockService::class.java,
        ExperimentalAppLockService::class.java
    )

    fun unlockApp(packageName: String) {
        temporarilyUnlockedApp = packageName
        appUnlockTimes[packageName] = System.currentTimeMillis()
        
        // If the limit was reached, grant a grace period
        // We check if the limit is currently reached to decide if this unlock is a "grace period" unlock
        // But AppLockManager needs access to repository to check limit. 
        // Better to handle this extension in a separate method or pass repo here.
        LogUtils.d(
            TAG,
            "App $packageName unlocked at timestamp: ${appUnlockTimes[packageName]}, current time: ${System.currentTimeMillis()}"
        )
    }

    fun forgetAppUnlock(packageName: String) {
        appUnlockTimes.remove(packageName)
        if (temporarilyUnlockedApp == packageName) {
            temporarilyUnlockedApp = ""
        }
        LogUtils.d(TAG, "Security reset: Forgotten unlock state for $packageName")
    }

    fun startUsageTracking(packageName: String) {
        if (packageName.isBlank()) return
        LogUtils.d(TAG, "Starting usage tracking for $packageName")
        appStartTimeMap[packageName] = System.currentTimeMillis()
    }

    fun stopUsageTracking(packageName: String, repository: dev.ravargs.applock.data.repository.AppLockRepository) {
        val startTime = appStartTimeMap.remove(packageName) ?: return
        val duration = System.currentTimeMillis() - startTime
        if (duration > 0) {
            LogUtils.d(TAG, "Stopping usage tracking for $packageName. Duration: ${duration}ms")
            repository.incrementDailyUsage(packageName, duration)
        }
    }

    fun getCurrentlyTrackingPackage(): String? {
        return appStartTimeMap.keys().toList().firstOrNull()
    }

    fun stopUsageTrackingForActiveApp(repository: dev.ravargs.applock.data.repository.AppLockRepository) {
        val packageName = getCurrentlyTrackingPackage() ?: return
        LogUtils.d(TAG, "Stopping active tracking for $packageName due to external trigger (e.g. screen off)")
        stopUsageTracking(packageName, repository)
    }

    fun isTimeLimitReached(packageName: String, repository: dev.ravargs.applock.data.repository.AppLockRepository): Boolean {
        val limitMinutes = repository.getTimeLimit(packageName)
        if (limitMinutes <= 0) return false

        val dailyUsageMs = repository.getDailyUsage(packageName)
        val startTime = appStartTimeMap[packageName]
        val currentSessionMs = if (startTime != null) System.currentTimeMillis() - startTime else 0L
        
        val totalUsageMs = dailyUsageMs + currentSessionMs
        val limitMs = limitMinutes.toLong() * 60 * 1000
        
        LogUtils.d(TAG, "isTimeLimitReached check for $packageName: usage=${totalUsageMs/1000}s, limit=${limitMs/1000}s")
        
        return totalUsageMs >= limitMs
    }

    fun startPeriodicLimitCheck(packageName: String, repository: dev.ravargs.applock.data.repository.AppLockRepository, onLimitReached: () -> Unit) {
        if (periodicCheckPackageName == packageName && periodicCheckRunnable != null) {
            // Already monitoring this package, don't reset the timer
            return
        }
        
        stopPeriodicLimitCheck()
        periodicCheckPackageName = packageName
        
        periodicCheckRunnable = object : Runnable {
            override fun run() {
                if (isTimeLimitReached(packageName, repository)) {
                    LogUtils.d(TAG, "Time limit reached for $packageName during periodic check.")
                    onLimitReached()
                    stopPeriodicLimitCheck()
                } else {
                    mainHandler.postDelayed(this, 10000L) // Check every 10 seconds for responsiveness
                }
            }
        }
        mainHandler.postDelayed(periodicCheckRunnable!!, 10000L)
    }

    fun stopPeriodicLimitCheck() {
        periodicCheckRunnable?.let {
            mainHandler.removeCallbacks(it)
            periodicCheckRunnable = null
        }
        periodicCheckPackageName = null
    }

    fun temporarilyUnlockAppWithBiometrics(packageName: String) {
        unlockApp(packageName)
        reportBiometricAuthFinished()
    }

    fun reportBiometricAuthStarted() {
        currentBiometricState = BiometricState.AUTH_STARTED
    }

    fun reportBiometricAuthFinished() {
        currentBiometricState = BiometricState.IDLE
    }

    fun isAppTemporarilyUnlocked(packageName: String): Boolean =
        temporarilyUnlockedApp == packageName

    fun clearTemporarilyUnlockedApp() {
        temporarilyUnlockedApp = ""
    }

    fun resetAllStates() {
        LogUtils.d(TAG, "Emergency reset of all AppLock states.")
        temporarilyUnlockedApp = ""
        isLockScreenShown.set(false)
        currentBiometricState = BiometricState.IDLE
        recentlyLeftApp = ""
        recentlyLeftTime = 0L
        stopPeriodicLimitCheck()
    }

    fun startFallbackServices(context: Context, failedService: Class<*>) {
        val serviceName = failedService.simpleName

        if (!shouldAttemptRestart(serviceName)) return

        val targetBackend = when (failedService) {
            ShizukuAppLockService::class.java -> BackendImplementation.USAGE_STATS
            ExperimentalAppLockService::class.java -> BackendImplementation.ACCESSIBILITY
            AppLockAccessibilityService::class.java -> null
            else -> null
        }

        when (targetBackend) {
            BackendImplementation.ACCESSIBILITY -> {
                if (AppLockAccessibilityService.isServiceRunning) return
                Log.d(
                    TAG,
                    "Attempting ACCESSIBILITY backend restart. (Manually enabled in settings)"
                )
            }

            BackendImplementation.USAGE_STATS, BackendImplementation.SHIZUKU -> {
                startServiceByBackend(context, targetBackend)
            }

            null -> {
                if (failedService == AppLockAccessibilityService::class.java) {
                    LogUtils.d(
                        TAG,
                        "Accessibility Service stopped. Waiting for system restart or manual re-enable."
                    )
                    return
                }
                return
            }
        }
        recordRestartAttempt(serviceName)
    }

    fun stopAllOtherServices(context: Context, excludeService: Class<*>) {
        ALL_APP_LOCK_SERVICES
            .filter { it != excludeService }
            .forEach {
                context.stopService(Intent(context, it))
            }
        LogUtils.d(TAG, "Stopped all main app lock services except ${excludeService.simpleName}.")
    }

    fun resetRestartAttempts(serviceName: String) {
        serviceRestartAttempts.remove(serviceName)
        lastRestartTime.remove(serviceName)
        LogUtils.d(TAG, "Reset restart attempts for $serviceName")
    }

    private fun shouldAttemptRestart(serviceName: String): Boolean {
        val currentTime = System.currentTimeMillis()
        val attempts = serviceRestartAttempts[serviceName] ?: 0
        val lastRestart = lastRestartTime[serviceName] ?: 0L

        if (currentTime - lastRestart < AppLockConstants.RESTART_INTERVAL_MS) {
            Log.d(TAG, "Service $serviceName restart too recent, skipping")
            return false
        }

        if (attempts >= AppLockConstants.MAX_RESTART_ATTEMPTS) {
            if (currentTime - lastRestart > AppLockConstants.RESTART_COOLDOWN_MS) {
                Log.d(TAG, "Cooldown expired for $serviceName, resetting attempts")
                serviceRestartAttempts[serviceName] = 0
                return true
            }
            Log.d(TAG, "Max restart attempts reached for $serviceName, in cooldown")
            return false
        }

        return true
    }

    private fun recordRestartAttempt(serviceName: String) {
        val currentTime = System.currentTimeMillis()
        serviceRestartAttempts.compute(serviceName) { _, attempts -> (attempts ?: 0) + 1 }
        lastRestartTime[serviceName] = currentTime
        Log.d(
            TAG,
            "Recorded restart attempt ${serviceRestartAttempts[serviceName]} for $serviceName"
        )
    }

    private fun startServiceByBackend(context: Context, backend: BackendImplementation) {
        try {
            stopAllOtherServices(context, Nothing::class.java)

            val serviceClass = when (backend) {
                BackendImplementation.SHIZUKU -> ShizukuAppLockService::class.java
                BackendImplementation.USAGE_STATS -> ExperimentalAppLockService::class.java
                else -> return
            }

            LogUtils.d(TAG, "Starting $backend service as fallback.")
            context.startService(Intent(context, serviceClass))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start fallback service for backend: $backend", e)
        }
    }

    fun handleNewAppInstalled(context: Context, packageName: String) {
        if (packageName == context.packageName) return

        val preferencesRepository = PreferencesRepository(context)
        if (preferencesRepository.isAutoLockNewAppsEnabled()) {
            val lockedAppsRepository = LockedAppsRepository(context)

            if (!lockedAppsRepository.isAppLocked(packageName)) {
                LogUtils.d(TAG, "Requesting auto-lock for new app: $packageName")
                lockedAppsRepository.addLockedApp(packageName)
                LogUtils.d(TAG, "Successfully added $packageName to locked_apps. Current set size: ${lockedAppsRepository.getLockedApps().size}")
            }
        }
    }
}
