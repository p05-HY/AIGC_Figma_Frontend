package com.example.blueheartv.control

import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/**
 * 截图降采样的缩放还原系数（唯一可信源）。
 *
 * 方案 A：模型始终工作在「降采样后的小图」坐标系。握手 connect 上报降采样后分辨率，
 * 模型据此返回坐标；执行真实点击/滑动前，前端用 restoreX/restoreY 把坐标还原到设备真实像素。
 *
 * restoreX/restoreY 恒 >= 1（= 原始尺寸 / 降采样尺寸）。
 * 全程使用 Double，只在最后一步取整（roundToInt），避免 swipe 起止点累积误差。
 */
data class ScreenScale(
    val originalWidth: Int,
    val originalHeight: Int,
    val scaledWidth: Int,
    val scaledHeight: Int,
) {
    val restoreX: Double = if (scaledWidth > 0) originalWidth.toDouble() / scaledWidth else 1.0
    val restoreY: Double = if (scaledHeight > 0) originalHeight.toDouble() / scaledHeight else 1.0

    /** 模型坐标(降采样空间) → 设备真实像素，末端取整。 */
    fun toRealX(modelX: Int): Int = (modelX * restoreX).roundToInt()

    fun toRealY(modelY: Int): Int = (modelY * restoreY).roundToInt()
}

/**
 * 全局保存最近一次截图降采样的缩放系数。采集端写入，控制端读取还原坐标。
 */
object ScreenScaleState {
    private val ref = AtomicReference<ScreenScale?>(null)

    fun update(scale: ScreenScale) {
        ref.set(scale)
    }

    fun current(): ScreenScale? = ref.get()

    /** 还原 X 坐标；无缩放信息时按原值透传（identity）。 */
    fun toRealX(modelX: Int): Int = ref.get()?.toRealX(modelX) ?: modelX

    /** 还原 Y 坐标；无缩放信息时按原值透传（identity）。 */
    fun toRealY(modelY: Int): Int = ref.get()?.toRealY(modelY) ?: modelY
}
