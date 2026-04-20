package android.app

import android.app.ActivityManager.RunningTaskInfo
import android.os.Binder
import android.os.IBinder
import android.os.IInterface

@Suppress("unused")
interface IActivityTaskManage : IInterface {
    //    XIAOMI
    fun getTasks(
        maxNum: Int,
        filterOnlyVisibleRecents: Boolean,
        keepIntentExtra: Boolean
    ): MutableList<RunningTaskInfo?>?

    // https://github.com/gkd-kit/gkd/issues/58#issuecomment-1736843795
    fun getTasks(
        maxNum: Int,
        filterOnlyVisibleRecents: Boolean,
        keepIntentExtra: Boolean,
        displayId: Int
    ): MutableList<RunningTaskInfo?>?

    // https://github.com/gkd-kit/gkd/issues/58#issuecomment-1732245703
    fun getTasks(maxNum: Int): MutableList<RunningTaskInfo?>?

    fun registerTaskStackListener(listener: ITaskStackListener?)
    fun unregisterTaskStackListener(listener: ITaskStackListener?)

    object Stub : Binder(), IActivityTaskManager {
        fun asInterface(obj: IBinder?): IActivityTaskManager? {
            throw RuntimeException("Stub!")
        }

        override fun getTasks(
            maxNum: Int,
            filterOnlyVisibleRecents: Boolean,
            keepIntentExtra: Boolean
        ): MutableList<RunningTaskInfo?>? {
            // This is a stub implementation. Actual implementation would interact with the system service.
            throw RuntimeException("Stub!")
        }

        override fun getTasks(
            maxNum: Int,
            filterOnlyVisibleRecents: Boolean,
            keepIntentExtra: Boolean,
            displayId: Int
        ): MutableList<RunningTaskInfo?>? {
            throw RuntimeException("Stub!")
        }

        override fun getTasks(maxNum: Int): MutableList<RunningTaskInfo?>? {
            throw RuntimeException("Stub!")
        }

        override fun asBinder(): IBinder? {
            throw RuntimeException("Stub!")
        }


    }
}
