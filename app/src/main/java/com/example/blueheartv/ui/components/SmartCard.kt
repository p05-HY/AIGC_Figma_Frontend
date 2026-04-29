package com.example.blueheartv.ui.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.blueheartv.R
import com.example.blueheartv.model.SmartRecommendation
import com.example.blueheartv.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

private const val AUTO_SCROLL_INTERVAL_MS = 3000L
private const val RESUME_DELAY_MS = 3000L
private const val SWITCH_ANIM_MS = 300
private const val SNAP_ANIM_MS = 200
private const val SWIPE_THRESHOLD_FRACTION = 0.3f
private const val VELOCITY_THRESHOLD_DP_S = 500f
private const val OVERSCROLL_DAMPING = 0.3f

@Composable
fun SmartCardRow(
    recommendations: List<SmartRecommendation>,
    modifier: Modifier = Modifier,
    onCardClick: (SmartRecommendation) -> Unit = {},
) {
    if (recommendations.isEmpty()) return

    val count = recommendations.size
    var currentIndex by remember { mutableIntStateOf(0) }
    val dragOffset = remember { Animatable(0f) }

    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val cardWidthDp = screenWidthDp - 46.dp
    val cardWidthPx = with(density) { cardWidthDp.toPx() }
    val velocityThresholdPx = with(density) { VELOCITY_THRESHOLD_DP_S.dp.toPx() }

    val scope = rememberCoroutineScope()

    // Track user interaction to pause auto-scroll
    var userInteracting by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(0L) }

    // Animate to a target index
    suspend fun animateToIndex(targetIndex: Int, durationMs: Int = SWITCH_ANIM_MS) {
        val clamped = targetIndex.coerceIn(0, count - 1)
        val delta = (clamped - currentIndex) * cardWidthPx
        val targetOffset = dragOffset.value - delta
        dragOffset.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = durationMs,
                easing = if (durationMs == SWITCH_ANIM_MS) FastOutSlowInEasing else FastOutLinearInEasing,
            ),
            initialVelocity = (targetOffset - dragOffset.value) * 5f,
        )
        currentIndex = clamped
    }

    // Snap back to current position
    suspend fun snapBack() {
        dragOffset.animateTo(
            targetValue = 0f,
            animationSpec = tween(SNAP_ANIM_MS, easing = FastOutLinearInEasing),
        )
    }

    // Auto-scroll
    LaunchedEffect(count) {
        if (count <= 1) return@LaunchedEffect
        while (true) {
            delay(AUTO_SCROLL_INTERVAL_MS.milliseconds)
            if (!userInteracting && System.currentTimeMillis() - lastInteractionTime > RESUME_DELAY_MS) {
                val next = (currentIndex + 1) % count
                val delta = if (next == 0) {
                    -(count - 1).toFloat() * cardWidthPx
                } else {
                    -cardWidthPx
                }
                dragOffset.snapTo(delta)
                currentIndex = next
                dragOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(SWITCH_ANIM_MS, easing = FastOutSlowInEasing),
                )
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(146.dp)
                .pointerInput(count) {
                    if (count <= 1) return@pointerInput

                    val velocityTracker = VelocityTracker()

                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitPointerEvent().changes.firstOrNull() ?: continue
                            if (!down.pressed) continue

                            userInteracting = true
                            velocityTracker.resetTracking()

                            var totalDragX = 0f
                            var totalDragY = 0f
                            var horizontalClaimed = false

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed) break

                                    val dx = change.position.x - change.previousPosition.x
                                    val dy = change.position.y - change.previousPosition.y
                                    totalDragX += dx
                                    totalDragY += dy

                                    if (!horizontalClaimed && (abs(totalDragX) > 10f || abs(totalDragY) > 10f)) {
                                        horizontalClaimed = abs(totalDragX) > abs(totalDragY)
                                        if (!horizontalClaimed) break
                                    }

                                    if (horizontalClaimed) {
                                        change.consume()
                                        velocityTracker.addPosition(
                                            change.uptimeMillis,
                                            change.position,
                                        )

                                        var appliedDx = dx
                                        val atStart = currentIndex == 0 && dragOffset.value + dx > 0
                                        val atEnd = currentIndex == count - 1 && dragOffset.value + dx < 0
                                        if (atStart || atEnd) {
                                            appliedDx *= OVERSCROLL_DAMPING
                                        }

                                        scope.launch {
                                            dragOffset.snapTo(dragOffset.value + appliedDx)
                                        }
                                    }
                                }
                            } finally {
                                userInteracting = false
                                lastInteractionTime = System.currentTimeMillis()
                            }

                            if (!horizontalClaimed) continue

                            val velocity = velocityTracker.calculateVelocity().x
                            val offset = dragOffset.value

                            scope.launch {
                                val swipedEnough = abs(offset) > cardWidthPx * SWIPE_THRESHOLD_FRACTION
                                val flung = abs(velocity) > velocityThresholdPx

                                when {
                                    (swipedEnough || flung) && offset < 0 && currentIndex < count - 1 -> {
                                        val target = currentIndex + 1
                                        val remaining = -cardWidthPx - offset
                                        dragOffset.animateTo(
                                            targetValue = -cardWidthPx,
                                            animationSpec = tween(SNAP_ANIM_MS, easing = FastOutLinearInEasing),
                                        )
                                        currentIndex = target
                                        dragOffset.snapTo(0f)
                                    }

                                    (swipedEnough || flung) && offset > 0 && currentIndex > 0 -> {
                                        val target = currentIndex - 1
                                        dragOffset.animateTo(
                                            targetValue = cardWidthPx,
                                            animationSpec = tween(SNAP_ANIM_MS, easing = FastOutLinearInEasing),
                                        )
                                        currentIndex = target
                                        dragOffset.snapTo(0f)
                                    }

                                    else -> snapBack()
                                }
                            }
                        }
                    }
                },
        ) {
            // Render stacked cards (back to front)
            val visibleRange = maxOf(0, currentIndex - 1)..minOf(count - 1, currentIndex + 2)
            for (i in visibleRange.reversed()) {
                val relativeIndex = i - currentIndex
                val normalizedOffset = dragOffset.value / cardWidthPx

                val effectiveRelative = relativeIndex - normalizedOffset
                val absEffective = effectiveRelative.coerceAtLeast(0f)

                val scale = (1f - absEffective * 0.08f).coerceIn(0.76f, 1f)
                val alpha = (1f - absEffective * 0.3f).coerceIn(0.1f, 1f)
                val xOffsetDp = absEffective * 16f
                val elevation = (8f - absEffective * 3f).coerceAtLeast(0f)

                val zOrder = (10f - absEffective).coerceAtLeast(0f)

                SmartCard(
                    recommendation = recommendations[i],
                    onClick = {
                        if (i == currentIndex) onCardClick(recommendations[i])
                    },
                    cardWidth = cardWidthDp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .zIndex(zOrder)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                            translationX = with(density) { xOffsetDp.dp.toPx() }
                        },
                    elevation = elevation.dp,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Indicator dots
        if (count > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(count) { index ->
                    val isActive = index == currentIndex
                    Box(
                        modifier = Modifier
                            .size(if (isActive) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (isActive) BlueAccent else IconGray.copy(alpha = 0.4f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false, radius = 12.dp),
                            ) {
                                if (index != currentIndex) {
                                    userInteracting = false
                                    lastInteractionTime = System.currentTimeMillis()
                                    val delta = (index - currentIndex).toFloat() * -cardWidthPx
                                    scope.launch {
                                        dragOffset.snapTo(delta)
                                        currentIndex = index
                                        dragOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = tween(
                                                SNAP_ANIM_MS,
                                                easing = FastOutLinearInEasing
                                            ),
                                        )
                                    }
                                }
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartCard(
    recommendation: SmartRecommendation,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    cardWidth: Dp = 270.dp,
    elevation: Dp = 4.dp,
) {
    val cardShape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .width(cardWidth)
            .height(146.dp)
            .clickable { onClick() },
    ) {
        // Blurred background effect
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(cardShape)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.4f),
                                Color.White.copy(alpha = 0f),
                            )
                        ),
                        cardShape,
                    )
                    .border(
                        3.5.dp,
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.66f),
                                Color.White.copy(alpha = 0f),
                            ),
                        ),
                        cardShape,
                    )
                    .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(42.dp) else Modifier)
            )
        }

        // Glass overlay
        Box(
            modifier = Modifier
                .matchParentSize()
                .shadow(elevation, cardShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.4f),
                            Color.White.copy(alpha = 0f),
                        )
                    ),
                    cardShape,
                )
                .border(1.dp, Color.White.copy(alpha = 0.3f), cardShape),
        )

        // Content
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .shadow(10.dp, CircleShape, spotColor = BlueAccent)
                    .background(SurfaceWhite, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.robot_mascot),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp, 36.dp),
                    contentScale = ContentScale.Fit,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = recommendation.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextBlack,
                    lineHeight = 20.sp,
                )
                Text(
                    text = recommendation.subtitle,
                    fontSize = 12.sp,
                    color = MutedText,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}
