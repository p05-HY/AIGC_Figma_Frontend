package com.example.blueheartv.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.example.blueheartv.model.ChatAttachment
import java.util.*

internal fun Uri.toImageAttachment(context: Context): ChatAttachment? {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(this)?.takeIf { it.startsWith("image/") } ?: return null
    val bytes = resolver.openInputStream(this)?.use { it.readBytes() } ?: return null
    val displayName = resolver.query(this, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    } ?: lastPathSegment ?: "image"
    return ChatAttachment(
        id = UUID.randomUUID().toString(),
        displayName = displayName,
        mimeType = mimeType,
        base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP),
    )
}
