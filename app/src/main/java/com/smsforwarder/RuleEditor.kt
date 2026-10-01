package com.smsforwarder

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek

private enum class EditorStep(@StringRes val label: Int) {
    Message(R.string.editor_step_message),
    Schedule(R.string.editor_step_schedule),
    Recipients(R.string.editor_step_recipients),
    Send(R.string.editor_step_send)
}

private const val DEFAULT_START_MINUTE = 8 * 60
private const val DEFAULT_END_MINUTE = 18 * 60

/**
 * Full-screen editor that creates or changes a rule in four steps. New rules are walked through
 * step by step; when editing, every step can be opened directly and the rule saved at any time.
 */
@Composable
internal fun RuleEditorScreen(
    initialRule: ForwardRule?,
    onDismiss: () -> Unit,
    onSave: (ForwardRule) -> Unit
) {
    val context = LocalContext.current
    val initialSchedule = initialRule?.schedule ?: RuleSchedule.ALWAYS

    var step by remember { mutableStateOf(EditorStep.Message) }
    // New rules unlock the steps one by one; existing rules can jump to any step.
    var furthestStep by remember { mutableStateOf(if (initialRule == null) EditorStep.Message else EditorStep.Send) }

    var name by remember { mutableStateOf(initialRule?.name.orEmpty()) }
    var sender by remember { mutableStateOf(initialRule?.senderFilter.orEmpty()) }
    var message by remember { mutableStateOf(initialRule?.messageFilter.orEmpty()) }
    var exclude by remember { mutableStateOf(initialRule?.excludeFilter.orEmpty()) }
    var useRegex by remember { mutableStateOf(initialRule?.useRegex ?: false) }

    var limitedSchedule by remember { mutableStateOf(!initialSchedule.isAlways) }
    var days by remember { mutableStateOf(initialSchedule.days) }
    var limitedTime by remember { mutableStateOf(initialSchedule.startMinute != null) }
    var startMinute by remember { mutableIntStateOf(initialSchedule.startMinute ?: DEFAULT_START_MINUTE) }
    var endMinute by remember { mutableIntStateOf(initialSchedule.endMinute ?: DEFAULT_END_MINUTE) }

    var recipients by remember { mutableStateOf(initialRule?.recipients.orEmpty()) }
    var numberInput by remember { mutableStateOf("") }
    var numberError by remember { mutableStateOf<String?>(null) }
    var subscriptionId by remember { mutableStateOf(initialRule?.subscriptionId) }

    val invalidNumberError = stringResource(R.string.editor_number_invalid)

    // Accepts several numbers at once, e.g. when a list is pasted.
    fun addTypedNumbers(): Boolean {
        val entries = numberInput.split(Regex("[,;\\n]")).map(String::trim).filter(String::isNotEmpty)
        if (entries.isEmpty()) return true
        val normalized = entries.map(::normalizePhoneNumber)
        if (normalized.any { it == null }) {
            numberError = invalidNumberError
            return false
        }
        recipients = (recipients + normalized.filterNotNull()).distinct()
        numberInput = ""
        numberError = null
        return true
    }

    val filtersValid = (sender.isNotBlank() || message.isNotBlank()) &&
        (!useRegex || listOf(sender, message, exclude).all(::isValidRegex))
    val scheduleValid = !limitedSchedule || (days.isNotEmpty() && (!limitedTime || startMinute != endMinute))
    val recipientsValid = recipients.isNotEmpty() || numberInput.isNotBlank()
    fun stepValid(editorStep: EditorStep): Boolean = when (editorStep) {
        EditorStep.Message -> filtersValid
        EditorStep.Schedule -> scheduleValid
        EditorStep.Recipients -> recipientsValid
        EditorStep.Send -> true
    }
    val canSave = EditorStep.entries.all(::stepValid)

    fun buildRule() = ForwardRule(
        id = initialRule?.id ?: ForwardRule().id,
        name = name.trim(),
        senderFilter = sender.trim(),
        messageFilter = message.trim(),
        excludeFilter = exclude.trim(),
        useRegex = useRegex,
        schedule = if (limitedSchedule) {
            RuleSchedule(days, startMinute.takeIf { limitedTime }, endMinute.takeIf { limitedTime })
        } else {
            RuleSchedule.ALWAYS
        },
        recipients = recipients,
        enabled = initialRule?.enabled ?: true,
        subscriptionId = subscriptionId
    )

    // A typed but not yet added number is saved too, as long as it is valid.
    fun save() {
        if (addTypedNumbers() && recipients.isNotEmpty()) onSave(buildRule())
    }

    fun goTo(target: EditorStep) {
        // Leaving the recipients step adds a typed number, or stays to show why it is invalid.
        if (step == EditorStep.Recipients && target.ordinal > step.ordinal && !addTypedNumbers()) return
        step = target
        if (target.ordinal > furthestStep.ordinal) furthestStep = target
    }

    // The back button returns to the previous step before it closes the editor.
    BackHandler {
        if (step.ordinal > 0) step = EditorStep.entries[step.ordinal - 1] else onDismiss()
    }

    // The activity draws edge to edge, so the content keeps clear of the status bar, the
    // navigation bar and the keyboard itself; that way the bottom buttons are never covered.
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_cancel))
                }
                Text(
                    stringResource(if (initialRule == null) R.string.editor_new_title else R.string.editor_edit_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                // Editing usually changes one detail, so saving does not require going through all steps.
                if (initialRule != null) {
                    TextButton(onClick = ::save, enabled = canSave) { Text(stringResource(R.string.action_save)) }
                }
            }

            StepIndicator(
                current = step,
                isReachable = { it.ordinal <= furthestStep.ordinal },
                isComplete = { it.ordinal < furthestStep.ordinal && stepValid(it) },
                onSelect = ::goTo
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (step) {
                    EditorStep.Message -> MessageStep(
                        sender = sender,
                        onSenderChange = { sender = it },
                        message = message,
                        onMessageChange = { message = it },
                        exclude = exclude,
                        onExcludeChange = { exclude = it },
                        useRegex = useRegex,
                        onUseRegexChange = { useRegex = it }
                    )
                    EditorStep.Schedule -> ScheduleStep(
                        limited = limitedSchedule,
                        onLimitedChange = { limitedSchedule = it },
                        days = days,
                        onDaysChange = { days = it },
                        limitedTime = limitedTime,
                        onLimitedTimeChange = { limitedTime = it },
                        startMinute = startMinute,
                        onStartChange = { startMinute = it },
                        endMinute = endMinute,
                        onEndChange = { endMinute = it }
                    )
                    EditorStep.Recipients -> RecipientsStep(
                        recipients = recipients,
                        onRemove = { number -> recipients = recipients - number },
                        onAdd = { number -> recipients = (recipients + number).distinct() },
                        numberInput = numberInput,
                        onNumberInputChange = {
                            numberInput = it
                            numberError = null
                        },
                        numberError = numberError,
                        onNumberError = { numberError = it },
                        onAddTyped = { addTypedNumbers() }
                    )
                    EditorStep.Send -> SendStep(
                        name = name,
                        onNameChange = { name = it },
                        subscriptionId = subscriptionId,
                        onSubscriptionChange = { subscriptionId = it },
                        rule = buildRule(),
                        showSimPicker = SimCards.slotCount(context) > 1 || subscriptionId != null
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step.ordinal > 0) {
                    OutlinedButton(onClick = { step = EditorStep.entries[step.ordinal - 1] }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_back))
                    }
                }
                Spacer(Modifier.weight(1f))
                if (step == EditorStep.Send) {
                    Button(onClick = ::save, enabled = canSave) {
                        Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.editor_save))
                    }
                } else {
                    Button(onClick = { goTo(EditorStep.entries[step.ordinal + 1]) }, enabled = stepValid(step)) {
                        Text(stringResource(R.string.action_next))
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(
    current: EditorStep,
    isReachable: (EditorStep) -> Boolean,
    isComplete: (EditorStep) -> Boolean,
    onSelect: (EditorStep) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        EditorStep.entries.forEach { editorStep ->
            val selected = editorStep == current
            val reachable = isReachable(editorStep)
            val complete = isComplete(editorStep) && !selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = reachable && !selected) { onSelect(editorStep) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = when {
                        selected -> colors.primary
                        complete -> colors.primaryContainer
                        else -> colors.surfaceVariant
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (complete) {
                            Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                        } else {
                            Text(
                                (editorStep.ordinal + 1).toString(),
                                color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(editorStep.label),
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        selected -> colors.primary
                        reachable -> colors.onSurface
                        else -> colors.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun StepIntro(@StringRes title: Int, @StringRes text: Int) {
    Column {
        Text(stringResource(title), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(text), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

@Composable
private fun MessageStep(
    sender: String,
    onSenderChange: (String) -> Unit,
    message: String,
    onMessageChange: (String) -> Unit,
    exclude: String,
    onExcludeChange: (String) -> Unit,
    useRegex: Boolean,
    onUseRegexChange: (Boolean) -> Unit
) {
    StepIntro(R.string.editor_message_title, if (useRegex) R.string.editor_message_text_regex else R.string.editor_message_text)

    val regexError: @Composable () -> Unit = { Text(stringResource(R.string.editor_regex_invalid)) }

    @Composable
    fun FilterField(value: String, onChange: (String) -> Unit, @StringRes label: Int) {
        val invalid = useRegex && !isValidRegex(value)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(stringResource(label)) },
            singleLine = true,
            isError = invalid,
            supportingText = regexError.takeIf { invalid },
            modifier = Modifier.fillMaxWidth()
        )
    }

    FilterField(sender, onSenderChange, R.string.editor_sender_label)
    FilterField(message, onMessageChange, R.string.editor_message_label)
    if (sender.isBlank() && message.isBlank()) {
        Text(stringResource(R.string.editor_filter_missing), color = MaterialTheme.colorScheme.tertiary, fontSize = 12.sp)
    }
    FilterField(exclude, onExcludeChange, R.string.editor_exclude_label)

    SwitchRow(
        checked = useRegex,
        onCheckedChange = onUseRegexChange,
        title = stringResource(R.string.editor_regex_title),
        text = stringResource(R.string.editor_regex_text)
    )
}

@Composable
private fun ScheduleStep(
    limited: Boolean,
    onLimitedChange: (Boolean) -> Unit,
    days: Set<DayOfWeek>,
    onDaysChange: (Set<DayOfWeek>) -> Unit,
    limitedTime: Boolean,
    onLimitedTimeChange: (Boolean) -> Unit,
    startMinute: Int,
    onStartChange: (Int) -> Unit,
    endMinute: Int,
    onEndChange: (Int) -> Unit
) {
    val context = LocalContext.current
    var pickingStart by remember { mutableStateOf<Boolean?>(null) }

    StepIntro(R.string.editor_schedule_title, R.string.editor_schedule_text)

    Column(Modifier.selectableGroup()) {
        listOf(false to R.string.schedule_always, true to R.string.editor_schedule_limited).forEach { (value, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = limited == value, role = Role.RadioButton) { onLimitedChange(value) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = limited == value, onClick = null, modifier = Modifier.padding(8.dp))
                Text(stringResource(label), fontSize = 16.sp)
            }
        }
    }

    if (!limited) return

    SectionLabel(stringResource(R.string.editor_schedule_days))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { day ->
            val selected = day in days
            Surface(
                onClick = { onDaysChange(if (selected) days - day else days + day) },
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                border = if (selected) null else cardBorder(),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        shortDayName(day),
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { onDaysChange(WORKDAYS) }) { Text(stringResource(R.string.schedule_workdays)) }
        TextButton(onClick = { onDaysChange(WEEKEND) }) { Text(stringResource(R.string.schedule_weekend)) }
        TextButton(onClick = { onDaysChange(DayOfWeek.entries.toSet()) }) { Text(stringResource(R.string.schedule_daily)) }
    }
    if (days.isEmpty()) {
        Text(stringResource(R.string.editor_schedule_no_days), color = MaterialTheme.colorScheme.tertiary, fontSize = 12.sp)
    }

    SwitchRow(
        checked = limitedTime,
        onCheckedChange = onLimitedTimeChange,
        title = stringResource(R.string.editor_schedule_time_title),
        text = stringResource(R.string.editor_schedule_time_text)
    )
    if (limitedTime) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { pickingStart = true }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.editor_schedule_from, formatMinuteOfDay(context, startMinute)))
            }
            OutlinedButton(onClick = { pickingStart = false }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.editor_schedule_to, formatMinuteOfDay(context, endMinute)))
            }
        }
        when {
            startMinute == endMinute -> Text(
                stringResource(R.string.editor_schedule_same_time),
                color = MaterialTheme.colorScheme.tertiary,
                fontSize = 12.sp
            )
            endMinute < startMinute -> Text(
                stringResource(R.string.editor_schedule_overnight),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }

    pickingStart?.let { start ->
        TimePickerDialog(
            initialMinute = if (start) startMinute else endMinute,
            onDismiss = { pickingStart = null },
            onConfirm = {
                if (start) onStartChange(it) else onEndChange(it)
                pickingStart = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = DateFormat.is24HourFormat(context)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun RecipientsStep(
    recipients: List<String>,
    onRemove: (String) -> Unit,
    onAdd: (String) -> Unit,
    numberInput: String,
    onNumberInputChange: (String) -> Unit,
    numberError: String?,
    onNumberError: (String?) -> Unit,
    onAddTyped: () -> Unit
) {
    val context = LocalContext.current
    val contactNames = rememberContactNames(recipients)

    // Picking a single phone entry grants temporary read access to just that entry,
    // so the app does not need the READ_CONTACTS permission.
    val contactPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val number = readPhoneNumber(context, uri)
        if (number == null) {
            onNumberError(context.getString(R.string.editor_contact_no_number))
        } else {
            onNumberError(null)
            onAdd(number)
        }
    }

    StepIntro(R.string.editor_recipients_title, R.string.editor_recipients_text)

    if (recipients.isEmpty()) {
        Text(stringResource(R.string.editor_no_recipients), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
    recipients.forEach { number ->
        RecipientEntry(number = number, name = contactNames[number], onRemove = { onRemove(number) })
    }
    OutlinedTextField(
        value = numberInput,
        onValueChange = onNumberInputChange,
        label = { Text(stringResource(R.string.editor_add_number_label)) },
        singleLine = true,
        isError = numberError != null,
        supportingText = { Text(numberError ?: stringResource(R.string.editor_add_number_hint)) },
        trailingIcon = {
            Row {
                IconButton(
                    onClick = {
                        try {
                            contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                        } catch (e: ActivityNotFoundException) {
                            onNumberError(context.getString(R.string.editor_no_contacts_app))
                        }
                    }
                ) {
                    Icon(Icons.Outlined.Contacts, contentDescription = stringResource(R.string.editor_pick_contact))
                }
                IconButton(onClick = onAddTyped, enabled = numberInput.isNotBlank()) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.editor_add_number))
                }
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onAddTyped() }),
        modifier = Modifier.fillMaxWidth()
    )
    Text(stringResource(R.string.editor_cost_hint), color = MaterialTheme.colorScheme.tertiary, fontSize = 12.sp)
}

@Composable
private fun SendStep(
    name: String,
    onNameChange: (String) -> Unit,
    subscriptionId: Int?,
    onSubscriptionChange: (Int?) -> Unit,
    rule: ForwardRule,
    showSimPicker: Boolean
) {
    StepIntro(R.string.editor_send_title, R.string.editor_send_text)

    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.editor_name_label)) },
        placeholder = { Text(stringResource(R.string.editor_name_placeholder)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    if (showSimPicker) {
        SimPicker(selected = subscriptionId, onSelect = onSubscriptionChange)
    }

    SectionLabel(stringResource(R.string.editor_summary))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = cardBorder()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryLine(R.string.editor_summary_if, filterSummary(rule.senderFilter, rule.messageFilter))
            if (rule.excludeFilter.isNotBlank()) {
                SummaryLine(R.string.editor_summary_unless, rule.excludeFilter)
            }
            if (rule.useRegex) {
                SummaryLine(R.string.editor_summary_mode, stringResource(R.string.rule_summary_regex))
            }
            SummaryLine(R.string.editor_summary_when, scheduleSummary(rule.schedule))
            val contactNames = rememberContactNames(rule.recipients)
            SummaryLine(
                R.string.editor_summary_to,
                rule.recipients.joinToString(", ") { contactNames[it] ?: it }.ifEmpty { "–" }
            )
        }
    }
}

@Composable
private fun SummaryLine(@StringRes label: Int, value: String) {
    Row {
        Text(
            stringResource(label),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.width(72.dp)
        )
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SwitchRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, title: String, text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = cardBorder()
    ) {
        Row(
            modifier = Modifier
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun SimPicker(selected: Int?, onSelect: (Int?) -> Unit) {
    val context = LocalContext.current
    // Read again once the permission is granted.
    var permissionGeneration by remember { mutableIntStateOf(0) }
    val canRead = remember(permissionGeneration) { SimCards.canRead(context) }
    val simCards = remember(permissionGeneration) { SimCards.active(context) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionGeneration++ }

    SectionLabel(stringResource(R.string.editor_sim_label))
    if (!canRead) {
        Text(stringResource(R.string.editor_sim_permission), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        TextButton(onClick = { permissionLauncher.launch(Manifest.permission.READ_PHONE_STATE) }) {
            Text(stringResource(R.string.action_allow))
        }
        return
    }

    // A chosen SIM that is no longer inserted stays selectable so the rule is not changed silently.
    val options = listOf<Int?>(null) + simCards.map(SimCard::subscriptionId) +
        listOfNotNull(selected?.takeIf { id -> simCards.none { it.subscriptionId == id } })
    Column(Modifier.selectableGroup()) {
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = option == selected, role = Role.RadioButton) { onSelect(option) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = option == selected, onClick = null, modifier = Modifier.padding(8.dp))
                Text(simLabel(option, simCards), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun RecipientEntry(number: String, name: String?, onRemove: () -> Unit) {
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (name != null) Icons.Outlined.Person else Icons.Outlined.Phone,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            ContactLabel(
                number = number,
                name = name,
                color = contentColor,
                secondaryColor = contentColor.copy(alpha = 0.75f),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.editor_remove_number, name ?: number),
                    tint = contentColor
                )
            }
        }
    }
}

private fun readPhoneNumber(context: Context, uri: Uri): String? {
    val rawNumber = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null,
            null,
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull() ?: return null
    return normalizePhoneNumber(rawNumber.trim())
}
