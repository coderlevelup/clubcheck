package app.clubcheck.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import app.clubcheck.core.CoarseCell
import app.clubcheck.core.coarseCellFrom

/**
 * Approximate location from the framework's last known fix (no Play services needed). The exact fix is
 * snapped to a coarse cell immediately and discarded; denied, off or unavailable all give [CoarseCell.NONE].
 */
class CoarseLocation(private val context: Context) {
    suspend fun current(enabled: Boolean): CoarseCell {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return coarseCellFrom(granted, enabled) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            @Suppress("MissingPermission")
            val fix = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER).firstNotNullOfOrNull { lm.getLastKnownLocation(it) }
            fix?.let { it.latitude to it.longitude }
        }
    }
}
