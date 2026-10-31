package com.example.jarvis.vision

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.view.Display
import androidx.annotation.RequiresApi
import com.example.jarvis.accessibility.JarvisAccessibilityService
import com.example.jarvis.accessibility.ScreenPrivacyFilter
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Screenshot: API 30+ via AccessibilityService (sem diálogo); abaixo disso, MediaProjection (ProjectionService). */
object ScreenCapture {
    suspend fun capture(): Bitmap? {
        val svc = JarvisAccessibilityService.instance
        if (svc == null || Build.VERSION.SDK_INT < 30) return null
        val pkg = svc.currentPackage() ?: return null
        if (ScreenPrivacyFilter.isSensitivePackage(pkg)) return null
        val bmp = viaAccessibility(svc) ?: return null
        val regions = svc.sensitiveScreenBounds()
        if (regions.isEmpty()) return bmp
        val safe = bmp.copy(Bitmap.Config.ARGB_8888, true) ?: return bmp
        val canvas = Canvas(safe)
        val scaleX = safe.width.toFloat() / svc.resources.displayMetrics.widthPixels.coerceAtLeast(1)
        val scaleY = safe.height.toFloat() / svc.resources.displayMetrics.heightPixels.coerceAtLeast(1)
        val paint = Paint().apply { color = 0xFF202124.toInt() }
        regions.forEach { rect ->
            canvas.drawRect(
                rect.left * scaleX, rect.top * scaleY, rect.right * scaleX, rect.bottom * scaleY, paint
            )
        }
        bmp.recycle()
        return safe
    }

    @RequiresApi(30)
    private suspend fun viaAccessibility(svc: AccessibilityService): Bitmap? =
        suspendCancellableCoroutine { cont ->
            svc.takeScreenshot(Display.DEFAULT_DISPLAY, svc.mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                    val bmp = Bitmap.wrapHardwareBuffer(r.hardwareBuffer, r.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                    r.hardwareBuffer.close()
                    if (cont.isActive) cont.resume(bmp)
                }
                override fun onFailure(errorCode: Int) { if (cont.isActive) cont.resume(null) }
            })
        }
}
