package com.smsforwarder

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

@Composable
internal fun RulesScreen(
    rules: List<ForwardRule>,
    onRulesChange: (List<ForwardRule>) -> Unit,
    onOpenSettings: () -> Unit,
    /** Opens the full-screen rule editor; null creates a new rule. */
    onEditRule: (ForwardRule?) -> Unit,
    modifier: Modifier = Modifier
) {
    var deletingRule by remember { mutableStateOf<ForwardRule?>(null) }
    var showTest by rememberSaveable { mutableStateOf(false) }
    val contactNames = rememberContactNames(rules.flatMap(ForwardRule::recipients))
    val simCards = rememberSimCards()
    val dailyLimit = observePreferences(DailyLimit.PREFERENCES_NAME, DailyLimit::limit)
    val sentToday = observePreferences(DailyLimit.PREFERENCES_NAME, DailyLimit::sentToday)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.rules_title),
                subtitle = stringResource(R.string.rules_subtitle, rules.count { it.enabled }, rules.size),
                onOpenSettings = onOpenSettings
            )
        }

        item { CostNotice(dailyLimit, sentToday, onOpenSettings) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel(stringResource(R.string.rules_section), Modifier.weight(1f))
                if (rules.isNotEmpty()) {
                    TextButton(onClick = { showTest = true }) {
                        Icon(Icons.Outlined.Science, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.rules_test))
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Button(
                    onClick = { onEditRule(null) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.rules_add))
                }
            }
        }

        if (rules.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    border = cardBorder()
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(stringResource(R.string.rules_empty_title), fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.rules_empty_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(rules, key = ForwardRule::id) { rule ->
                RuleCard(
                    rule = rule,
                    contactNames = contactNames,
                    simCards = simCards,
                    onToggle = { enabled ->
                        onRulesChange(rules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it })
                    },
                    onEdit = { onEditRule(rule) },
                    onDelete = { deletingRule = rule }
                )
            }
        }

        item {
            Text(
                stringResource(R.string.rules_privacy_note),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
        }
    }

    if (showTest) {
        RuleTestDialog(rules = rules, simCards = simCards, onDismiss = { showTest = false })
    }

    deletingRule?.let { rule ->
        AlertDialog(
            onDismissRequest = { deletingRule = null },
            title = { Text(stringResource(R.string.rule_delete_title)) },
            text = { Text(stringResource(R.string.rule_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    onRulesChange(rules.filterNot { it.id == rule.id })
                    deletingRule = null
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.tertiary) }
            },
            dismissButton = {
                TextButton(onClick = { deletingRule = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

/** Cost warning with the daily limit; tapping it opens the settings, where the limit is changed. */
@Composable
private fun CostNotice(dailyLimit: Int, sentToday: Int, onOpenSettings: () -> Unit) {
    val contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    Surface(
        onClick = onOpenSettings,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Outlined.Paid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.cost_notice_title),
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.cost_notice_text),
                    color = contentColor,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.daily_limit_link, dailyLimitSummary(dailyLimit, sentToday)),
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleCard(
    rule: ForwardRule,
    contactNames: Map<String, String>,
    simCards: List<SimCard>,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var expanded by rememberSaveable(rule.id) { mutableStateOf(false) }
    val filters = filterSummary(rule.senderFilter, rule.messageFilter)
    val title = rule.name.ifBlank { filters }
    val switchLabel = stringResource(R.string.rule_enable_description, title)

    Surface(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(8.dp),
        border = cardBorder()
    ) {
        Column(Modifier.padding(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (rule.name.isNotBlank()) {
                        Text(
                            filters,
                            color = colors.onSurfaceVariant,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val extras = extraConditionsSummary(rule)
                    if (extras.isNotEmpty()) {
                        Text(
                            extras,
                            color = colors.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val recipientCount =
                        pluralStringResource(R.plurals.recipient_count, rule.recipients.size, rule.recipients.size)
                    // The SIM only matters on phones with several, or when one was chosen earlier.
                    val showSim = rule.subscriptionId != null || simCards.size > 1
                    Text(
                        if (showSim) "$recipientCount  ·  ${simLabel(rule.subscriptionId, simCards)}" else recipientCount,
                        color = if (rule.enabled) colors.primary else colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.semantics { contentDescription = switchLabel }
                )
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            if (expanded) {
                Column(Modifier.padding(end = 8.dp)) {
                    HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(vertical = 12.dp))
                    SectionLabel(stringResource(R.string.editor_recipients_label))
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        rule.recipients.forEach { number ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (number in contactNames) Icons.Outlined.Person else Icons.Outlined.Phone,
                                    contentDescription = null,
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                ContactLabel(number = number, name = contactNames[number])
                            }
                        }
                    }
                    HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDelete) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_delete))
                        }
                        TextButton(onClick = onEdit) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_edit))
                        }
                    }
                }
            }
        }
    }
}

/** Names a rule from the history: its own name, or else a description of its filters. */
@Composable
internal fun ruleSummary(rule: MatchedRule): String {
    if (rule.name.isNotBlank()) return rule.name
    rule.legacySummary?.let { return it }
    return filterSummary(rule.senderFilter, rule.messageFilter)
}

/** Describes a rule's filters in the current language. */
@Composable
internal fun filterSummary(senderFilter: String, messageFilter: String): String {
    val parts = listOfNotNull(
        senderFilter.takeIf(String::isNotBlank)?.let { stringResource(R.string.rule_summary_sender, it) },
        messageFilter.takeIf(String::isNotBlank)?.let { stringResource(R.string.rule_summary_text, it) }
    )
    return if (parts.isEmpty()) stringResource(R.string.rule_summary_any) else parts.joinToString(" + ")
}

/** The conditions beyond sender and text: exclusion, schedule and regex mode; empty if there are none. */
@Composable
internal fun extraConditionsSummary(rule: ForwardRule): String =
    listOfNotNull(
        rule.excludeFilter.takeIf(String::isNotBlank)?.let { stringResource(R.string.rule_summary_exclude, it) },
        rule.schedule.takeUnless { it.isAlways }?.let { scheduleSummary(it) },
        stringResource(R.string.rule_summary_regex).takeIf { rule.useRegex }
    ).joinToString("  ·  ")

/** "Always", or the days and times, e.g. "Mon–Fri  ·  08:00–18:00". */
@Composable
internal fun scheduleSummary(schedule: RuleSchedule): String {
    if (schedule.isAlways) return stringResource(R.string.schedule_always)
    val days = when (schedule.days) {
        DayOfWeek.entries.toSet() -> stringResource(R.string.schedule_daily)
        WORKDAYS -> stringResource(R.string.schedule_workdays)
        WEEKEND -> stringResource(R.string.schedule_weekend)
        else -> DayOfWeek.entries.filter { it in schedule.days }.map { shortDayName(it) }.joinToString(", ")
    }
    val start = schedule.startMinute
    val end = schedule.endMinute
    if (start == null || end == null) return days
    val context = LocalContext.current
    return "$days  ·  ${formatMinuteOfDay(context, start)}–${formatMinuteOfDay(context, end)}"
}

internal val WORKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
internal val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/** "Mo", "Di" or "Mon", "Tue" in the app language. */
@Composable
internal fun shortDayName(day: DayOfWeek): String =
    day.getDisplayName(TextStyle.SHORT, LocalConfiguration.current.locales[0]).removeSuffix(".")

/** Formats minutes after midnight in the phone's 12 or 24 hour style. */
internal fun formatMinuteOfDay(context: Context, minute: Int): String =
    DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a")
        .format(LocalTime.of(minute / 60, minute % 60))

