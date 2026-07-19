package com.example.pulseledger.life

import android.app.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.ScanCallback
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.example.pulseledger.data.db.Db
import com.example.pulseledger.data.db.TogetherDay
import com.example.pulseledger.ui.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Opt-in background together sensing: advertises the couple beacon
 * continuously (very low power) and samples a short scan every 10 minutes.
 * Each positive sample credits 10 minutes to today's together log.
 */
class TogetherService : Service() {

    private var advCb: AdvertiseCallback? = null
    private var scanCb: ScanCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)
    private var seenThisSample = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Profile.init(this)
        val code = Profile.partnerCode ?: run { stopSelf(); return START_NOT_STICKY }
        startForeground(7011, buildNotification())
        advCb = TogetherBeacon.startAdvertising(this, code)
        scheduleSample(code)
        return START_STICKY
    }

    private fun scheduleSample(code: String) {
        handler.postDelayed({
            seenThisSample = false
            scanCb = TogetherBeacon.startScan(this, code) { seenThisSample = true }
            handler.postDelayed({
                TogetherBeacon.stopScan(this, scanCb); scanCb = null
                if (seenThisSample) creditMinutes(10)
                scheduleSample(code)
            }, 12_000)
        }, 10 * 60_000L)
    }

    private fun creditMinutes(min: Int) = scope.launch {
        val zone = java.time.ZoneId.systemDefault()
        val day = java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val dao = Db.get(applicationContext).dao()
        val prev = dao.togetherFor(day)?.minutes ?: 0
        dao.upsertTogether(TogetherDay(day, prev + min))
    }

    private fun buildNotification(): Notification {
        val ch = NotificationChannel("together", "Together sensing",
            NotificationManager.IMPORTANCE_MIN)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(ch)
        return Notification.Builder(this, "together")
            .setContentTitle("Together sensing on")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
    }

    override fun onDestroy() {
        TogetherBeacon.stopAdvertising(this, advCb)
        TogetherBeacon.stopScan(this, scanCb)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        fun sync(ctx: Context) {
            Profile.init(ctx)
            val i = Intent(ctx, TogetherService::class.java)
            if (Profile.partnerCode != null && Profile.togetherBackground) {
                runCatching { ctx.startForegroundService(i) }
            } else ctx.stopService(i)
        }
    }
}
