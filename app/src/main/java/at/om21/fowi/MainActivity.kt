package at.om21.fowi

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

private enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    Home(R.string.tab_home, Icons.Outlined.Home),
    Rules(R.string.tab_rules, Icons.Outlined.Tune),
    History(R.string.tab_history, Icons.Outlined.History)
}

/** Whether the rule editor is open, and the rule it edits (null for a new one). */
private data class RuleEditorState(val open: Boolean = false, val rule: ForwardRule? = null)

// AppCompatActivity applies the in-app language and the light/dark choice from the settings.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val transparentBars = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = transparentBars, navigationBarStyle = transparentBars)
        val openHistory = intent.getBooleanExtra(EXTRA_OPEN_HISTORY, false)
        setContent {
            FowiTheme {
                FowiApp(initialTab = if (openHistory) Tab.History else Tab.Home)
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_HISTORY = "openHistory"
    }
}

@Composable
private fun FowiApp(initialTab: Tab) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }
    var historyFilter by rememberSaveable { mutableStateOf(HistoryFilter.All) }
    // Saveable so that the settings stay open when a language or theme change recreates the activity.
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var rules by remember { mutableStateOf(RuleStore.load(context)) }
    var ruleEditor by remember { mutableStateOf(RuleEditorState()) }
    var forwardingEnabled by remember { mutableStateOf(AppSettings.isForwardingEnabled(context)) }
    val snackbarHostState = remember { SnackbarHostState() }
    val openSettings = { showSettings = true }

    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(onBack = { showSettings = false })
        return
    }

    // Shown in place of the tabs, like the settings, so it gets the whole screen.
    if (ruleEditor.open) {
        RuleEditorScreen(
            initialRule = ruleEditor.rule,
            onDismiss = { ruleEditor = RuleEditorState() },
            onSave = { savedRule ->
                rules = if (rules.any { it.id == savedRule.id }) {
                    rules.map { if (it.id == savedRule.id) savedRule else it }
                } else {
                    rules + savedRule
                }
                RuleStore.save(context, rules)
                ruleEditor = RuleEditorState()
            }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selectedTab,
                        onClick = {
                            if (tab == Tab.History) historyFilter = HistoryFilter.All
                            selectedTab = tab
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        val screenModifier = Modifier.padding(innerPadding)
        when (selectedTab) {
            Tab.Home -> HomeScreen(
                forwardingEnabled = forwardingEnabled,
                onForwardingChange = { enabled ->
                    forwardingEnabled = enabled
                    AppSettings.setForwardingEnabled(context, enabled)
                },
                rules = rules,
                onOpenRules = { selectedTab = Tab.Rules },
                onOpenHistory = { filter ->
                    historyFilter = filter
                    selectedTab = Tab.History
                },
                onOpenSettings = openSettings,
                modifier = screenModifier
            )
            Tab.Rules -> RulesScreen(
                rules = rules,
                onRulesChange = { updatedRules ->
                    rules = updatedRules
                    RuleStore.save(context, updatedRules)
                },
                onOpenSettings = openSettings,
                onEditRule = { ruleEditor = RuleEditorState(open = true, rule = it) },
                modifier = screenModifier
            )
            Tab.History -> HistoryScreen(
                filter = historyFilter,
                onFilterChange = { historyFilter = it },
                snackbarHostState = snackbarHostState,
                onOpenSettings = openSettings,
                modifier = screenModifier
            )
        }
    }
}
