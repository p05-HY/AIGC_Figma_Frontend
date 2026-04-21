package com.example.blueheartv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.R
import com.example.blueheartv.model.SmartRecommendation
import com.example.blueheartv.ui.theme.*

@Composable
fun SmartCardRow(
    recommendations: List<SmartRecommendation>,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 23.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(recommendations) { item ->
            SmartCard(item)
        }
    }
}

@Composable
private fun SmartCard(
    recommendation: SmartRecommendation,
) {
    val cardShape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .width(270.dp)
            .height(146.dp),
    ) {
        // Blurred background effect
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(cardShape)
        ) {
            // Dark radial blur background
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
                    .blur(42.dp)
            )
        }

        // Glass overlay card
        Box(
            modifier = Modifier
                .matchParentSize()
                .shadow(4.dp, cardShape)
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

        // Card content
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Robot avatar
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
