package com.momory.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine

sealed class LocationResult {
    data class Success(val location: Location) : LocationResult()
    object NoPermission : LocationResult()
    /** La permission est accordée, mais le service de localisation du téléphone est coupé. */
    object LocationServicesDisabled : LocationResult()
    /** Aucune position obtenue dans le délai imparti (GPS lent à s'initialiser, intérieur, etc.). */
    object Timeout : LocationResult()
}

/** Récupère la position du téléphone via l'API Android native (pas de Play Services requis). */
class LocationProvider(private val context: Context) {

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun isLocationEnabled(lm: LocationManager): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }

    /** Dernière position connue si assez récente (< 5 min), sinon en demande une fraîche (timeout 15s). */
    suspend fun getCurrentLocation(): LocationResult {
        if (!hasPermission()) return LocationResult.NoPermission
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!isLocationEnabled(lm)) return LocationResult.LocationServicesDisabled

        val cached = runCatching {
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        }.getOrNull()
        if (cached != null && System.currentTimeMillis() - cached.time < 5 * 60_000L) {
            return LocationResult.Success(cached)
        }

        val providers = listOfNotNull(
            LocationManager.GPS_PROVIDER.takeIf { lm.isProviderEnabled(it) },
            LocationManager.NETWORK_PROVIDER.takeIf { lm.isProviderEnabled(it) }
        )
        if (providers.isEmpty()) {
            return cached?.let { LocationResult.Success(it) } ?: LocationResult.LocationServicesDisabled
        }

        return suspendCancellableCoroutine { cont ->
            val handler = Handler(Looper.getMainLooper())
            var resolved = false
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (resolved) return
                    resolved = true
                    lm.removeUpdates(this)
                    handler.removeCallbacksAndMessages(null)
                    if (cont.isActive) cont.resume(LocationResult.Success(location), null)
                }
            }
            try {
                providers.forEach { lm.requestLocationUpdates(it, 0L, 0f, listener, Looper.getMainLooper()) }
            } catch (e: SecurityException) {
                resolved = true
                val fallback = cached?.let { LocationResult.Success(it) } ?: LocationResult.NoPermission
                if (cont.isActive) cont.resume(fallback, null)
                return@suspendCancellableCoroutine
            }
            cont.invokeOnCancellation { lm.removeUpdates(listener) }
            // Le premier GPS fix peut prendre du temps (initialisation à froid, intérieur) —
            // 15s laisse une vraie chance avant de retomber sur la dernière position connue.
            handler.postDelayed({
                if (resolved) return@postDelayed
                resolved = true
                lm.removeUpdates(listener)
                if (cont.isActive) {
                    val result = cached?.let { LocationResult.Success(it) } ?: LocationResult.Timeout
                    cont.resume(result, null)
                }
            }, 15000)
        }
    }
}
