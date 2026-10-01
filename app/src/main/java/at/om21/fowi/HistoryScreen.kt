package at.om21.fowi

import android.content.Context
import android.text.format.DateUtils
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

enum class HistoryFilter { All, Failed }

private class ResendRequest(val entry: ForwardLogEntry, val recipientIndices: List<Int>)

@Composable
internal fun HistoryScreen(
    filter: HistoryFilter,
    onFilterChange: (HistoryFilter) -> Unit,
    snackbarHostState: SnackbarHostState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries = observePreferences(ForwardLog.PREFERENCES_NAME, ForwardLog::load)
    val failedEntries = entries.filter { entry -> entry.recipients.any(RecipientResult::failed) }
    val shownEntries = if (filter == HistoryFilter.Failed) failedEntries else entries
    val contactNames = rememberContactNames(
        entries.flatMap { entry -> entry.recipients.map(RecipientResult::number) + entry.sender }
    )
    var confirmClear by remember { mutableStateOf(false) }
    var resendRequest by remember { mutableStateOf<ResendRequest?>(null) }

    fun delete(entry: ForwardLogEntry) {
        val index = ForwardLog.delete(context, entry.id)
        if (index < 0) return
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.history_deleted),
                actionLabel = context.getString(R.string.action_undo),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) ForwardLog.restore(context, entry, index)
        }
    }

    fun requestResend(entry: ForwardLogEntry, recipientIndices: List<Int>) {
        if (hasSmsPermissions(context)) {
            resendRequest = ResendRequest(entry, recipientIndices)
        } else {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.history_resend_needs_sms)) }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.history_title),
                subtitle = pluralStringResource(R.plurals.history_count, entries.size, entries.size),
                onOpenSettings = onOpenSettings
            )
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = filter == HistoryFilter.All,
                    onClick = { onFilterChange(HistoryFilter.All) },
                    label = { Text(stringResource(R.string.history_filter_all, entries.size)) }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = filter == HistoryFilter.Failed,
                    onClick = { onFilterChange(HistoryFilter.Failed) },
                    label = { Text(stringResource(R.string.history_filter_failed, failedEntries.size)) }
                )
                Spacer(Modifier.weight(1f))
                if (entries.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.history_clear)) }
                }
            }
        }

        if (shownEntries.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    border = cardBorder()
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            stringResource(
                                if (filter == HistoryFilter.Failed) R.string.history_empty_failed_title
                                else R.string.history_empty_title
                            ),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(
                                if (filter == HistoryFilter.Failed) R.string.history_empty_failed_text
                                else R.string.history_empty_text
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(shownEntries, key = ForwardLogEntry::id) { entry ->
                HistoryCard(
                    entry = entry,
                    contactNames = contactNames,
                    onResend = { indices -> requestResend(entry, indices) },
                    onDelete = { delete(entry) }
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_text)) },
            confirmButton = {
                TextButton(onClick = {
                    ForwardLog.clear(context)
                    confirmClear = false
                }) { Text(stringResource(R.string.history_clear), color = MaterialTheme.colorScheme.tertiary) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    resendRequest?.let { request ->
        val count = request.recipientIndices.size
        AlertDialog(
            onDismissRequest = { resendRequest = null },
            title = { Text(stringResource(R.string.history_resend_title)) },
            text = {
                Text(
                    if (count == 1) {
                        val number = request.entry.recipients[request.recipientIndices.first()].number
                        stringResource(R.string.history_resend_text_single, contactNames[number] ?: number)
                    } else {
                        pluralStringResource(R.plurals.history_resend_text, count, count)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    request.recipientIndices.forEach { SmsSender.resend(context, request.entry, it) }
                    resendRequest = null
                }) { Text(stringResource(R.string.history_resend)) }
            },
            dismissButton = {
                TextButton(onClick = { resendRequest = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

private enum class EntryState { Sending, Sent, Delivered, Failed, Unconfirmed }

private fun entryState(entry: ForwardLogEntry, now: Long): EntryState {
    val recipients = entry.recipients
    return when {
        recipients.any(RecipientResult::failed) -> EntryState.Failed
        recipients.any { it.deliveryReportOverdue(now) } -> EntryState.Unconfirmed
        recipients.any { it.sendStatus == SendStatus.PENDING } -> EntryState.Sending
        recipients.all { it.deliveryStatus == DeliveryStatus.DELIVERED } -> EntryState.Delivered
        else -> EntryState.Sent
    }
}

private class StatusDisplay(val icon: ImageVector, val color: Color, val text: String)

@Composable
private fun entryStatusDisplay(state: EntryState): StatusDisplay {
    val colors = MaterialTheme.colorScheme
    return when (state) {
        EntryState.Sending -> StatusDisplay(Icons.Outlined.Schedule, colors.onSurfaceVariant, stringResource(R.string.status_sending))
        EntryState.Sent -> StatusDisplay(Icons.Outlined.Done, colors.onSurfaceVariant, stringResource(R.string.status_sent_waiting))
        EntryState.Delivered -> StatusDisplay(Icons.Outlined.DoneAll, colors.primary, stringResource(R.string.status_delivered))
        EntryState.Failed -> StatusDisplay(Icons.Outlined.ErrorOutline, colors.tertiary, stringResource(R.string.status_failed))
        EntryState.Unconfirmed -> StatusDisplay(Icons.Outlined.HelpOutline, colors.tertiary, stringResource(R.string.status_unconfirmed))
    }
}

@Composable
private fun HistoryCard(
    entry: ForwardLogEntry,
    contactNames: Map<String, String>,
    onResend: (List<Int>) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    val status = entryStatusDisplay(entryState(entry, System.currentTimeMillis()))
    val ruleNames = entry.rules.map { ruleSummary(it) }.joinToString(", ")
    val senderName = contactNames[entry.sender] ?: entry.sender

    Surface(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(8.dp),
        border = cardBorder()
    ) {
        Column(Modifier.padding(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).background(status.color.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(status.icon, contentDescription = null, tint = status.color, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    // The rule is the title, since several rules can forward from the same sender.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            ruleNames,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(formatDateTime(context, entry.receivedAt), color = colors.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Text(
                        if (expanded) senderName else "$senderName: ${entry.body}",
                        color = colors.onSurfaceVariant,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${status.text}  ·  " +
                            pluralStringResource(R.plurals.recipient_count, entry.recipients.size, entry.recipients.size),
                        color = status.color,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            if (expanded) {
                Column(Modifier.padding(end = 8.dp)) {
                    Spacer(Modifier.height(12.dp))
                    Text(entry.body, fontSize = 14.sp)
                    contactNames[entry.sender]?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.history_sender_number, entry.sender),
                            color = colors.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(vertical = 10.dp))
                    entry.recipients.forEachIndexed { index, recipient ->
                        // With several matching rules, show which ones reached this recipient.
                        val via = if (entry.rules.size > 1) {
                            entry.rules.filter { recipient.number in it.recipients }.map { ruleSummary(it) }
                        } else {
                            emptyList()
                        }
                        RecipientRow(
                            recipient = recipient,
                            name = contactNames[recipient.number],
                            viaRules = via,
                            onResend = { onResend(listOf(index)) }
                        )
                    }
                    HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(vertical = 6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDelete) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_delete))
                        }
                        TextButton(onClick = { onResend(entry.recipients.indices.toList()) }) {
                            Icon(Icons.Outlined.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.history_resend_all))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipientRow(
    recipient: RecipientResult,
    name: String?,
    viaRules: List<String>,
    onResend: () -> Unit
) {
    val status = recipientStatus(recipient)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(status.icon, contentDescription = null, tint = status.color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            ContactLabel(number = recipient.number, name = name)
            Text(status.text, color = status.color, fontSize = 12.sp)
            if (viaRules.isNotEmpty()) {
                Text(
                    stringResource(R.string.history_via_rules, viaRules.joinToString(", ")),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp
                )
            }
            if (recipient.attempt > 1) {
                Text(
                    stringResource(R.string.status_attempt, recipient.attempt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
        // Resending while the current attempt is still in progress would only race with it.
        if (recipient.sendStatus != SendStatus.PENDING) {
            IconButton(onClick = onResend) {
                Icon(
                    Icons.Outlined.Replay,
                    contentDescription = stringResource(R.string.history_resend_to, name ?: recipient.number),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun recipientStatus(recipient: RecipientResult): StatusDisplay {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val sent = recipient.sentAt?.let { stringResource(R.string.status_sent_at, formatTime(context, it)) }
        ?: stringResource(R.string.status_sent)
    return when {
        recipient.sendStatus == SendStatus.PENDING ->
            StatusDisplay(Icons.Outlined.Schedule, colors.onSurfaceVariant, stringResource(R.string.status_sending))
        recipient.sendStatus == SendStatus.FAILED ->
            StatusDisplay(
                Icons.Outlined.ErrorOutline,
                colors.tertiary,
                stringResource(R.string.status_not_sent, describeSendError(context, recipient.error)) +
                    (recipient.sentAt?.let { "  ·  ${formatTime(context, it)}" } ?: "")
            )
        recipient.deliveryStatus == DeliveryStatus.DELIVERED ->
            StatusDisplay(
                Icons.Outlined.DoneAll,
                colors.primary,
                recipient.deliveredAt
                    ?.let { "$sent  ·  ${stringResource(R.string.status_delivered_at, formatTime(context, it))}" }
                    ?: "$sent  ·  ${stringResource(R.string.status_delivered)}"
            )
        recipient.deliveryStatus == DeliveryStatus.FAILED ->
            StatusDisplay(Icons.Outlined.ErrorOutline, colors.tertiary, "$sent  ·  ${stringResource(R.string.status_not_delivered)}")
        recipient.deliveryReportOverdue(System.currentTimeMillis()) ->
            StatusDisplay(Icons.Outlined.HelpOutline, colors.tertiary, "$sent  ·  ${stringResource(R.string.status_no_report_24h)}")
        else ->
            StatusDisplay(Icons.Outlined.Done, colors.onSurfaceVariant, "$sent  ·  ${stringResource(R.string.status_waiting_report)}")
    }
}

// Formats with the app's configured language rather than the device default.
internal fun formatDateTime(context: Context, millis: Long): String {
    val locale = context.resources.configuration.locales[0]
    return if (DateUtils.isToday(millis)) {
        context.getString(R.string.today_at, formatTime(context, millis))
    } else {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(Date(millis))
    }
}

private fun formatTime(context: Context, millis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT, context.resources.configuration.locales[0]).format(Date(millis))
