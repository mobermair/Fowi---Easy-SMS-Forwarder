package at.om21.fowi

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

private class SetupStep(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val done: Boolean,
    val required: Boolean,
    val actionLabel: String,
    val onAction: () -> Unit
)

@Composable
internal fun HomeScreen(
    forwardingEnabled: Boolean,
    onForwardingChange: (Boolean) -> Unit,
    rules: List<ForwardRule>,
    onOpenRules: () -> Unit,
    onOpenHistory: (HistoryFilter) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val history = observePreferences(ForwardLog.PREFERENCES_NAME, ForwardLog::load)
    val sentTotal = observePreferences(ForwardLog.PREFERENCES_NAME, ForwardLog::sentTotal)
    val activeRules = rules.count { it.enabled }

    // Permissions and battery settings can change in the system settings at any time,
    // so every resume triggers a fresh check.
    var checkGeneration by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { checkGeneration++ }
    val smsGranted = remember(checkGeneration) { hasSmsPermissions(context) }
    val notificationsEnabled = remember(checkGeneration) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val contactsGranted = remember(checkGeneration) { ContactNames.canRead(context) }
    val batteryUnrestricted = remember(checkGeneration) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }

    // Once a permission was denied, Android may stop showing its dialog, so the next
    // tap opens the system settings instead.
    var smsDenied by rememberSaveable { mutableStateOf(false) }
    var notificationsDenied by rememberSaveable { mutableStateOf(false) }
    var contactsDenied by rememberSaveable { mutableStateOf(false) }
    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        smsDenied = !results.values.all { it }
        checkGeneration++
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsDenied = !granted
        checkGeneration++
    }
    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        contactsDenied = !granted
        checkGeneration++
    }

    // The device settings come first; creating a rule is the last step.
    val steps = listOf(
        SetupStep(
            icon = Icons.Outlined.Sms,
            title = stringResource(R.string.setup_sms_title),
            description = stringResource(
                if (smsGranted) R.string.setup_sms_done else R.string.setup_sms_todo
            ),
            done = smsGranted,
            required = true,
            actionLabel = stringResource(R.string.action_allow),
            onAction = {
                if (smsDenied) {
                    openAppSettings(context)
                } else {
                    smsLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS))
                }
            }
        ),
        SetupStep(
            icon = Icons.Outlined.BatteryChargingFull,
            title = stringResource(R.string.setup_battery_title),
            description = stringResource(
                if (batteryUnrestricted) R.string.setup_battery_done else R.string.setup_battery_todo
            ),
            done = batteryUnrestricted,
            required = false,
            actionLabel = stringResource(R.string.action_exempt),
            onAction = { requestBatteryExemption(context) }
        ),
        SetupStep(
            icon = Icons.Outlined.Notifications,
            title = stringResource(R.string.setup_notifications_title),
            description = stringResource(
                if (notificationsEnabled) R.string.setup_notifications_done else R.string.setup_notifications_todo
            ),
            done = notificationsEnabled,
            required = false,
            actionLabel = stringResource(R.string.action_allow),
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsDenied) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openNotificationSettings(context)
                }
            }
        ),
        SetupStep(
            icon = Icons.Outlined.Contacts,
            title = stringResource(R.string.setup_contacts_title),
            description = stringResource(
                if (contactsGranted) R.string.setup_contacts_done else R.string.setup_contacts_todo
            ),
            done = contactsGranted,
            required = false,
            actionLabel = stringResource(R.string.action_allow),
            onAction = {
                if (contactsDenied) {
                    openAppSettings(context)
                } else {
                    contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
            }
        ),
        SetupStep(
            icon = Icons.Outlined.Tune,
            title = stringResource(R.string.setup_rule_title),
            description = if (activeRules > 0) stringResource(R.string.setup_rule_done, activeRules, rules.size)
            else stringResource(R.string.setup_rule_todo),
            done = activeRules > 0,
            required = true,
            actionLabel = stringResource(R.string.tab_rules),
            onAction = onOpenRules
        )
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.tab_home),
                subtitle = history.firstOrNull()
                    ?.let { stringResource(R.string.home_last_forward, formatDateTime(context, it.receivedAt)) }
                    ?: stringResource(R.string.home_no_forward_yet),
                onOpenSettings = onOpenSettings
            )
        }

        item {
            ForwardingSwitchCard(
                enabled = forwardingEnabled,
                ready = smsGranted && activeRules > 0,
                onChange = onForwardingChange
            )
        }

        item {
            val failed = history.sumOf { entry -> entry.recipients.count(RecipientResult::failed) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = stringResource(R.string.stat_forwarded),
                    value = sentTotal.toString(),
                    onClick = { onOpenHistory(HistoryFilter.All) },
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = stringResource(R.string.stat_active_rules),
                    value = "$activeRules/${rules.size}",
                    onClick = onOpenRules,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = stringResource(R.string.stat_failed),
                    value = failed.toString(),
                    onClick = { onOpenHistory(HistoryFilter.Failed) },
                    modifier = Modifier.weight(1f),
                    valueColor = if (failed > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        item {
            SetupCard(steps, onOpenAppSettings = { openAppSettings(context) })
        }
    }
}

@Composable
private fun ForwardingSwitchCard(enabled: Boolean, ready: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val active = enabled && ready
    val containerColor = when {
        active -> colors.primary
        enabled -> colors.tertiaryContainer
        else -> colors.surface
    }
    val titleColor = when {
        active -> colors.onPrimary
        enabled -> colors.tertiary
        else -> colors.onSurface
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        shape = RoundedCornerShape(8.dp),
        border = if (enabled) null else cardBorder()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (enabled) R.string.forwarding_on else R.string.forwarding_paused),
                    color = titleColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(
                        when {
                            active -> R.string.forwarding_active_hint
                            enabled -> R.string.forwarding_setup_hint
                            else -> R.string.forwarding_paused_hint
                        }
                    ),
                    color = if (active) colors.onPrimary.copy(alpha = 0.85f) else colors.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            val switchLabel = stringResource(R.string.forwarding_switch_description)
            Switch(
                checked = enabled,
                onCheckedChange = onChange,
                // The card itself uses the primary color, so the thumb takes the contrasting one.
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onPrimary,
                    checkedTrackColor = colors.secondary,
                    checkedBorderColor = colors.secondary
                ),
                modifier = Modifier.semantics { contentDescription = switchLabel }
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = cardBorder()
    ) {
        Column(Modifier.padding(start = 12.dp, top = 12.dp, end = 6.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    value,
                    color = valueColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SetupCard(steps: List<SetupStep>, onOpenAppSettings: () -> Unit) {
    val allDone = steps.all { it.done }
    // Collapsed once everything is done; null means the user has not toggled it yet.
    var expandedChoice by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val expanded = expandedChoice ?: !allDone
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = cardBorder()
    ) {
        Column(Modifier.padding(top = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedChoice = !expanded }
                    .padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel(stringResource(R.string.setup_title), Modifier.weight(1f))
                if (allDone) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    stringResource(R.string.setup_progress, steps.count { it.done }, steps.size),
                    color = if (allDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            if (expanded) {
                steps.forEachIndexed { index, step ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }
                    SetupRow(step)
                }
            } else {
                Spacer(Modifier.height(6.dp))
            }
            // Granted permissions can only be revoked in the system settings.
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAppSettings)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.setup_app_settings_title),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        stringResource(R.string.setup_app_settings_text),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SetupRow(step: SetupStep) {
    val colors = MaterialTheme.colorScheme
    val accent = when {
        step.done -> colors.primary
        step.required -> colors.tertiary
        else -> colors.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(if (step.done) colors.primaryContainer else accent.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (step.done) Icons.Outlined.Check else step.icon,
                contentDescription = if (step.done) stringResource(R.string.setup_done) else null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (step.required || step.done) step.title
                else stringResource(R.string.setup_optional_title, step.title),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(step.description, color = colors.onSurfaceVariant, fontSize = 12.sp)
        }
        if (!step.done) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = step.onAction) { Text(step.actionLabel) }
        }
    }
}

internal fun hasSmsPermissions(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    )
}

private fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    )
}

// Asking directly is allowed for apps whose core function needs reliable background delivery;
// some devices do not offer the dialog, so fall back to the general settings list.
@SuppressLint("BatteryLife")
private fun requestBatteryExemption(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        )
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}
