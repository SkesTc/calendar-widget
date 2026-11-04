package com.skestc.calendarwidget.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 天氣「跟隨 GPS」用的大概位置（不需要 Google Play 服務）。
 * Android 不讓 App 在背景取得位置，所以在開啟 App 時更新；背景時用系統記得的最後位置。
 */
object LocationReader {
    fun hasPermission(context: Context): Boolean =
        listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION).any {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }

    /** 系統記得的最新位置；沒有權限或沒有資料時回傳 null。 */
    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        return runCatching {
            manager.getProviders(true)
                .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
        }.getOrNull()
    }

    /** 在前景取得目前位置（最多等 10 秒），拿不到時用系統記得的最後位置。 */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val provider = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.isProviderEnabled(LocationManager.FUSED_PROVIDER) ->
                    LocationManager.FUSED_PROVIDER
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                else -> null
            }
            if (provider != null) {
                val fresh = withTimeoutOrNull(10_000) {
                    suspendCancellableCoroutine<Location?> { continuation ->
                        val signal = CancellationSignal()
                        continuation.invokeOnCancellation { signal.cancel() }
                        runCatching {
                            manager.getCurrentLocation(provider, signal, context.mainExecutor) { continuation.resume(it) }
                        }.onFailure { continuation.resume(null) }
                    }
                }
                if (fresh != null) return fresh
            }
        }
        return lastKnown(context)
    }

    /** 開 App 時呼叫：GPS 模式就更新位置，必要時馬上抓新的天氣。 */
    suspend fun refreshForWeather(context: Context, onStale: () -> Unit) {
        val store = WeatherStore(context)
        if (store.mode != WeatherLocationMode.GPS) {
            if (store.isStale()) onStale()
            return
        }
        current(context)?.let { store.saveGps(it.latitude, it.longitude) }
        if (store.isStale()) onStale()
    }
}
