package at.om21.fowi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

data class SimCard(val subscriptionId: Int, val slotIndex: Int, val name: String)

/**
 * Lists the inserted SIM cards so rules can send from a specific one. Reading them requires the
 * optional READ_PHONE_STATE permission; without it, forwards use the phone's default SMS SIM.
 */
object SimCards {
    fun canRead(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    /** Number of SIM slots, readable without any permission. */
    @Suppress("DEPRECATION")
    fun slotCount(context: Context): Int {
        val telephony = context.getSystemService(TelephonyManager::class.java) ?: return 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) telephony.activeModemCount else telephony.phoneCount
    }

    @SuppressLint("MissingPermission")
    fun active(context: Context): List<SimCard> {
        if (!canRead(context)) return emptyList()
        val subscriptions = runCatching {
            context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList
        }.getOrNull().orEmpty()
        return subscriptions
            .map { info ->
                SimCard(
                    subscriptionId = info.subscriptionId,
                    slotIndex = info.simSlotIndex,
                    name = info.displayName?.toString().orEmpty().ifBlank { info.carrierName?.toString().orEmpty() }
                )
            }
            .sortedBy(SimCard::slotIndex)
    }

    /**
     * False when the SIM is known to be missing. Without the permission the app cannot check,
     * so it lets the system try and report the outcome.
     */
    fun isAvailable(context: Context, subscriptionId: Int?): Boolean {
        if (subscriptionId == null || !canRead(context)) return true
        return active(context).any { it.subscriptionId == subscriptionId }
    }
}

/** Returns the inserted SIM cards, read again when the app resumes. */
@Composable
internal fun rememberSimCards(): List<SimCard> {
    val context = LocalContext.current
    var generation by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { generation++ }
    return remember(generation) { SimCards.active(context) }
}

/** "SIM 1 · A1", the default SIM for null, or a hint that the chosen SIM is not inserted. */
@Composable
internal fun simLabel(subscriptionId: Int?, simCards: List<SimCard>): String {
    if (subscriptionId == null) return stringResource(R.string.sim_default)
    val sim = simCards.firstOrNull { it.subscriptionId == subscriptionId }
        ?: return stringResource(R.string.sim_missing)
    val slot = stringResource(R.string.sim_slot, sim.slotIndex + 1)
    return if (sim.name.isBlank()) slot else "$slot  ·  ${sim.name}"
}
