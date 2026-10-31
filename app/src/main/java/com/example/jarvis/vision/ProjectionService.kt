package com.example.jarvis.vision

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat

/** Só necessário em Android < 11 (ex.: EMUI 10): captura contínua leve via MediaProjection. */
class ProjectionService : Service() {

    companion object {
        private const val CH = "giselle_projection"
        @Volatile private var reader: ImageReader? = null
        @Volatile var running = false; private set

        fun start(ctx: Context, resultCode: Int, data: Intent) {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, ProjectionService::class.java).putExtra("code", resultCode).putExtra("data", data)
            )
        }

        fun latestFrame(): Bitmap? {
            val img = reader?.acquireLatestImage() ?: return null
            return try {
                val p = img.planes[0]
                val rowPad = p.rowStride - p.pixelStride * img.width
                val bmp = Bitmap.createBitmap(img.width + rowPad / p.pixelStride, img.height, Bitmap.Config.ARGB_8888)
                bmp.copyPixelsFromBuffer(p.buffer)
                Bitmap.createBitmap(bmp, 0, 0, img.width, img.height)
            } finally { img.close() }
        }
    }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Giselle (visão)", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(this, CH).setContentTitle("Giselle: visão ativa")
            .setSmallIcon(android.R.drawable.ic_menu_view).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1003, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(1003, n)

        val code = intent?.getIntExtra("code", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.let { IntentCompat.getParcelableExtra(it, "data", Intent::class.java) }
        if (code != Activity.RESULT_OK || data == null) { stopSelf(); return START_NOT_STICKY }

        val mpm = getSystemService(MediaProjectionManager::class.java)
        val mp = mpm.getMediaProjection(code, data)
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { running = false; reader = null }
        }, Handler(Looper.getMainLooper()))
        val dm = resources.displayMetrics
        val r = ImageReader.newInstance(dm.widthPixels, dm.heightPixels, PixelFormat.RGBA_8888, 2)
        display = mp.createVirtualDisplay(
            "giselle", dm.widthPixels, dm.heightPixels, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, null
        )
        projection = mp; reader = r; running = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false; reader = null
        display?.release(); projection?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
