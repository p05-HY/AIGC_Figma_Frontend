package com.example.blueheartv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.example.blueheartv.ui.theme.Slate700Stroke
import com.example.blueheartv.ui.theme.SurfaceWhite
import com.example.blueheartv.ui.theme.TextBlack
import com.example.blueheartv.ui.theme.MutedText

@Composable
fun SmartCardRow(
    recommendations: List<SmartRecommendation>,
    modifier: Modifier = Modifier,
    onCardClick: (SmartRecommendation) -> Unit = {},
) {
    if (recommendations.isEmpty()) return

    val cards = recommendations.take(3)
    val backToFront = cards.asReversed()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(360.dp),
    ) {
        backToFront.forEachIndexed { drawIndex, recommendation ->
            val sourceIndex = cards.lastIndex - drawIndex
            val startOffset = when (sourceIndex) {
                0 -> 23.dp
                1 -> 69.87.dp
                else -> 107.56.dp
            }
            val topOffset = when (sourceIndex) {
                0 -> 88.dp
                1 -> 72.dp
                else -> 56.dp
            }
            val cardAlpha = when (sourceIndex) {
                0 -> 1f
                1 -> 0.92f
                else -> 0.85f
            }

            SmartCard(
                recommendation = recommendation,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = startOffset, y = topOffset)
                    .alpha(cardAlpha)
                    .zIndex(drawIndex.toFloat()),
                onClick = if (sourceIndex == 0) {
                    { onCardClick(recommendation) }
                } else {
                    {}
                },
                withFaceImage = sourceIndex == 0,
                contentAlpha = if (sourceIndex == 0) 1f else 0.85f,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 386.dp, top = 246.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == 1) 6.dp else 4.dp)
                        .clip(CircleShape)
                        .background(if (index == 1) BlueAccent else IconGray.copy(alpha = 0.35f)),
                )
            }
        }
    }
}

@Composable
private fun SmartCard(
    recommendation: SmartRecommendation,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    withFaceImage: Boolean = false,
    contentAlpha: Float = 1f,
) {
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .size(width = 269.dp, height = 146.dp)
            .clip(shape)
            .clickable { onClick() },
    ) {
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
