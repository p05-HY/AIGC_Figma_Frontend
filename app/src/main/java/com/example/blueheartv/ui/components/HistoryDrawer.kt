package com.example.blueheartv.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.R
import com.example.blueheartv.model.ChatHistory
import com.example.blueheartv.ui.theme.*

@Composable
fun HistoryDrawer(
    isOpen: Boolean,
    histories: List<ChatHistory>,
    onClose: () -> Unit,
    onNewChat: () -> Unit = {},
    onHistoryClick: (ChatHistory) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it }),
        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it }),
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            // Dark overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OverlayBlack)
                    .clickable { onClose() },
            )

            // Drawer panel
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(319.dp),
                shape = RoundedCornerShape(topEnd = 40.dp, bottomEnd = 0.dp),
                color = SurfaceWhite,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = 24.dp),
                ) {
                    // Header row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 23.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Chat",
                            modifier = Modifier
                                .size(28.dp)
                                .clickable { onNewChat() },
                            tint = TextBlack,
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        Image(
                            painter = painterResource(R.drawable.user_avatar),
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title
                    Text(
                        text = "历史对话",
                        fontSize = 18.sp,
                        color = TextBlack,
                        letterSpacing = (-0.5).sp,
                        modifier = Modifier.padding(horizontal = 23.dp),
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(
                        modifier = Modifier.padding(horizontal = 0.dp),
                        thickness = 0.5.dp,
                        color = DividerColor,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // History list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 23.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(histories) { history ->
                            HistoryItem(
                                history = history,
                                onClick = { onHistoryClick(history) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    history: ChatHistory,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = CardBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = history.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = TextBlack,
                lineHeight = 20.sp,
            )
            Text(
                text = history.timestamp,
                fontSize = 12.sp,
                color = MutedText,
                lineHeight = 18.sp,
            )
        }
    }
}
