package com.example.blueheartv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.blueheartv.R
import com.example.blueheartv.model.SmartRecommendation
import com.example.blueheartv.ui.theme.BlueAccent
import com.example.blueheartv.ui.theme.GlassFill
import com.example.blueheartv.ui.theme.GlassUpperHighlight
import com.example.blueheartv.ui.theme.GradientWhite00
import com.example.blueheartv.ui.theme.GradientWhite40
import com.example.blueheartv.ui.theme.IconGray
import com.example.blueheartv.ui.theme.MutedText
import com.example.blueheartv.ui.theme.Slate700Stroke
import com.example.blueheartv.ui.theme.SurfaceWhite
import com.example.blueheartv.ui.theme.TextBlack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 智能推荐卡片轮播组件
 *
 * 支持功能：
 * - 左右滑动切换卡片（堆叠式动画）
 * - 自动播放（3秒间隔，手动交互后暂停10秒再恢复）
 * - 无限循环
 * - 指示器联动（可点击切换）
 * - 保留原有卡片堆叠视觉效果
 *
 * 动画方案：
 * - 当前卡片：向右下方平移 + 旋转 + 透明度降为0（250ms）
 * - 下一张卡片：从后方平滑前移至顶层 + 放大到完整尺寸（250ms）
 * - 底层卡片：跟随微调位置
 */
@Composable
fun SmartCardCarousel(
    recommendations: List<SmartRecommendation>,
    onCardClick: (SmartRecommendation) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (recommendations.isEmpty()) return

    val cardCount = recommendations.size.coerceAtMost(3)
    val cards = recommendations.take(cardCount)

    // 边界情况：只有1条推荐，降级为单卡片展示
    if (cardCount == 1) {
        SingleCardDisplay(
            recommendation = cards[0],
            onCardClick = onCardClick,
            modifier = modifier,
        )
        return
    }

    // 完整轮播系统（2条或3条推荐）
    CarouselWithStackedCards(
        cards = cards,
        cardCount = cardCount,
        onCardClick = onCardClick,
        modifier = modifier,
    )
}

@Composable
private fun SingleCardDisplay(
    recommendation: SmartRecommendation,
    onCardClick: (SmartRecommendation) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 320.dp),
    ) {
        val cardWidth = (maxWidth * 0.67f).coerceIn(200.dp, 320.dp)
        val cardHeight = (cardWidth * 0.54f).coerceIn(120.dp, 180.dp)

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = maxWidth * 0.17f, y = maxHeight * 0.18f),
        ) {
            SmartCardContent(
                recommendation = recommendation,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                onClick = { onCardClick(recommendation) },
                withFaceImage = true,
                contentAlpha = 1f,
            )
        }
    }
}

@Composable
private fun CarouselWithStackedCards(
    cards: List<SmartRecommendation>,
    cardCount: Int,
    onCardClick: (SmartRecommendation) -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var isAnimating by remember { mutableFloatStateOf(0f) }
    var lastInteractionTime by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()

    // 自动播放逻辑
    LaunchedEffect(cardCount, currentIndex) {
        while (true) {
            val timeSinceInteraction = System.currentTimeMillis() - lastInteractionTime
            if (timeSinceInteraction >= 10000 && isAnimating == 0f) {
                delay(3000)
                // 触发切换到下一张
                scope.launch {
                    isAnimating = 1f
                    delay(250)
                    currentIndex = (currentIndex + 1) % cardCount
                    isAnimating = 0f
                }
            } else {
                delay(1000)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = 320.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        // 手势结束后的处理
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        lastInteractionTime = System.currentTimeMillis()

                        // 向左滑动（dragAmount < 0）切换到下一张
                        if (dragAmount < -50 && isAnimating == 0f) {
                            scope.launch {
                                isAnimating = 1f
                                delay(250)
                                currentIndex = (currentIndex + 1) % cardCount
                                isAnimating = 0f
                            }
                        }
                        // 向右滑动（dragAmount > 0）切换到上一张
                        else if (dragAmount > 50 && isAnimating == 0f) {
                            scope.launch {
                                isAnimating = 1f
                                delay(250)
                                currentIndex = (currentIndex - 1 + cardCount) % cardCount
                                isAnimating = 0f
                            }
                        }
                    }
                )
            },
    ) {
        val cardWidth = (maxWidth * 0.67f).coerceIn(200.dp, 320.dp)
        val cardHeight = (cardWidth * 0.54f).coerceIn(120.dp, 180.dp)
        val cardOffsets = listOf(0.06f, 0.17f, 0.27f)
        val cardTops = listOf(0.22f, 0.18f, 0.14f)

        // 渲染卡片堆叠（从后到前）
        val displayCards = List(cardCount) { index ->
            val actualIndex = (currentIndex + index) % cardCount
            cards[actualIndex]
        }

        displayCards.asReversed().forEachIndexed { drawIndex, recommendation ->
            val sourceIndex = cardCount - 1 - drawIndex
            val startOffset = maxWidth * cardOffsets.getOrElse(sourceIndex) { 0.27f }
            val topOffset = maxHeight * cardTops.getOrElse(sourceIndex) { 0.14f }
            val cardAlpha = when (sourceIndex) {
                0 -> 1f
                1 -> 0.92f
                else -> 0.85f
            }

            // 动画效果
            val animatedAlpha = if (sourceIndex == 0 && isAnimating > 0f) {
                1f - isAnimating
            } else if (sourceIndex == 1 && isAnimating > 0f) {
                0.92f + (0.08f * isAnimating)
            } else {
                cardAlpha
            }

            val animatedOffsetX = if (sourceIndex == 0 && isAnimating > 0f) {
                startOffset + (200.dp * isAnimating)
            } else if (sourceIndex == 1 && isAnimating > 0f) {
                startOffset - ((startOffset - maxWidth * 0.17f) * isAnimating)
            } else {
                startOffset
            }

            val animatedOffsetY = if (sourceIndex == 0 && isAnimating > 0f) {
                topOffset + (100.dp * isAnimating)
            } else if (sourceIndex == 1 && isAnimating > 0f) {
                topOffset - ((topOffset - maxHeight * 0.18f) * isAnimating)
            } else {
                topOffset
            }

            val animatedRotation = if (sourceIndex == 0 && isAnimating > 0f) {
                8f * isAnimating
            } else {
                0f
            }

            val animatedScale = if (sourceIndex == 1 && isAnimating > 0f) {
                0.95f + (0.05f * isAnimating)
            } else {
                1f
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = animatedOffsetX, y = animatedOffsetY)
                    .graphicsLayer {
                        scaleX = animatedScale
                        scaleY = animatedScale
                        rotationZ = animatedRotation
                    }
                    .alpha(animatedAlpha)
                    .zIndex(drawIndex.toFloat()),
            ) {
                SmartCardContent(
                    recommendation = recommendation,
                    cardWidth = cardWidth,
                    cardHeight = cardHeight,
                    onClick = if (sourceIndex == 0) {
                        { onCardClick(recommendation) }
                    } else {
                        {}
                    },
                    withFaceImage = true,
                    contentAlpha = if (sourceIndex == 0) 1f else 0.85f,
                )
            }
        }
    }

    // 水平指示器（放在卡片区域正下方）
    Spacer(modifier = Modifier.height(12.dp))
    HorizontalIndicator(
        cardCount = cardCount,
        currentIndex = currentIndex,
        onDotClick = { index ->
            if (index != currentIndex && isAnimating == 0f) {
                lastInteractionTime = System.currentTimeMillis()
                scope.launch {
                    isAnimating = 1f
                    delay(250)
                    currentIndex = index
                    isAnimating = 0f
                }
            }
        },
    )
  }
}

@Composable
private fun HorizontalIndicator(
    cardCount: Int,
    currentIndex: Int,
    onDotClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(cardCount) { index ->
            val isActive = (currentIndex == index)

            // 动画大小
            val size by animateDpAsState(
                targetValue = if (isActive) 8.dp else 6.dp,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label = "dot_size",
            )

            // 动画颜色
            val color by animateColorAsState(
                targetValue = if (isActive) BlueAccent else IconGray.copy(alpha = 0.35f),
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label = "dot_color",
            )

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clickable(enabled = !isActive) {
                        onDotClick(index)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
    }
}

@Composable
private fun VerticalIndicator(
    cardCount: Int,
    currentIndex: Int,
    onDotClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(cardCount) { index ->
            val isActive = (currentIndex == index)

            // 动画大小
            val size by animateDpAsState(
                targetValue = if (isActive) 8.dp else 6.dp,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label = "dot_size",
            )

            // 动画颜色
            val color by animateColorAsState(
                targetValue = if (isActive) BlueAccent else IconGray.copy(alpha = 0.35f),
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label = "dot_color",
            )

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clickable(enabled = !isActive) {
                        onDotClick(index)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
    }
}

@Composable
private fun SmartCardContent(
    recommendation: SmartRecommendation,
    cardWidth: Dp,
    cardHeight: Dp,
    onClick: () -> Unit,
    withFaceImage: Boolean,
    contentAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .size(width = cardWidth, height = cardHeight)
            .clip(shape)
            .clickable { onClick() },
    ) {
        // 玻璃态背景层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(GlassFill, shape),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(GradientWhite00, SurfaceWhite)),
                    shape,
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(21.dp)
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to GradientWhite40,
                            1f to GradientWhite00,
                        ),
                    ),
                    shape,
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, Slate700Stroke, shape),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, GlassUpperHighlight, shape),
        )

        // 卡片内容
        if (withFaceImage) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_echo_face),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(48.dp)
                        .shadow(20.dp, RoundedCornerShape(24.dp)),
                )
                Spacer(modifier = Modifier.height(22.dp))
                Text(
                    text = recommendation.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextBlack,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(contentAlpha),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = recommendation.subtitle,
                    fontSize = 12.sp,
                    color = MutedText,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(contentAlpha),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 12.dp, top = 20.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 42.7.dp, height = 42.dp)
                        .shadow(10.dp, RoundedCornerShape(12.dp))
                        .background(SurfaceWhite, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_echo_face),
                        contentDescription = null,
                        modifier = Modifier.size(width = 24.dp, height = 36.dp),
                    )
                }
                Spacer(modifier = Modifier.height(22.dp))
                Text(
                    text = recommendation.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextBlack,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.alpha(contentAlpha),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = recommendation.subtitle,
                    fontSize = 12.sp,
                    color = MutedText,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.alpha(contentAlpha),
                )
            }
        }
    }
}
