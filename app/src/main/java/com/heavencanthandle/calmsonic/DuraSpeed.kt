package com.heavencanthandle.calmsonic

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

fun openDuraSpeedSettings(context: Context) {
    val candidates = listOf(
        Intent().setClassName(
            "com.mediatek.duraspeed",
            "com.mediatek.duraspeed.view.DatabaseAppActivity",
        ),
        Intent().setClassName(
            "com.android.settings",
            "com.android.settings.Settings\$HighPowerApplicationsActivity",
        ),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        },
    )
    for (intent in candidates) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return
            }
        } catch (_: Exception) {
            // Try the next Kompakt / Settings fallback.
        }
    }
}
