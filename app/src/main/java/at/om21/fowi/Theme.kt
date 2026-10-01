package at.om21.fowi

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Forest = Color(0xFF21594C)
private val Leaf = Color(0xFFBED66A)
private val Coral = Color(0xFFB85840)
private val Ink = Color(0xFF1D2925)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9F1E7),
    onPrimaryContainer = Forest,
    secondary = Leaf,
    onSecondary = Ink,
    secondaryContainer = Leaf,
    onSecondaryContainer = Ink,
    tertiary = Coral,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF0E9),
    onTertiaryContainer = Color(0xFF7A3422),
    background = Color(0xFFF5F7F2),
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF66736D),
    outlineVariant = Color(0xFFE1E7DF),
    surfaceContainer = Color(0xFFF0F3EE),
    surfaceContainerHigh = Color(0xFFF0F3EE),
    surfaceContainerHighest = Color(0xFFE6EBE4)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD0B8),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF1E3A31),
    onPrimaryContainer = Color(0xFFB7EBD6),
    secondary = Leaf,
    onSecondary = Ink,
    secondaryContainer = Leaf,
    onSecondaryContainer = Ink,
    tertiary = Color(0xFFF2A28C),
    onTertiary = Color(0xFF5A1D0E),
    tertiaryContainer = Color(0xFF43261D),
    onTertiaryContainer = Color(0xFFFFDBD0),
    background = Color(0xFF0F1513),
    onBackground = Color(0xFFE1E8E3),
    surface = Color(0xFF18201C),
    onSurface = Color(0xFFE1E8E3),
    onSurfaceVariant = Color(0xFFA0ADA6),
    outlineVariant = Color(0xFF2F3B35),
    surfaceContainer = Color(0xFF1D2521),
    surfaceContainerHigh = Color(0xFF222B26),
    surfaceContainerHighest = Color(0xFF2B3530)
)

// The light/dark choice from the settings is applied through AppCompat's night mode,
// which is reflected in the configuration that isSystemInDarkTheme() reads.
@Composable
internal fun FowiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}

@Composable
internal fun cardBorder() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

@Composable
internal fun ScreenHeader(
    title: String,
    subtitle: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.app_eyebrow),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
            }
        }
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/**
 * Reads a value from the given preferences file and re-reads it whenever that file changes,
 * so screens pick up writes made by the broadcast receivers while the app is open.
 */
@Composable
internal fun <T> observePreferences(preferencesName: String, read: (Context) -> T): T {
    val context = LocalContext.current
    var value by remember { mutableStateOf(read(context)) }
    DisposableEffect(preferencesName) {
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> value = read(context) }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        value = read(context)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value
}
