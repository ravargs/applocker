package android.app

import android.content.ComponentName

class TaskInfo {
    fun getActivityType() = 0

    fun isVisible() = false

    val topActivity: ComponentName? = null
}
