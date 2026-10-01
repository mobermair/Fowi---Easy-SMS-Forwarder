package com.smsforwarder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Looks up contact names for phone numbers. Names are read live from the address book and never
 * stored, so renamed contacts show up immediately. Requires the optional READ_CONTACTS permission.
 */
object ContactNames {
    fun canRead(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** PhoneLookup matches regardless of formatting, e.g. "+49171…" and "0171…". */
    fun lookup(context: Context, number: String): String? {
        if (!canRead(context) || number.isBlank()) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()?.takeIf(String::isNotBlank)
    }

    fun displayName(context: Context, number: String): String = lookup(context, number) ?: number
}

/**
 * Returns the contact names for [numbers], loaded off the main thread. Lookups are repeated
 * when the app resumes, since contacts or the permission may have changed in the meantime.
 */
@Composable
internal fun rememberContactNames(numbers: Collection<String>): Map<String, String> {
    val context = LocalContext.current
    var generation by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { generation++ }
    val keys = numbers.distinct().sorted()
    val names by produceState(emptyMap<String, String>(), keys, generation) {
        value = withContext(Dispatchers.IO) {
            keys.mapNotNull { number -> ContactNames.lookup(context, number)?.let { number to it } }.toMap()
        }
    }
    return names
}

/** Shows the contact name with the number below it, or only the number if there is no contact. */
@Composable
internal fun ContactLabel(
    number: String,
    name: String?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    secondaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(modifier) {
        Text(
            name ?: number,
            color = color,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (name != null) {
            Text(number, color = secondaryColor, fontSize = 12.sp, maxLines = 1)
        }
    }
}
