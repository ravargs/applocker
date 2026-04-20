package android.app

import android.os.Parcel
import android.os.Parcelable
import dev.rikka.tools.refine.RefineAs

@RefineAs(ActivityManager::class)
object ActivityManagerHidden {
    val isHighEndGfx: Boolean
        get() {
            throw RuntimeException("Stub!")
        }

    /**
     * Represents a task snapshot.
     */
    class TaskSnapshot protected constructor(`in`: Parcel?) : Parcelable {
        override fun describeContents(): Int {
            return 0
        }

        override fun writeToParcel(parcel: Parcel, i: Int) {
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<TaskSnapshot?>? = null
        }
    }
}
