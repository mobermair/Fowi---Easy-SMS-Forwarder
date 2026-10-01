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

// Colors taken from the logo: its green gradient, the lime arrow, the arrow's dark outline,
// the white speech bubble and the muted green of the text lines in the bubble.
private val LogoGreen = Color(0xFF1B6C58)
private val LogoGreenDeep = Color(0xFF0D493D)
private val LogoOutline = Color(0xFF0C4B3B)
private val LogoLime = Color(0xFFCEE76F)
private val BubbleWhite = Color(0xFFFBFCF6)
private val BubbleShade = Color(0xFFF2F6EE)
private val Ink = Color(0xFF14261F)
// Warnings and costs keep a warm accent so they stand out from the green.
private val Coral = Color(0xFFB4533A)

// Light: green actions on the bubble's white, with the lime arrow as accent (switches, tab marker).
private val LightColors = lightColorScheme(
    primary = LogoGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEEE2),
    onPrimaryContainer = LogoGreenDeep,
    secondary = LogoLime,
    onSecondary = LogoOutline,
    secondaryContainer = LogoLime,
    onSecondaryContainer = LogoOutline,
    tertiary = Coral,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBEDE5),
    onTertiaryContainer = Color(0xFF6E2D1C),
    background = BubbleShade,
    onBackground = Ink,
    surface = BubbleWhite,
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF4F665C),
    outline = Color(0xFF7D948A),
    outlineVariant = Color(0xFFD9E4DB),
    surfaceVariant = Color(0xFFE4EDE5),
    surfaceContainer = Color(0xFFEDF3EC),
    surfaceContainerHigh = Color(0xFFE8EFE7),
    surfaceContainerHighest = Color(0xFFE1EAE1)
)

// Dark: like the logo, the lime arrow stands out on deep green; actions are lime with outline-green text.
private val DarkColors = darkColorScheme(
    primary = LogoLime,
    onPrimary = LogoOutline,
    primaryContainer = Color(0xFF1C4A3D),
    onPrimaryContainer = Color(0xFFE3F2B4),
    secondary = Color(0xFF3E8F77),
    onSecondary = Color.White,
    secondaryContainer = LogoGreen,
    onSecondaryContainer = LogoLime,
    tertiary = Color(0xFFF0A58E),
    onTertiary = Color(0xFF5A1D0E),
    tertiaryContainer = Color(0xFF3D2620),
    onTertiaryContainer = Color(0xFFFFDBD0),
    background = Color(0xFF0B1A16),
    onBackground = Color(0xFFE4EDE6),
    surface = Color(0xFF11241E),
    onSurface = Color(0xFFE4EDE6),
    onSurfaceVariant = Color(0xFF9DB3A9),
    outline = Color(0xFF5F7A6F),
    outlineVariant = Color(0xFF24392F),
    surfaceVariant = Color(0xFF1C3129),
    surfaceContainer = Color(0xFF142A23),
    surfaceContainerHigh = Color(0xFF193128),
    surfaceContainerHighest = Color(0xFF20392F)
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
