package android.app

import android.os.Binder
import android.os.RemoteException

interface IProcessObserver {
    @Throws(RemoteException::class)
    fun onForegroundActivitiesChanged(pid: Int, uid: Int, foregroundActivity: Boolean)

    @Throws(RemoteException::class)
    fun onProcessDied(pid: Int, uid: Int)

    // no longer exists from API 26
    @Throws(RemoteException::class)
    fun onProcessStateChanged(pid: Int, uid: Int, procState: Int)

    // from Q beta 3
    @Throws(RemoteException::class)
    fun onForegroundServicesChanged(pid: Int, uid: Int, serviceTypes: Int)

    // from android-14.0.0_r50
    @Throws(RemoteException::class)
    fun onProcessStarted(
        pid: Int,
        processUid: Int,
        packageUid: Int,
        packageName: String,
        processName: String
    )

    open class Stub : Binder(), IProcessObserver {
        override fun onForegroundActivitiesChanged(
            pid: Int,
            uid: Int,
            foregroundActivity: Boolean
        ) {
            throw RuntimeException("Stub!")
        }

        override fun onProcessDied(pid: Int, uid: Int) {
            throw RuntimeException("Stub!")
        }

        override fun onProcessStateChanged(
            pid: Int,
            uid: Int,
            procState: Int
        ) {
            throw RuntimeException("Stub!")
        }

        override fun onForegroundServicesChanged(
            pid: Int,
            uid: Int,
            serviceTypes: Int
        ) {
            throw RuntimeException("Stub!")
        }

        override fun onProcessStarted(
            pid: Int,
            processUid: Int,
            packageUid: Int,
            packageName: String,
            processName: String
        ) {
            throw RuntimeException("Stub!")
        }
    }
}
