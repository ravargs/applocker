package dev.ravargs.applock.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.content.pm.LauncherApps
import android.os.IBinder
import android.os.UserHandle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import dev.ravargs.applock.R
import dev.ravargs.applock.core.broadcast.DeviceAdmin
import dev.ravargs.applock.core.utils.LogUtils
import dev.ravargs.applock.core.utils.appLockRepository
import dev.ravargs.applock.data.repository.AppLockRepository
import dev.ravargs.applock.data.repository.AppLockRepository.Companion.shouldStartService
import dev.ravargs.applock.data.repository.BackendImplementation
import dev.ravargs.applock.features.lockscreen.ui.PasswordOverlayActivity
import dev.ravargs.applock.shizuku.ShizukuActivityManager
import rikka.shizuku.Shizuku

class ShizukuAppLockService : Service() {
    private val appLockRepository: AppLockRepository by lazy { applicationContext.appLockRepository() }
    private var shizukuActivityManager: ShizukuActivityManager? = null
    private var previousForegroundPackage = ""

    private val notificationManager: NotificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    companion object {
        private const val TAG = "ShizukuAppLockService"
        private const val NOTIFICATION_ID = 112
        private const val CHANNEL_ID = "ShizukuAppLockServiceChannel"

        @Volatile
        var isServiceRunning = false
    }

    private val screenStateReceiver = object: android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                LogUtils.d(TAG, "Screen off detected in Shizuku service. Resetting state.")
                AppLockManager.stopUsageTrackingForActiveApp(appLockRepository)
                AppLockManager.stopPeriodicLimitCheck()
                AppLockManager.isLockScreenShown.set(false)
                AppLockManager.clearTemporarilyUnlockedApp()
            }
        }
    }

    private val launcherAppsCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) {
            LogUtils.d(TAG, "Dynamic detection (Shizuku): Package added - $packageName")
            AppLockManager.handleNewAppInstalled(applicationContext, packageName)
        }

        override fun onPackageChanged(packageName: String, user: UserHandle) {}
        override fun onPackageRemoved(packageName: String, user: UserHandle) {}
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {}
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {}
    }

    override fun onCreate() {
        super.onCreate()
        AppLockManager.resetAllStates()
        AppLockManager.reportBiometricAuthFinished() // Reset to IDLE
        
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenStateReceiver, filter)

        val launcherApps = getSystemService(android.content.Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        launcherApps.registerCallback(launcherAppsCallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogUtils.d(TAG, "ShizukuAppLockService started. Running: $isServiceRunning")

        if (isServiceRunning) return START_STICKY
        isServiceRunning = true

        if (!shouldStartService(appLockRepository, this::class.java) || !isShizukuAvailable()) {
            Log.e(TAG, "Service not needed or Shizuku not ready. Triggering fallback if necessary.")
            isServiceRunning = false
            AppLockManager.startFallbackServices(this, ShizukuAppLockService::class.java)
            stopSelf()
            return START_NOT_STICKY
        }

        AppLockManager.resetRestartAttempts(TAG)
        appLockRepository.setActiveBackend(BackendImplementation.SHIZUKU)
        AppLockManager.stopAllOtherServices(this, this::class.java)

        setupShizukuActivityManager()

        val shizukuStarted = shizukuActivityManager?.start() == true
        if (!shizukuStarted) {
            Log.e(TAG, "Shizuku failed to start, triggering fallback")
            isServiceRunning = false
            AppLockManager.startFallbackServices(this, ShizukuAppLockService::class.java)
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundService()

        return START_STICKY
    }

    override fun onDestroy() {
        LogUtils.d(TAG, "ShizukuAppLockService killed.")

        shizukuActivityManager?.stop()

        if (isServiceRunning && shouldStartService(appLockRepository, this::class.java)) {
            LogUtils.d(TAG, "Service destroyed unexpectedly, starting fallback")
            AppLockManager.startFallbackServices(this, ShizukuAppLockService::class.java)
        }

        try {
            unregisterReceiver(screenStateReceiver)
            val launcherApps = getSystemService(android.content.Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.unregisterCallback(launcherAppsCallback)
        } catch (e: Exception) {
            // ignore
        }

        isServiceRunning = false
        notificationManager.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onUnbind(intent: Intent?): Boolean {
        LogUtils.d(TAG, "ShizukuAppLockService unbound. Checking for necessary restart.")
        if (shouldStartService(appLockRepository, this::class.java)) {
            AppLockManager.startFallbackServices(this, ShizukuAppLockService::class.java)
        }
        return super.onUnbind(intent)
    }

    private fun isShizukuAvailable(): Boolean {
        return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun startForegroundService() {
        createNotificationChannel()
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val type = determineForegroundServiceType()
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun determineForegroundServiceType(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val component = ComponentName(this, DeviceAdmin::class.java)

            return if (dpm.isAdminActive(component)) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
        }
        return 0
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "AppLock Service",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(serviceChannel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AppLock")
            .setContentText("Protecting your apps with Shizuku")
            .setSmallIcon(R.drawable.baseline_shield_24)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    private fun setupShizukuActivityManager() {
        shizukuActivityManager =
            ShizukuActivityManager(this, appLockRepository) { packageName, _, timeMillis ->
                val triggeringPackage = previousForegroundPackage
                previousForegroundPackage = packageName

                if (AppLockManager.isLockScreenShown.get() || packageName == this.packageName) {
                    return@ShizukuActivityManager
                }

                LogUtils.d(TAG, "Current package=$packageName, trigger=$triggeringPackage")
                
                // Usage tracking integration
                if (triggeringPackage != packageName) {
                    AppLockManager.stopUsageTracking(triggeringPackage, appLockRepository)
                    AppLockManager.stopPeriodicLimitCheck()
                    AppLockManager.startUsageTracking(packageName)
                }

                checkAndLockApp(packageName, triggeringPackage, timeMillis)
            }
    }

    private fun checkAndLockApp(packageName: String, triggeringPackage: String, currentTime: Long) {
        val lockedApps = appLockRepository.getLockedApps()

        if (packageName !in lockedApps) return

        val limitMinutes = appLockRepository.getTimeLimit(packageName)
        if (limitMinutes > 0) {
            val isReached = AppLockManager.isTimeLimitReached(packageName, appLockRepository)
            
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

        val isShown = AppLockManager.isLockScreenShown.get()
        val biometricState = AppLockManager.currentBiometricState
        
        if (isShown || biometricState == BiometricState.AUTH_STARTED) {
            LogUtils.d(TAG, "Skipping check for $packageName: shown=$isShown, biometric=$biometricState")
            return
        }

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

            val durationMillis = unlockDurationMinutes.toLong() * 60_000L

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
        }

        if (AppLockManager.isLockScreenShown.get()) {
            LogUtils.d(TAG, "Lock screen already shown, skipping")
            return
        }

        AppLockManager.reportBiometricAuthFinished()
        showLockScreenOverlay(packageName, triggeringPackage)
    }

    private fun showLockScreenOverlay(packageName: String, triggeringPackage: String) {
        LogUtils.d(TAG, "Locked app: $packageName. Showing overlay.")
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
            Log.e(TAG, "Error starting overlay for: $packageName", e)
            AppLockManager.isLockScreenShown.set(false)
        }
    }
}
