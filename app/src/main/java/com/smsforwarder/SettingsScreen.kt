package com.smsforwarder

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var themeMode by remember { mutableStateOf(AppSettings.themeMode(context)) }
    var language by remember { mutableStateOf(AppSettings.language()) }
    val versionName = remember { appVersionName(context) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.settings_title), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }

            // First, because the rules screen links here for the daily limit.
            item { ForwardingSection() }

            item {
                OptionGroup(
                    label = stringResource(R.string.settings_appearance),
                    options = listOf(
                        ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                        ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                        ThemeMode.DARK to stringResource(R.string.settings_theme_dark)
                    ),
                    selected = themeMode,
                    onSelect = { mode ->
                        themeMode = mode
                        AppSettings.setThemeMode(context, mode)
                    }
                )
            }

            item {
                // Language names are shown in their own language so they can be found in either.
                OptionGroup(
                    label = stringResource(R.string.settings_language),
                    options = listOf(
                        AppLanguage.SYSTEM to stringResource(R.string.settings_language_system),
                        AppLanguage.ENGLISH to "English",
                        AppLanguage.GERMAN to "Deutsch"
                    ),
                    selected = language,
                    onSelect = { selectedLanguage ->
                        language = selectedLanguage
                        AppSettings.setLanguage(selectedLanguage)
                    }
                )
            }

            item { HelpSection() }

            item { FeedbackSection(versionName) }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painterResource(R.drawable.fowi_logo),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.app_name), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.settings_version, versionName),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun ForwardingSection() {
    val context = LocalContext.current
    var included by remember { mutableStateOf(AppSettings.isSenderIncluded(context)) }
    val limit = observePreferences(DailyLimit.PREFERENCES_NAME, DailyLimit::limit)
    val sentToday = observePreferences(DailyLimit.PREFERENCES_NAME, DailyLimit::sentToday)
    var editingLimit by rememberSaveable { mutableStateOf(false) }

    Column {
        SectionLabel(stringResource(R.string.settings_forwarding), Modifier.padding(start = 4.dp, bottom = 8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            border = cardBorder()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = included, role = Role.Switch) {
                            included = it
                            AppSettings.setSenderIncluded(context, it)
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_include_sender), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(R.string.settings_include_sender_text),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Switch(checked = included, onCheckedChange = null)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingLimit = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_daily_limit), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            dailyLimitSummary(limit, sentToday),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_edit), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    if (editingLimit) {
        DailyLimitDialog(
            initialLimit = limit,
            onDismiss = { editingLimit = false },
            onSave = {
                DailyLimit.setLimit(context, it)
                editingLimit = false
            }
        )
    }
}

/** "Up to 100 SMS per day · 12 sent today", shown in the settings and on the rules screen. */
@Composable
internal fun dailyLimitSummary(limit: Int, sentToday: Int): String =
    if (limit > 0) stringResource(R.string.daily_limit_summary, limit, sentToday)
    else stringResource(R.string.daily_limit_summary_off, sentToday)

@Composable
private fun DailyLimitDialog(initialLimit: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var input by rememberSaveable { mutableStateOf(initialLimit.toString()) }
    val value = input.trim().toIntOrNull()?.takeIf { it in 0..DailyLimit.MAX_LIMIT }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_daily_limit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.daily_limit_dialog_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.daily_limit_dialog_label)) },
                    supportingText = { Text(stringResource(R.string.daily_limit_dialog_hint)) },
                    isError = value == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onSave) }, enabled = value != null) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

private val helpTopics = listOf(
    R.string.help_how_title to R.string.help_how_text,
    R.string.help_status_title to R.string.help_status_text,
    R.string.help_rules_title to R.string.help_rules_text,
    R.string.help_costs_title to R.string.help_costs_text,
    R.string.help_privacy_title to R.string.help_privacy_text,
    R.string.help_troubleshooting_title to R.string.help_troubleshooting_text
)

@Composable
private fun HelpSection() {
    var expandedTopic by rememberSaveable { mutableStateOf<Int?>(null) }
    Column {
        SectionLabel(stringResource(R.string.settings_help), Modifier.padding(start = 4.dp, bottom = 8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            border = cardBorder()
        ) {
            Column {
                helpTopics.forEachIndexed { index, (title, text) ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val expanded = expandedTopic == title
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { expandedTopic = if (expanded) null else title }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(title),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = stringResource(
                                    if (expanded) R.string.action_collapse else R.string.action_expand
                                ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (expanded) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(text),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackSection(versionName: String) {
    val context = LocalContext.current
    Column {
        SectionLabel(stringResource(R.string.settings_feedback), Modifier.padding(start = 4.dp, bottom = 8.dp))
        Surface(
            onClick = { sendFeedback(context, versionName) },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            border = cardBorder()
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.feedback_title), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(R.string.feedback_text, FEEDBACK_EMAIL),
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

private const val FEEDBACK_EMAIL = "info@om21.at"

// Opens the user's mail app with a prefilled draft; nothing is sent without the user's action.
private fun sendFeedback(context: Context, versionName: String) {
    val details = "\n\n---\n${context.getString(R.string.app_name)} $versionName\n" +
        "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n" +
        "${Build.MANUFACTURER} ${Build.MODEL}"
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.feedback_subject, versionName))
        .putExtra(Intent.EXTRA_TEXT, details)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.feedback_no_mail_app, FEEDBACK_EMAIL), Toast.LENGTH_LONG).show()
    }
}

private fun appVersionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

@Composable
private fun <T> OptionGroup(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Column {
        SectionLabel(label, Modifier.padding(start = 4.dp, bottom = 8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            border = cardBorder()
        ) {
            Column(Modifier.selectableGroup()) {
                options.forEachIndexed { index, (value, text) ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = value == selected, role = Role.RadioButton) { onSelect(value) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = value == selected, onClick = null, modifier = Modifier.padding(8.dp))
                        Text(text, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
