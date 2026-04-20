package android.window

import android.os.Parcel
import android.os.Parcelable

open class TaskSnapshot protected constructor(`in`: Parcel?) : Parcelable {
    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(parcel: Parcel, i: Int) {
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<TaskSnapshot?> =
            object : Parcelable.Creator<TaskSnapshot?> {
                override fun createFromParcel(`in`: Parcel?): TaskSnapshot {
                    return TaskSnapshot(`in`)
                }

                override fun newArray(size: Int): Array<TaskSnapshot?> {
                    return arrayOfNulls<TaskSnapshot>(size)
                }
            }
    }
}
