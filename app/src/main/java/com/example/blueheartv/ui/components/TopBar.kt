package com.example.blueheartv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import com.example.blueheartv.R
import com.example.blueheartv.ui.theme.DarkPrimary
import com.example.blueheartv.ui.theme.TextBlack

@Composable
fun AppTopBar(
    onMenuClick: () -> Unit,
    onAddClick: () -> Unit = {},
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

        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "New Chat",
            modifier = Modifier
                .size(25.dp)
                .clickable { onAddClick() },
            tint = TextBlack,
        )
    }
}
