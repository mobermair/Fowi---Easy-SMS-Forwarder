package com.smsforwarder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime

/**
 * Simulates an incoming SMS: shows which rules match, who would receive it, from which SIM and
 * with which text. Uses the same matching as [SmsReceiver] and sends nothing.
 */
@Composable
internal fun RuleTestDialog(rules: List<ForwardRule>, simCards: List<SimCard>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var sender by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    val forwardingEnabled = remember { AppSettings.isForwardingEnabled(context) }
    val dailyLimit = remember { DailyLimit.limit(context) }
    val sentToday = remember { DailyLimit.sentToday(context) }

    // The test uses the current time, like a message arriving right now.
    val now = remember { LocalDateTime.now() }
    val matching = rules.filter { it.matches(sender.trim(), message) }
    val active = matching.filter { it.enabled && it.isActiveAt(now) }
    val targets = recipientTargets(active)
    val contactNames = rememberContactNames(targets.map(RecipientTarget::number))
    val preview = remember(sender, message) { SmsSender.messageFor(context, sender.trim(), message) }
    val parts = remember(preview) { SmsSender.partCount(context, preview) }
    val totalSms = parts * targets.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.test_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.test_hint), color = colors.onSurfaceVariant)
                OutlinedTextField(
                    value = sender,
                    onValueChange = { sender = it },
                    label = { Text(stringResource(R.string.test_sender_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text(stringResource(R.string.test_message_label)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))

                when {
                    message.isBlank() -> ResultText(stringResource(R.string.test_enter_message))
                    SmsSender.isForwarded(message) -> ResultText(stringResource(R.string.test_result_loop), warning = true)
                    matching.isEmpty() -> ResultText(stringResource(R.string.test_result_no_match), warning = true)
                    else -> {
                        SectionLabel(stringResource(R.string.test_matching_rules))
                        matching.forEach { rule ->
                            val title = rule.name.ifBlank { filterSummary(rule.senderFilter, rule.messageFilter) }
                            val isActive = rule in active
                            Text(
                                when {
                                    !rule.enabled -> stringResource(R.string.test_rule_disabled, title)
                                    !isActive -> stringResource(R.string.test_rule_outside_schedule, title, scheduleSummary(rule.schedule))
                                    else -> title
                                },
                                color = if (isActive) colors.onSurface else colors.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (active.isEmpty()) {
                            ResultText(stringResource(R.string.test_result_only_disabled), warning = true)
                        } else {
                            Spacer(Modifier.size(2.dp))
                            SectionLabel(stringResource(R.string.editor_recipients_label))
                            val showSim = simCards.size > 1 || targets.any { it.subscriptionId != null }
                            targets.forEach { target ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (target.number in contactNames) Icons.Outlined.Person else Icons.Outlined.Phone,
                                        contentDescription = null,
                                        tint = colors.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    ContactLabel(
                                        number = target.number,
                                        name = contactNames[target.number],
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (showSim) {
                                        Text(
                                            simLabel(target.subscriptionId, simCards),
                                            color = colors.onSurfaceVariant,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.size(2.dp))
                            SectionLabel(stringResource(R.string.test_preview_label))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = colors.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(preview, fontSize = 14.sp, modifier = Modifier.padding(12.dp))
                            }
                            ResultText(stringResource(R.string.test_sms_count, parts, totalSms))
                            if (dailyLimit > 0 && sentToday + totalSms > dailyLimit) {
                                ResultText(stringResource(R.string.test_over_limit, dailyLimit, sentToday), warning = true)
                            }
                        }
                    }
                }
                if (!forwardingEnabled) {
                    ResultText(stringResource(R.string.test_paused), warning = true)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
    )
}

@Composable
private fun ResultText(text: String, warning: Boolean = false) {
    Text(
        text,
        color = if (warning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp
    )
}
