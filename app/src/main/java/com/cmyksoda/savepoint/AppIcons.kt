package com.cmyksoda.savepoint

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

enum class AppIcon(val alias: String, val label: String, val background: Int, val foreground: Int) {
    MIDNIGHT("MainActivity", "Midnight", R.drawable.icon_background_midnight, R.drawable.icon_handheld_midnight),
    LAVENDER("IconLavender", "Lavender", R.drawable.icon_background_lavender, R.drawable.icon_handheld_lavender),
    BLUSH("IconBlush", "Blush", R.drawable.icon_background_blush, R.drawable.icon_handheld_blush),
    BERRY("IconBerry", "Berry", R.drawable.icon_background_berry, R.drawable.icon_handheld_berry),
}

private fun component(context: Context, icon: AppIcon) =
    ComponentName(context.packageName, "com.cmyksoda.savepoint.${icon.alias}")

fun currentAppIcon(context: Context): AppIcon =
    AppIcon.entries.firstOrNull { icon ->
        when (context.packageManager.getComponentEnabledSetting(component(context, icon))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == AppIcon.MIDNIGHT
            else -> false
        }
    } ?: AppIcon.MIDNIGHT

fun setAppIcon(context: Context, chosen: AppIcon) {
    val packages = context.packageManager
    // Turn the new launcher entry on first, so the app always has one.
    packages.setComponentEnabledSetting(
        component(context, chosen),
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        PackageManager.DONT_KILL_APP,
    )
    // Android closes a task whose launcher entry is turned off, so reopen in a task of the new one.
    context.startActivity(
        Intent()
            .setComponent(component(context, chosen))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            .putExtra("settings", true)
    )
    (context as? Activity)?.finishAndRemoveTask()
    AppIcon.entries
        .filter { it != chosen }
        .forEach {
            packages.setComponentEnabledSetting(
                component(context, it),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
}

@Composable
fun AppIconPicker() {
    val context = LocalContext.current
    var current by remember { mutableStateOf(currentAppIcon(context)) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        AppIcon.entries.forEach { icon ->
            val selected = icon == current
            Column(
                Modifier.clip(RoundedCornerShape(16.dp))
                    .selectable(selected, role = Role.RadioButton) {
                        if (!selected) {
                            setAppIcon(context, icon)
                            current = icon
                        }
                    }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier.size(60.dp)
                        .border(
                            2.dp,
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0f),
                            CircleShape,
                        )
                        .padding(5.dp)
                        .clip(CircleShape)
                ) {
                    // Launchers show the middle two thirds of an adaptive icon.
                    Image(painterResource(icon.background), null, Modifier.fillMaxSize().scale(1.5f))
                    Image(painterResource(icon.foreground), null, Modifier.fillMaxSize().scale(1.5f))
                }
                Text(
                    icon.label,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
