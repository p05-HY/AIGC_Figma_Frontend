package com.example.blueheartv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blueheartv.R
import com.example.blueheartv.ui.theme.*

@Composable
fun BottomInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    sendEnabled: Boolean = true,
    onAttachClick: () -> Unit = {},
    onMicClick: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDividerLine()

        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 13.dp)
                    .fillMaxWidth()
                    .height(41.25.dp)
                    .shadow(2.dp, RoundedCornerShape(16.dp))
                    .background(SurfaceWhite, RoundedCornerShape(16.dp))
                    .border(0.5.dp, StrokeMuted, RoundedCornerShape(16.dp)),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_attachment),
                    contentDescription = "Attach file",
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 13.dp)
                        .size(19.dp)
                        .clickable { onAttachClick() },
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 44.dp, end = 80.dp)
                        .fillMaxWidth(),
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = "发消息...",
                            fontSize = 14.sp,
                            color = GrayText,
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = { if (it.length <= 2000) onValueChange(it) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = TextBlack,
                        ),
                        singleLine = true,
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_mic),
                        contentDescription = "Voice input",
                        modifier = Modifier
                            .size(width = 9.dp, height = 13.dp)
                            .clickable { onMicClick() },
                    )
                    Image(
                        painter = painterResource(R.drawable.ic_send_arrow),
                        contentDescription = "Send",
                        modifier = Modifier
                            .size(15.dp)
                            .clickable { if (sendEnabled) onSend() },
                    )
                }
            }
        }
    }
}

@Composable
private fun IconButton24(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 20.dp),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun HorizontalDividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(DividerColor)
    )
}
