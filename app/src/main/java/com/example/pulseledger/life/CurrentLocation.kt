package com.example.pulseledger.life

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

object CurrentLocation {
    @SuppressLint("MissingPermission")
    suspend fun get(ctx: Context): Pair<Double, Double>? = suspendCancellableCoroutine { cont ->
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val last = runCatching {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }.getOrNull()
        if (last != null) cont.resume(last.latitude to last.longitude)
        else runCatching {
            lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, { loc ->
                if (cont.isActive) cont.resume(loc.latitude to loc.longitude)
            }, null)
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }
}
