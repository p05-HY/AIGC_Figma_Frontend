package com.example.blueheartv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
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
import com.example.blueheartv.ui.theme.DarkPrimary
import com.example.blueheartv.ui.theme.TextBlack

@Composable
fun AppTopBar(
    onMenuClick: () -> Unit,
    onAddClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(48.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
                .size(25.dp)
                .clip(CircleShape)
                .clickable { onMenuClick() },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_menu_hamburger),
                contentDescription = "Menu",
                modifier = Modifier.size(25.dp),
            )
        }

        Text(
            text = "Echo",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = DarkPrimary,
            modifier = Modifier.align(Alignment.Center),
        )

        Image(
            painter = painterResource(R.drawable.ic_avatar_brand),
            contentDescription = "New Chat",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .size(26.dp)
                .clip(CircleShape)
                .clickable { onAddClick() },
            contentScale = ContentScale.Crop,
        )
    }
}
