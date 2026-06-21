package com.example.blueheartv.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun parseMarkdownBlocks_recognizesRichBlocksAndTableFallback() {
        val markdown = """
            # 标题
            普通段落包含 **加粗**、*斜体*、`code` 和 [链接](https://example.com)。
            > 引用内容
            - 第一项
            - 第二项
            1. 第一步
            2. 第二步
            | 名称 | 状态 |
            | --- | --- |
            | Echo | OK |
            ```kotlin
            val name = "Echo"
            ```
        """.trimIndent()

        val blocks = parseMarkdownBlocks(markdown)

        assertTrue(blocks.any { it is MarkdownBlock.Heading && it.level == 1 && it.text == "标题" })
        assertTrue(blocks.any { it is MarkdownBlock.Quote && it.text == "引用内容" })
        assertTrue(blocks.any { it is MarkdownBlock.BulletList && it.items == listOf("第一项", "第二项") })
        assertTrue(blocks.any { it is MarkdownBlock.OrderedList && it.items == listOf("第一步", "第二步") })
        assertTrue(blocks.any { it is MarkdownBlock.TableText && it.text.contains("| Echo | OK |") })
        assertTrue(blocks.any { it is MarkdownBlock.CodeBlock && it.language == "kotlin" && it.code.contains("val name") })

        val paragraph = blocks.filterIsInstance<MarkdownBlock.Paragraph>().first()
        assertEquals(
            listOf(
                MarkdownInline.Text("普通段落包含 "),
                MarkdownInline.Bold("加粗"),
                MarkdownInline.Text("、"),
                MarkdownInline.Italic("斜体"),
                MarkdownInline.Text("、"),
                MarkdownInline.Code("code"),
                MarkdownInline.Text(" 和 "),
                MarkdownInline.Link("链接", "https://example.com"),
                MarkdownInline.Text("。"),
            ),
            paragraph.inlines,
        )
    }
}
