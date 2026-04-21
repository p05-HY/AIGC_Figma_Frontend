package com.example.blueheartv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.R
import com.example.blueheartv.ui.theme.DarkPrimary

@Composable
fun AppTopBar(
    onMenuClick: () -> Unit,
    onAvatarClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_menu_hamburger),
            contentDescription = "Menu",
            modifier = Modifier
                .size(25.dp)
                .clickable { onMenuClick() },
        )

        Text(
            text = "超级蓝心小V",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = DarkPrimary,
            textAlign = TextAlign.Center,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.weight(1f),
        )

        Image(
            painter = painterResource(R.drawable.user_avatar),
            contentDescription = "Avatar",
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .clickable { onAvatarClick() },
            contentScale = ContentScale.Crop,
        )
    }
}
