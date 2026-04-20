package dev.ravargs.applock.features.applist.domain

import android.graphics.drawable.Drawable

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable
)
