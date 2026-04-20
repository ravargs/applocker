package dev.ravargs.applock.core.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.ravargs.applock.core.utils.LogUtils
import dev.ravargs.applock.core.utils.appLockRepository
import dev.ravargs.applock.data.repository.BackendImplementation
import dev.ravargs.applock.services.AppLockAccessibilityService
import dev.ravargs.applock.services.ExperimentalAppLockService
import dev.ravargs.applock.services.ShizukuAppLockService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val repository = context.appLockRepository()
        
        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "App package replaced, clearing old logs")
                // Clear all old logs on app update
                LogUtils.clearAllLogs()
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                try {
                    startAppropriateServices(context, repository)
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting services on boot", e)
                }
            }
            else -> {
                Log.w(TAG, "Invalid intent action: ${intent.action}")
            }
        }
    }

    private fun startAppropriateServices(
        context: Context,
        repository: dev.ravargs.applock.data.repository.AppLockRepository
    ) {
        if (repository.isAntiUninstallEnabled()) {
            startService(context, AppLockAccessibilityService::class.java)
        }

        when (repository.getBackendImplementation()) {
            BackendImplementation.SHIZUKU -> {
                startService(context, ShizukuAppLockService::class.java)
            }

            BackendImplementation.ACCESSIBILITY -> {
                startService(context, AppLockAccessibilityService::class.java)
            }

            BackendImplementation.USAGE_STATS -> {
                startService(context, ExperimentalAppLockService::class.java)
            }
        }
    }

    private fun startService(context: Context, serviceClass: Class<*>) {
        try {
            val serviceIntent = Intent(context, serviceClass)
            context.startService(serviceIntent)
            Log.d(TAG, "Started service: ${serviceClass.simpleName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service: ${serviceClass.simpleName}", e)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
