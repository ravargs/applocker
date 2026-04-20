package dev.ravargs.applock.core.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.ravargs.applock.core.utils.LogUtils
import dev.ravargs.applock.data.repository.LockedAppsRepository
import dev.ravargs.applock.data.repository.PreferencesRepository
import dev.ravargs.applock.services.AppLockManager
import android.widget.Toast
import android.os.Handler
import android.os.Looper

class PackageReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "PackageReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_PACKAGE_ADDED || action == Intent.ACTION_PACKAGE_REPLACED) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            LogUtils.d(TAG, "Manifest detection ($action): $packageName")
            AppLockManager.handleNewAppInstalled(context, packageName)
        }
    }
}
