package com.example.blueheartv.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.control.ScreenScaleState
import kotlin.math.roundToInt

/**
 * 坐标校准页（联调用）。
 *
 * 用途：量化「截图降采样 → 模型坐标 → 还原点击」链路的精度。
 * - 全屏九宫格网格 + 参考十字标记（中心、四角、四分位），每个标记同时标注：
 *   real(设备真实像素) / model(降采样后模型坐标系像素)。
 * - 手指点按任意位置，实时显示该点的 real 像素与换算回的 model 坐标。
 *
 * 联调方法：让 Agent 点击屏幕中心，模型应发出约 (scaledW/2, scaledH/2) 的坐标；
 * 观察实际落点十字是否压在中心标记上，即可量化偏差。
 */
@Composable
fun CoordinateCalibrationScreen(
    onBack: () -> Unit = {},
) {
    val scale = ScreenScaleState.current()

    var touchReal by remember { mutableStateOf<Offset?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0E1116))) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset -> touchReal = offset },
                        onPress = { offset -> touchReal = offset },
                    )
                },
        ) {
            val w = size.width
            val h = size.height
            val gridColor = Color(0x33FFFFFF)
            val axisColor = Color(0x66FFFFFF)

            // 网格线（每 1/8）
            val cols = 8
            val rows = 8
            for (i in 1 until cols) {
                val x = w * i / cols
                drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
            }
            for (i in 1 until rows) {
                val y = h * i / rows
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            // 中轴
            drawLine(axisColor, Offset(w / 2, 0f), Offset(w / 2, h), strokeWidth = 2f)
            drawLine(axisColor, Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = 2f)

            // 参考标记点（相对比例 → 当前画布像素）
            val refs = listOf(
                0.5f to 0.5f,
                0.0f to 0.0f,
                1.0f to 0.0f,
                0.0f to 1.0f,
                1.0f to 1.0f,
                0.25f to 0.25f,
                0.75f to 0.25f,
                0.25f to 0.75f,
                0.75f to 0.75f,
            )
            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 26f
                isAntiAlias = true
            }
            refs.forEach { (fx, fy) ->
                val cx = (w * fx).coerceIn(8f, w - 8f)
                val cy = (h * fy).coerceIn(8f, h - 8f)
                drawCircle(Color(0xFF80A4E5), radius = 6f, center = Offset(cx, cy))
                drawLine(Color(0xFF80A4E5), Offset(cx - 18f, cy), Offset(cx + 18f, cy), strokeWidth = 2f)
                drawLine(Color(0xFF80A4E5), Offset(cx, cy - 18f), Offset(cx, cy + 18f), strokeWidth = 2f)

                // real 像素（画布像素即设备真实像素，因全屏）
                val realX = cx.roundToInt()
                val realY = cy.roundToInt()
                // model 像素（降采样坐标系）
                val label = if (scale != null) {
                    val mx = (realX * scale.scaledWidth / scale.originalWidth.toFloat()).roundToInt()
                    val my = (realY * scale.scaledHeight / scale.originalHeight.toFloat()).roundToInt()
                    "real($realX,$realY)\nmodel($mx,$my)"
                } else {
                    "real($realX,$realY)"
                }
                var ty = cy + 40f
                label.split("\n").forEach { line ->
                    drawContext.canvas.nativeCanvas.drawText(line, cx + 12f, ty, textPaint)
                    ty += 30f
                }
            }

            // 触摸落点十字
            touchReal?.let { p ->
                drawLine(Color(0xFFFF5252), Offset(p.x - 40f, p.y), Offset(p.x + 40f, p.y), strokeWidth = 3f)
                drawLine(Color(0xFFFF5252), Offset(p.x, p.y - 40f), Offset(p.x, p.y + 40f), strokeWidth = 3f)
                drawCircle(Color(0xFFFF5252), radius = 8f, center = p, style = Stroke(width = 3f))
            }
        }

        // 顶部信息卡
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xCC1A2030),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = Color.White,
                            )
                        }
                        Text(
                            text = "坐标校准",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    val scaleText = if (scale != null) {
                        "original ${scale.originalWidth}x${scale.originalHeight}  →  " +
                            "scaled ${scale.scaledWidth}x${scale.scaledHeight}\n" +
                            "restoreX=${"%.4f".format(scale.restoreX)}  restoreY=${"%.4f".format(scale.restoreY)}"
                    } else {
                        "暂无缩放信息（尚未采集过截图）。连接 ADB 并触发一次 observe 后再查看。"
                    }
                    Text(
                        text = scaleText,
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    touchReal?.let { p ->
                        val rx = p.x.roundToInt()
                        val ry = p.y.roundToInt()
                        val modelText = if (scale != null) {
                            val mx = (rx * scale.scaledWidth / scale.originalWidth.toFloat()).roundToInt()
                            val my = (ry * scale.scaledHeight / scale.originalHeight.toFloat()).roundToInt()
                            "  →  model($mx,$my)"
                        } else {
                            ""
                        }
                        Text(
                            text = "触摸 real($rx,$ry)$modelText",
                            color = Color(0xFFFF8A80),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
