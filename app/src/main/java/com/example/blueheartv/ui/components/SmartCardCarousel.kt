package com.example.blueheartv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.blueheartv.R
import com.example.blueheartv.model.SmartRecommendation
import com.example.blueheartv.ui.theme.BlueAccent
import com.example.blueheartv.ui.theme.IconGray
import com.example.blueheartv.ui.theme.MutedText
import com.example.blueheartv.ui.theme.Radius
import com.example.blueheartv.ui.theme.SurfaceWhite
import com.example.blueheartv.ui.theme.TextBlack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
 * - 当前卡片：沿平行层叠轨道退到后层，轻微缩小并降低透明度
 * - 下一张卡片：同步前移至顶层，放大并淡入
 * - 后方卡片：保持水平平行补位，动画期间保持卡片数量稳定
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
    var transitionDirection by remember { mutableIntStateOf(1) }
    val transitionProgress = remember { Animatable(0f) }
    var lastInteractionTime by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    val dragThresholdPx = with(LocalDensity.current) { 50.dp.toPx() }

    suspend fun animateToIndex(targetIndex: Int, direction: Int) {
        if (targetIndex == currentIndex || transitionProgress.isRunning) return

        transitionDirection = direction
        transitionProgress.snapTo(0f)
        transitionProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(360, easing = FastOutSlowInEasing),
        )
        currentIndex = targetIndex
        transitionProgress.snapTo(0f)
    }

    // 自动播放逻辑
    LaunchedEffect(cardCount) {
        while (true) {
            delay(3000)
            val timeSinceInteraction = System.currentTimeMillis() - lastInteractionTime
            if (timeSinceInteraction >= 10000 && !transitionProgress.isRunning) {
                animateToIndex(
                    targetIndex = (currentIndex + 1) % cardCount,
                    direction = 1,
                )
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
                .pointerInput(cardCount, currentIndex, dragThresholdPx) {
                    var accumulatedDrag = 0f

                    detectHorizontalDragGestures(
                        onDragStart = {
                            accumulatedDrag = 0f
                        },
                        onDragEnd = {
                            accumulatedDrag = 0f
                        },
                        onDragCancel = {
                            accumulatedDrag = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            lastInteractionTime = System.currentTimeMillis()

                            if (!transitionProgress.isRunning) {
                                accumulatedDrag += dragAmount

                                when {
                                    accumulatedDrag <= -dragThresholdPx -> {
                                        accumulatedDrag = 0f
                                        scope.launch {
                                            animateToIndex(
                                                targetIndex = (currentIndex + 1) % cardCount,
                                                direction = 1,
                                            )
                                        }
                                    }

                                    accumulatedDrag >= dragThresholdPx -> {
                                        accumulatedDrag = 0f
                                        scope.launch {
                                            animateToIndex(
                                                targetIndex = (currentIndex - 1 + cardCount) % cardCount,
                                                direction = -1,
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    )
                },
        ) {
            val cardWidth = (maxWidth * 0.67f).coerceIn(200.dp, 320.dp)
            val cardHeight = (cardWidth * 0.54f).coerceIn(120.dp, 180.dp)
            val cardShape = RoundedCornerShape(12.dp)

            cards.forEachIndexed { actualIndex, recommendation ->
                val relativeIndex = floorMod(actualIndex - currentIndex, cardCount)
                val transform = stackedCardTransform(
                    relativeIndex = relativeIndex,
                    direction = transitionDirection,
                    progress = transitionProgress.value,
                    cardCount = cardCount,
                    maxWidth = maxWidth,
                    maxHeight = maxHeight,
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = transform.offsetX, y = transform.offsetY)
                        .graphicsLayer {
                            scaleX = transform.scale
                            scaleY = transform.scale
                            alpha = transform.alpha
                        }
                        .shadow(transform.shadowElevation, cardShape, clip = false)
                        .zIndex(transform.zIndex),
                ) {
                    SmartCardContent(
                        recommendation = recommendation,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        onClick = if (relativeIndex == 0 && transitionProgress.value == 0f) {
                            { onCardClick(recommendation) }
                        } else {
                            {}
                        },
                        withFaceImage = true,
                        contentAlpha = transform.contentAlpha,
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
                if (index != currentIndex && !transitionProgress.isRunning) {
                    lastInteractionTime = System.currentTimeMillis()
                    scope.launch {
                        animateToIndex(
                            targetIndex = index,
                            direction = carouselDirection(currentIndex, index, cardCount),
                        )
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
    val shape = RoundedCornerShape(Radius.medium.dp)

    Box(
        modifier = modifier
            .size(width = cardWidth, height = cardHeight)
            .clip(shape)
            .clickable { onClick() },
    ) {
        GlassmorphismCardBackground(shape = shape)

        // 卡片内容
        if (withFaceImage) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.Start,
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
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(contentAlpha),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = recommendation.subtitle,
                    fontSize = 12.sp,
                    color = MutedText,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(contentAlpha),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(contentAlpha),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = recommendation.subtitle,
                    fontSize = 12.sp,
                    color = MutedText,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(contentAlpha),
                )
            }
        }
    }
}

private data class StackedCardTransform(
    val offsetX: Dp,
    val offsetY: Dp,
    val scale: Float,
    val alpha: Float,
    val contentAlpha: Float,
    val shadowElevation: Dp,
    val zIndex: Float,
)

private fun stackedCardTransform(
    relativeIndex: Int,
    direction: Int,
    progress: Float,
    cardCount: Int,
    maxWidth: Dp,
    maxHeight: Dp,
): StackedCardTransform {
    val lastSlot = cardCount - 1
    val fromSlot = relativeIndex.coerceIn(0, lastSlot)
    val toSlot = if (direction > 0) {
        if (fromSlot == 0) lastSlot else fromSlot - 1
    } else {
        if (fromSlot == lastSlot) 0 else fromSlot + 1
    }

    return lerp(
        start = stackSlotTransform(fromSlot, cardCount, maxWidth, maxHeight),
        stop = stackSlotTransform(toSlot, cardCount, maxWidth, maxHeight),
        fraction = progress.coerceIn(0f, 1f),
    )
}

private fun stackSlotTransform(
    slotIndex: Int,
    cardCount: Int,
    maxWidth: Dp,
    maxHeight: Dp,
): StackedCardTransform {
    val baseOffsetX = maxWidth * 0.06f
    val baseOffsetY = maxHeight * 0.22f
    val scales = listOf(1f, 0.98f, 0.96f)
    val alphas = listOf(1f, 0.92f, 0.85f)
    val contentAlphas = listOf(1f, 0.9f, 0.82f)
    val shadowElevations = listOf(16.dp, 9.dp, 5.dp)

    return StackedCardTransform(
        offsetX = baseOffsetX + (35.dp * slotIndex),
        offsetY = baseOffsetY - (16.dp * slotIndex),
        scale = scales.getOrElse(slotIndex) { 0.96f },
        alpha = alphas.getOrElse(slotIndex) { 0.85f },
        contentAlpha = contentAlphas.getOrElse(slotIndex) { 0.82f },
        shadowElevation = shadowElevations.getOrElse(slotIndex) { 5.dp },
        zIndex = (cardCount - slotIndex).toFloat(),
    )
}

private fun lerp(
    start: StackedCardTransform,
    stop: StackedCardTransform,
    fraction: Float,
): StackedCardTransform = StackedCardTransform(
    offsetX = lerp(start.offsetX, stop.offsetX, fraction),
    offsetY = lerp(start.offsetY, stop.offsetY, fraction),
    scale = lerp(start.scale, stop.scale, fraction),
    alpha = lerp(start.alpha, stop.alpha, fraction),
    contentAlpha = lerp(start.contentAlpha, stop.contentAlpha, fraction),
    shadowElevation = lerp(start.shadowElevation, stop.shadowElevation, fraction),
    zIndex = lerp(start.zIndex, stop.zIndex, fraction),
)

private fun lerp(start: Dp, stop: Dp, fraction: Float): Dp = start + ((stop - start) * fraction)

private fun lerp(start: Float, stop: Float, fraction: Float): Float = start + ((stop - start) * fraction)

private fun floorMod(value: Int, modulo: Int): Int = ((value % modulo) + modulo) % modulo

private fun carouselDirection(currentIndex: Int, targetIndex: Int, cardCount: Int): Int {
    val forwardDistance = floorMod(targetIndex - currentIndex, cardCount)
    val backwardDistance = floorMod(currentIndex - targetIndex, cardCount)
    return if (forwardDistance <= backwardDistance) 1 else -1
}
