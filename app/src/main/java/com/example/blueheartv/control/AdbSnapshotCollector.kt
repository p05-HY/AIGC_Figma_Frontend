package com.example.blueheartv.control

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File

private const val TAG = "AdbSnapshotCollector"

/** 降采样目标：长边像素上限（保持原始宽高比等比缩放，不做短边保底——列为观察项）。 */
private const val MAX_LONG_EDGE = 1280

/** WebP 有损压缩质量（0-100）。 */
private const val WEBP_QUALITY = 80

/**
 * 截图传输格式开关。默认 WebP（体积约为 PNG 的 1/3~1/5）。
 * 若联调发现后端识图链路无法解码 WebP，把此项改为 false 回退 PNG 即可，其余逻辑不变。
 */
private const val USE_WEBP = true

class AdbSnapshotCollector(
    context: Context,
    private val executor: ShizukuAdbExecutor,
) {

    suspend fun collect(): AdbSnapshot {
        val screenshot = captureScreenshotBase64()

        val ui = AdbAccessibilityService.dumpUiTree()
        val topActivity = resolveTopActivity()

        return AdbSnapshot(
            screenshot = screenshot,
            ui = ui,
            currentPackage = AdbAccessibilityService.currentPackageName() ?: topActivity.first,
            activity = AdbAccessibilityService.currentActivityName() ?: topActivity.second
        )
    }

    private suspend fun captureScreenshotBase64(): String? {
        return runCatching {
            // shell 用户可写的临时目录
            val tempFilePath = "/data/local/tmp/screenshot_${System.currentTimeMillis()}.png"
            val result = executor.execute("screencap -p '$tempFilePath'")

            if (!result.isSuccess) {
                Log.w(TAG, "screencap failed: ${result.stderr.ifBlank { result.stdout }}")
                return null
            }

            val file = File(tempFilePath)
            if (!file.exists() || file.length() <= 0L) {
                Log.w(TAG, "screencap produced empty file")
                return null
            }

            val bytes = file.readBytes()
            // 清理临时文件
            file.delete()

            // 降采样 + 压缩，同时更新缩放还原系数（唯一可信源）
            downsampleToBase64(bytes)
        }.onFailure { error ->
            Log.w(TAG, "capture screenshot failed: ${error.message}", error)
        }.getOrNull()
    }

    /**
     * 将原始 PNG 字节降采样到长边 <= [MAX_LONG_EDGE]（等比），压缩为 WebP/PNG 并 Base64。
     * 同时把原始/降采样尺寸写入 [ScreenScaleState]，供坐标还原使用。
     */
    private fun downsampleToBase64(bytes: ByteArray): String? {
        // 1. 仅解码边界，获取原始尺寸
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
        val originalWidth = boundsOptions.outWidth
        val originalHeight = boundsOptions.outHeight
        if (originalWidth <= 0 || originalHeight <= 0) {
            Log.w(TAG, "decode bounds failed: ${originalWidth}x$originalHeight")
            return null
        }

        // 2. 计算目标尺寸：长边 <= MAX_LONG_EDGE，保持宽高比
        val longEdge = maxOf(originalWidth, originalHeight)
        val ratio = if (longEdge > MAX_LONG_EDGE) MAX_LONG_EDGE.toFloat() / longEdge else 1f
        val targetWidth = maxOf(1, Math.round(originalWidth * ratio))
        val targetHeight = maxOf(1, Math.round(originalHeight * ratio))

        // 3. inSampleSize 预降采样，避免大图解码 OOM
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = computeInSampleSize(originalWidth, originalHeight, targetWidth, targetHeight)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            ?: run {
                Log.w(TAG, "decode bitmap failed")
                return null
            }

        // 4. 精确等比缩放到目标尺寸
        val scaled = if (decoded.width != targetWidth || decoded.height != targetHeight) {
            Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true).also {
                if (it != decoded) decoded.recycle()
            }
        } else {
            decoded
        }

        // 5. 压缩为 WebP（或回退 PNG）
        val output = ByteArrayOutputStream()
        scaled.compress(compressFormat(), WEBP_QUALITY, output)
        scaled.recycle()

        // 6. 更新缩放还原系数（唯一可信源）
        ScreenScaleState.update(
            ScreenScale(
                originalWidth = originalWidth,
                originalHeight = originalHeight,
                scaledWidth = targetWidth,
                scaledHeight = targetHeight,
            )
        )

        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    @Suppress("DEPRECATION")
    private fun compressFormat(): Bitmap.CompressFormat = when {
        !USE_WEBP -> Bitmap.CompressFormat.PNG
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Bitmap.CompressFormat.WEBP_LOSSY
        else -> Bitmap.CompressFormat.WEBP
    }

    /** 计算最大不超过目标尺寸的 2 的幂下采样系数。 */
    private fun computeInSampleSize(
        originalWidth: Int,
        originalHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): Int {
        var inSampleSize = 1
        var halfWidth = originalWidth / 2
        var halfHeight = originalHeight / 2
        while (halfWidth >= targetWidth && halfHeight >= targetHeight) {
            inSampleSize *= 2
            halfWidth /= 2
            halfHeight /= 2
        }
        return inSampleSize
    }

    private suspend fun resolveTopActivity(): Pair<String?, String?> {
        val output = runCatching {
            executor.execute("dumpsys window | grep mCurrentFocus").stdout
        }.getOrNull().orEmpty()
        if (output.isBlank()) return null to null
        val focusComponent = output.substringAfterLast(" ").substringBefore("}")
        val activity = focusComponent.substringAfter("/", "")
        val pkg = focusComponent.substringBefore("/", "")
        return pkg.ifBlank { null } to activity.ifBlank { null }
    }
}
