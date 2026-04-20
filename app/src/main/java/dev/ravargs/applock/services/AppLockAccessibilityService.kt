package dev.ravargs.applock.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.getSystemService
import dev.ravargs.applock.core.broadcast.DeviceAdmin
import dev.ravargs.applock.core.utils.LogUtils
import dev.ravargs.applock.core.utils.appLockRepository
import dev.ravargs.applock.core.utils.enableAccessibilityServiceWithShizuku
import dev.ravargs.applock.data.repository.AppLockRepository
import dev.ravargs.applock.data.repository.BackendImplementation
import dev.ravargs.applock.features.lockscreen.ui.PasswordOverlayActivity
import dev.ravargs.applock.services.AppLockConstants.ACCESSIBILITY_SETTINGS_CLASSES
import dev.ravargs.applock.services.AppLockConstants.EXCLUDED_APPS
import rikka.shizuku.Shizuku

@SuppressLint("AccessibilityPolicy")
class AppLockAccessibilityService : AccessibilityService() {
    private val appLockRepository: AppLockRepository by lazy { applicationContext.appLockRepository() }
    private val keyboardPackages: List<String> by lazy { getKeyboardPackageNames() }

    private var recentsOpen = false
    private var lastForegroundPackage = ""

    companion object {
        private const val TAG = "AppLockAccessibility"
        private const val DEVICE_ADMIN_SETTINGS_PACKAGE = "com.android.settings"
        private const val APP_PACKAGE_PREFIX = "dev.ravargs.applock"

        @Volatile
        var isServiceRunning = false
    }

    private val screenStateReceiver = object: android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            try {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    LogUtils.d(TAG, "Screen off detected. Resetting AppLock state.")
                    AppLockManager.stopUsageTrackingForActiveApp(appLockRepository)
                    AppLockManager.stopPeriodicLimitCheck()
                    AppLockManager.isLockScreenShown.set(false)
                    AppLockManager.clearTemporarilyUnlockedApp()
                    // Optional: Clear all unlock timestamps to force re-lock on next unlock
                    AppLockManager.appUnlockTimes.clear()
                }
            } catch (e: Exception) {
                logError("Error in screenStateReceiver", e)
            }
        }
    }
    private val launcherAppsCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) {
            LogUtils.d(TAG, "Dynamic detection: Package added - $packageName")
            AppLockManager.handleNewAppInstalled(applicationContext, packageName)
        }

        override fun onPackageChanged(packageName: String, user: UserHandle) {}
        override fun onPackageRemoved(packageName: String, user: UserHandle) {}
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {}
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {}
    }

    override fun onCreate() {
        super.onCreate()
        try {
            isServiceRunning = true
            AppLockManager.resetAllStates()
            startPrimaryBackendService()

            val filter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenStateReceiver, filter)

            val launcherApps = getSystemService(android.content.Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            if (launcherApps != null) {
                launcherApps.registerCallback(launcherAppsCallback)
            } else {
                Log.e(TAG, "LauncherApps service not found!")
            }
        } catch (e: Exception) {
            logError("Error in onCreate", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            serviceInfo = serviceInfo.apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_WINDOWS_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                packageNames = null
            }

            Log.d(TAG, "Accessibility service connected")
            AppLockManager.resetAllStates()
            AppLockManager.resetRestartAttempts(TAG)
            val backend = appLockRepository.getBackendImplementation()
            appLockRepository.setActiveBackend(BackendImplementation.ACCESSIBILITY)
        } catch (e: Exception) {
            logError("Error in onServiceConnected", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        Log.d(TAG, event.toString())
        try {
            handleAccessibilityEvent(event)
        } catch (e: Exception) {
            logError("Unhandled error in onAccessibilityEvent", e)
        }
    }

    private fun handleAccessibilityEvent(event: AccessibilityEvent) {
        if (appLockRepository.isAntiUninstallEnabled() &&
            event.packageName == DEVICE_ADMIN_SETTINGS_PACKAGE
        ) {
            checkForDeviceAdminDeactivation(event)
        }

        // Early return if protection is disabled or service is not running
        if (!appLockRepository.isProtectEnabled()) {
            return
        }
        
        if (!isServiceRunning) {
            return
        }

        val packageName = event.packageName?.toString() ?: return

        // Handle window state changes
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            try {
                handleWindowStateChanged(event)
            } catch (e: Exception) {
                logError("Error handling window state change", e)
                return
            }
        }

        // Skip processing if recents are open
        if (recentsOpen) {
            LogUtils.d(TAG, "Recents opened, ignoring accessibility event for $packageName")
            return
        }

            try {
                processPackageLocking(packageName)
            } catch (e: Exception) {
            logError("Error processing package locking for $packageName", e)
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val isRecentlyOpened = isRecentlyOpened(event)
        val isHomeScreen = isHomeScreen(event)

        when {
            isRecentlyOpened -> {
                LogUtils.d(TAG, "Entering recents")
                recentsOpen = true
            }

            isHomeScreenTransition(event) && recentsOpen -> {
                LogUtils.d(TAG, "Transitioning to home screen from recents")
                recentsOpen = false
                clearTemporarilyUnlockedAppIfNeeded()
            }

            isHomeScreen -> {
                LogUtils.d(TAG, "On home screen")
                recentsOpen = false
                clearTemporarilyUnlockedAppIfNeeded()
            }

            isAppSwitchedFromRecents(event) -> {
                LogUtils.d(TAG, "App switched from recents")
                recentsOpen = false
                clearTemporarilyUnlockedAppIfNeeded(event.packageName?.toString())
            }
        }
    }


    @SuppressLint("InlinedApi")
    private fun isRecentlyOpened(event: AccessibilityEvent): Boolean {
        return (event.packageName == getSystemDefaultLauncherPackageName() &&
                event.contentChangeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED) ||
                (event.text.toString().lowercase().contains("recent apps"))
    }

    private fun isHomeScreen(event: AccessibilityEvent): Boolean {
        // Simplified for environment robustness (Waydroid/Generic Launchers)
        return event.packageName == getSystemDefaultLauncherPackageName()
    }

    @SuppressLint("InlinedApi")
    private fun isHomeScreenTransition(event: AccessibilityEvent): Boolean {
        return event.contentChangeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED &&
                event.packageName == getSystemDefaultLauncherPackageName()
    }

    private fun isAppSwitchedFromRecents(event: AccessibilityEvent): Boolean {
        return event.packageName != getSystemDefaultLauncherPackageName() && recentsOpen
    }

    private fun clearTemporarilyUnlockedAppIfNeeded(newPackage: String? = null) {
        val shouldClear = newPackage == null ||
                (newPackage != AppLockManager.temporarilyUnlockedApp &&
                        newPackage !in appLockRepository.getTriggerExcludedApps())

        if (shouldClear) {
            LogUtils.d(TAG, "Clearing temporarily unlocked app")
            AppLockManager.clearTemporarilyUnlockedApp()
        }
    }

    private fun isValidPackageForLocking(packageName: String): Boolean {
        // Check if device is locked
        if (applicationContext.isDeviceLocked()) {
             return false
        }

        // Check if accessibility should handle locking
        if (!shouldAccessibilityHandleLocking()) {
            return false
        }

        // Skip keyboard packages
        if (packageName in keyboardPackages) {
            return false
        }

        // Skip excluded packages
        if (packageName == APP_PACKAGE_PREFIX ||
            packageName in EXCLUDED_APPS
        ) {
            LogUtils.d(TAG, "Package $packageName is excluded from locking.")
            return false
        }

        return true
    }

    private fun processPackageLocking(packageName: String) {
        val currentForegroundPackage = packageName
        val triggeringPackage = lastForegroundPackage
        lastForegroundPackage = currentForegroundPackage

        // Basic exclusions for stability
        val triggerExclusions = appLockRepository.getTriggerExcludedApps()
        if (triggeringPackage in triggerExclusions) {
            if (appLockRepository.isLoggingEnabled()) {
                LogUtils.d(TAG, "Skipping lock for $packageName: Trigger $triggeringPackage is excluded")
            }
            // We only return if it's a known excluded trigger to avoid infinite loops
            // BUT we still check for locking later if needed. 
            // Actually, let's keep it simple for now and just log it.
        }

        // Usage tracking integration
        if (triggeringPackage != currentForegroundPackage) {
            AppLockManager.stopUsageTracking(triggeringPackage, appLockRepository)
            AppLockManager.stopPeriodicLimitCheck()
            AppLockManager.startUsageTracking(currentForegroundPackage)
        }

        // Fix for "Lock Immediately" not working when switching between apps
        val unlockedApp = AppLockManager.temporarilyUnlockedApp
        if (unlockedApp.isNotEmpty() &&
            unlockedApp != currentForegroundPackage &&
            currentForegroundPackage !in appLockRepository.getTriggerExcludedApps()
        ) {
            LogUtils.d(
                TAG,
                "Switched from unlocked app $unlockedApp to $currentForegroundPackage."
            )
            AppLockManager.setRecentlyLeftApp(unlockedApp)
            AppLockManager.clearTemporarilyUnlockedApp()
        }

        checkAndLockApp(currentForegroundPackage, triggeringPackage, System.currentTimeMillis())
    }

    private fun shouldAccessibilityHandleLocking(): Boolean {
        return when (appLockRepository.getBackendImplementation()) {
            BackendImplementation.ACCESSIBILITY -> true
            BackendImplementation.SHIZUKU -> !applicationContext.isServiceRunning(
                ShizukuAppLockService::class.java
            )

            BackendImplementation.USAGE_STATS -> !applicationContext.isServiceRunning(
                ExperimentalAppLockService::class.java
            )
        }
    }

    private fun checkAndLockApp(packageName: String, triggeringPackage: String, currentTime: Long) {
        // Return early if lock screen is already shown or biometric auth is in progress
        val isShown = AppLockManager.isLockScreenShown.get()
        val biometricState = AppLockManager.currentBiometricState
        
        if (isShown || biometricState == BiometricState.AUTH_STARTED) {
            LogUtils.d(TAG, "Skipping check for $packageName: shown=$isShown, biometric=$biometricState")
            return
        }

        // Return if package is not locked
        val lockedApps = appLockRepository.getLockedApps()
        if (packageName !in lockedApps) {
            if (appLockRepository.isLoggingEnabled()) {
                LogUtils.d(TAG, "Package $packageName is NOT in locked apps list. Skipping.")
            }
            return
        }

        // Return if app is temporarily unlocked
        if (AppLockManager.isAppTemporarilyUnlocked(packageName)) {
            LogUtils.d(TAG, "App $packageName is temporarily unlocked. Skipping.")
            return
        }

        val limitMinutes = appLockRepository.getTimeLimit(packageName)
        if (limitMinutes > 0) {
            val isReached = AppLockManager.isTimeLimitReached(packageName, appLockRepository)
            
            // Log exactly what is happening
            LogUtils.d(TAG, "Time limit check for $packageName: reached=$isReached")
            
            // If NOT reached, monitor periodically and return
            if (!isReached) {
                LogUtils.d(TAG, "Time limit not reached for $packageName ($limitMinutes min). Monitoring periodically.")
                AppLockManager.startPeriodicLimitCheck(packageName, appLockRepository) {
                    showLockScreenOverlay(packageName, triggeringPackage)
                }
                return
            }
            
            // If reached, it falls through to standard locking logic below
            LogUtils.d(TAG, "Time limit reached for $packageName, falling through to standard locking.")
        }

        AppLockManager.clearTemporarilyUnlockedApp()

        val unlockDurationMinutes = appLockRepository.getUnlockTimeDuration()
        val unlockTimestamp = AppLockManager.appUnlockTimes[packageName] ?: 0L

        LogUtils.d(
            TAG,
            "checkAndLockApp: pkg=$packageName, duration=$unlockDurationMinutes min, unlockTime=$unlockTimestamp, currentTime=$currentTime, isLockScreenShown=${AppLockManager.isLockScreenShown.get()}"
        )

        if (unlockDurationMinutes > 0 && unlockTimestamp > 0) {
            if (unlockDurationMinutes >= 10_000) {
                return
            }

            val durationMillis = unlockDurationMinutes.toLong() * 60L * 1000L

            val elapsedMillis = currentTime - unlockTimestamp

            LogUtils.d(
                TAG,
                "Grace period check: elapsed=${elapsedMillis}ms (${elapsedMillis / 1000}s), duration=${durationMillis}ms (${durationMillis / 1000}s)"
            )

            if (elapsedMillis < durationMillis) {
                return
            }

            LogUtils.d(TAG, "Unlock grace period expired for $packageName. Clearing timestamp.")
            AppLockManager.appUnlockTimes.remove(packageName)
            AppLockManager.clearTemporarilyUnlockedApp()
        }

        if (AppLockManager.isLockScreenShown.get() ||
            AppLockManager.currentBiometricState == BiometricState.AUTH_STARTED
        ) {
            LogUtils.d(TAG, "Lock screen already shown or biometric auth in progress, skipping")
            return
        }

        showLockScreenOverlay(packageName, triggeringPackage)
    }

    private fun showLockScreenOverlay(packageName: String, triggeringPackage: String) {
        LogUtils.d(TAG, "Locked app detected: $packageName. Showing overlay.")
        // isLockScreenShown will be set to true by the Activity onResume

        val intent = Intent(this, PasswordOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_FROM_BACKGROUND or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("locked_package", packageName)
            putExtra("triggering_package", triggeringPackage)
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            logError("Failed to start password overlay", e)
        }
    }

    private fun checkForDeviceAdminDeactivation(event: AccessibilityEvent) {
        Log.d(TAG, "Checking for device admin deactivation for event: $event")

        // Check if user is trying to deactivate the accessibility service
        if (isDeactivationAttempt(event)) {
            Log.d(TAG, "Blocking accessibility service deactivation")
            blockDeactivationAttempt()
            return
        }

        // Check if on device admin page and our app is visible
        val isDeviceAdminPage = isDeviceAdminPage(event)
        //val isOurAppVisible = findNodeWithTextContaining(rootNode, "App Lock") != null ||
        //        findNodeWithTextContaining(rootNode, "AppLock") != null

        LogUtils.d(TAG, "User is on device admin page: $isDeviceAdminPage, $event")

        if (!isDeviceAdminPage) {
            return
        }

        blockDeviceAdminDeactivation()
    }

    private fun isDeactivationAttempt(event: AccessibilityEvent): Boolean {
        val isAccessibilitySettings = event.className in ACCESSIBILITY_SETTINGS_CLASSES &&
                event.text.any { it.contains("App Lock") }
        val isSubSettings = event.className == "com.android.settings.SubSettings" &&
                event.text.any { it.contains("App Lock") }
        val isAlertDialog =
            event.packageName == "com.google.android.packageinstaller" && event.className == "android.app.AlertDialog" && event.text.toString()
                .lowercase().contains("App Lock")

        return isAccessibilitySettings || isSubSettings || isAlertDialog
    }

    @SuppressLint("InlinedApi")
    private fun blockDeactivationAttempt() {
        try {
            performGlobalAction(GLOBAL_ACTION_BACK)
            performGlobalAction(GLOBAL_ACTION_HOME)
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } catch (e: Exception) {
            logError("Error blocking deactivation attempt", e)
        }
    }

    private fun isDeviceAdminPage(event: AccessibilityEvent): Boolean {
        val hasDeviceAdminDescription = event.contentDescription?.toString()?.lowercase()
            ?.contains("Device admin app") == true &&
                event.className == "android.widget.FrameLayout"

        val isAdminConfigClass =
            event.className!!.contains("DeviceAdminAdd") || event.className!!.contains("DeviceAdminSettings")

        return hasDeviceAdminDescription || isAdminConfigClass
    }

    @SuppressLint("InlinedApi")
    private fun blockDeviceAdminDeactivation() {
        try {
            val dpm: DevicePolicyManager? = getSystemService()
            val component = ComponentName(this, DeviceAdmin::class.java)

            if (dpm?.isAdminActive(component) == true) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                performGlobalAction(GLOBAL_ACTION_BACK)
                performGlobalAction(GLOBAL_ACTION_HOME)
                Thread.sleep(100)
                performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                Toast.makeText(
                    this,
                    "Disable anti-uninstall from AppLock settings to remove this restriction.",
                    Toast.LENGTH_LONG
                ).show()
                Log.w(TAG, "Blocked device admin deactivation attempt.")
            }
        } catch (e: Exception) {
            logError("Error blocking device admin deactivation", e)
        }
    }

    private fun findNodeWithTextContaining(
        node: AccessibilityNodeInfo,
        text: String
    ): AccessibilityNodeInfo? {
        return try {
            if (node.text?.toString()?.contains(text, ignoreCase = true) == true) {
                return node
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val result = findNodeWithTextContaining(child, text)
                if (result != null) return result
            }
            null
        } catch (e: Exception) {
            logError("Error finding node with text: $text", e)
            null
        }
    }

    private fun getKeyboardPackageNames(): List<String> {
        return try {
            getSystemService<InputMethodManager>()?.enabledInputMethodList?.map { it.packageName }
                ?: emptyList()
        } catch (e: Exception) {
            logError("Error getting keyboard package names", e)
            emptyList()
        }
    }

    fun getSystemDefaultLauncherPackageName(): String {
        return try {
            val packageManager = packageManager
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }

            val resolveInfoList: List<ResolveInfo> = packageManager.queryIntentActivities(
                homeIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            )

            val systemLauncher = resolveInfoList.find { resolveInfo ->
                val isSystemApp = (resolveInfo.activityInfo.applicationInfo.flags and
                        android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                val isOurApp = resolveInfo.activityInfo.packageName == packageName

                isSystemApp && !isOurApp
            }

            systemLauncher?.activityInfo?.packageName?.also {
                if (it.isEmpty()) {
                    Log.w(TAG, "Could not find a clear system launcher package name.")
                }
            } ?: ""
        } catch (e: Exception) {
            logError("Error getting system default launcher package", e)
            ""
        }
    }

    private fun startPrimaryBackendService() {
        try {
            AppLockManager.stopAllOtherServices(this, AppLockAccessibilityService::class.java)

            when (appLockRepository.getBackendImplementation()) {
                BackendImplementation.SHIZUKU -> {
                    Log.d(TAG, "Starting Shizuku service as primary backend")
                    startService(Intent(this, ShizukuAppLockService::class.java))
                }

                BackendImplementation.USAGE_STATS -> {
                    Log.d(TAG, "Starting Experimental service as primary backend")
                    startService(Intent(this, ExperimentalAppLockService::class.java))
                }

                else -> {
                    Log.d(TAG, "Accessibility service is the primary backend.")
                }
            }
        } catch (e: Exception) {
            logError("Error starting primary backend service", e)
        }
    }

    override fun onInterrupt() {
        try {
            LogUtils.d(TAG, "Accessibility service interrupted")
        } catch (e: Exception) {
            logError("Error in onInterrupt", e)
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        return try {
            Log.d(TAG, "Accessibility service unbound")
            isServiceRunning = false
            AppLockManager.startFallbackServices(this, AppLockAccessibilityService::class.java)

            if (Shizuku.pingBinder() && appLockRepository.isAntiUninstallEnabled()) {
                enableAccessibilityServiceWithShizuku(ComponentName(packageName, javaClass.name))
            }

            super.onUnbind(intent)
        } catch (e: Exception) {
            logError("Error in onUnbind", e)
            super.onUnbind(intent)
        }
    }

    override fun onDestroy() {
        try {
            super.onDestroy()
            isServiceRunning = false
            LogUtils.d(TAG, "Accessibility service destroyed")

            try {
                unregisterReceiver(screenStateReceiver)
            } catch (_: IllegalArgumentException) {
                // Ignore if not registered
                Log.w(TAG, "Receiver not registered or already unregistered")
            }

            val launcherApps = getSystemService(android.content.Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.unregisterCallback(launcherAppsCallback)

            AppLockManager.isLockScreenShown.set(false)
            AppLockManager.startFallbackServices(this, AppLockAccessibilityService::class.java)
        } catch (e: Exception) {
            logError("Error in onDestroy", e)
        }
    }

    /**
     * Logs errors silently without crashing the service.
     * Only logs to debug level to avoid unnecessary noise in production.
     */
    private fun logError(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }
}
