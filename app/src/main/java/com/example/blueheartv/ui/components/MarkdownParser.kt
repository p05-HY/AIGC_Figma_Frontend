package com.example.blueheartv.ui.components

sealed interface MarkdownBlock {
    data class Paragraph(val inlines: List<MarkdownInline>) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class BulletList(val items: List<String>) : MarkdownBlock
    data class OrderedList(val items: List<String>) : MarkdownBlock
    data class TableText(val text: String) : MarkdownBlock
    data class CodeBlock(
        val language: String,
        val code: String,
    ) : MarkdownBlock
}

sealed interface MarkdownInline {
    data class Text(val text: String) : MarkdownInline
    data class Bold(val text: String) : MarkdownInline
    data class Italic(val text: String) : MarkdownInline
    data class Code(val text: String) : MarkdownInline
    data class Link(val text: String, val url: String) : MarkdownInline
}

private val headingRegex = Regex("^(#{1,6})\\s+(.+)$")
private val orderedRegex = Regex("^\\d+[.)]\\s+(.+)$")

fun parseMarkdownBlocks(content: String): List<MarkdownBlock> {
    if (content.isBlank()) return emptyList()

    val blocks = mutableListOf<MarkdownBlock>()
    val lines = content.lines()
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        if (line.isBlank()) {
            index += 1
            continue
        }

        if (line.trimStart().startsWith("```")) {
            val language = line.trim().removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            index += 1
            while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                codeLines.add(lines[index])
                index += 1
            }
            if (index < lines.size) index += 1
            blocks.add(MarkdownBlock.CodeBlock(language = language, code = codeLines.joinToString("\n").trimEnd()))
            continue
        }

        val headingMatch = headingRegex.matchEntire(line.trim())
        if (headingMatch != null) {
            blocks.add(
                MarkdownBlock.Heading(
                    level = headingMatch.groupValues[1].length,
                    text = headingMatch.groupValues[2].trim(),
                ),
            )
            index += 1
            continue
        }

        if (line.trimStart().startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (index < lines.size && lines[index].trimStart().startsWith(">")) {
                quoteLines.add(lines[index].trimStart().removePrefix(">").trim())
                index += 1
            }
            blocks.add(MarkdownBlock.Quote(quoteLines.joinToString("\n")))
            continue
        }

        if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ")) {
            val items = mutableListOf<String>()
            while (index < lines.size) {
                val trimmed = lines[index].trimStart()
                if (!trimmed.startsWith("- ") && !trimmed.startsWith("* ")) break
                items.add(trimmed.drop(2).trim())
                index += 1
            }
            blocks.add(MarkdownBlock.BulletList(items))
            continue
        }

        if (orderedRegex.matches(line.trimStart())) {
            val items = mutableListOf<String>()
            while (index < lines.size) {
                val match = orderedRegex.matchEntire(lines[index].trimStart()) ?: break
                items.add(match.groupValues[1].trim())
                index += 1
            }
            blocks.add(MarkdownBlock.OrderedList(items))
            continue
        }

        if (line.trimStart().startsWith("|")) {
            val tableLines = mutableListOf<String>()
            while (index < lines.size && lines[index].trimStart().startsWith("|")) {
                tableLines.add(lines[index])
                index += 1
            }
            blocks.add(MarkdownBlock.TableText(tableLines.joinToString("\n")))
            continue
        }

        val paragraphLines = mutableListOf(line.trim())
        index += 1
        while (index < lines.size && lines[index].isNotBlank() && isParagraphContinuation(lines[index])) {
            paragraphLines.add(lines[index].trim())
            index += 1
        }
        blocks.add(MarkdownBlock.Paragraph(parseMarkdownInlines(paragraphLines.joinToString(" "))))
    }

    return blocks
}

private fun isParagraphContinuation(line: String): Boolean {
    val trimmed = line.trimStart()
    return !trimmed.startsWith("```") &&
        !trimmed.startsWith(">") &&
        !trimmed.startsWith("|") &&
        !trimmed.startsWith("- ") &&
        !trimmed.startsWith("* ") &&
        !orderedRegex.matches(trimmed) &&
        headingRegex.matchEntire(trimmed) == null
}

fun parseMarkdownInlines(text: String): List<MarkdownInline> {
    val result = mutableListOf<MarkdownInline>()
    var cursor = 0
    while (cursor < text.length) {
        val next = findNextInlineToken(text, cursor)
        if (next == null) {
            result.add(MarkdownInline.Text(text.substring(cursor)))
            break
        }
        if (next.start > cursor) {
            result.add(MarkdownInline.Text(text.substring(cursor, next.start)))
        }
        result.add(next.inline)
        cursor = next.end
    }
    return result.mergeAdjacentText()
}

private data class InlineToken(
    val start: Int,
    val end: Int,
    val inline: MarkdownInline,
)

private fun findNextInlineToken(text: String, from: Int): InlineToken? {
    val candidates = listOfNotNull(
        findDelimited(text, from, "**", "**") { MarkdownInline.Bold(it) },
        findDelimited(text, from, "`", "`") { MarkdownInline.Code(it) },
        findLink(text, from),
        findItalic(text, from),
    )
    return candidates.minByOrNull { it.start }
}

private fun findDelimited(
    text: String,
    from: Int,
    startToken: String,
    endToken: String,
    factory: (String) -> MarkdownInline,
): InlineToken? {
    val start = text.indexOf(startToken, from)
    if (start < 0) return null
    val contentStart = start + startToken.length
    val end = text.indexOf(endToken, contentStart)
    if (end < 0 || end == contentStart) return null
    return InlineToken(start, end + endToken.length, factory(text.substring(contentStart, end)))
}

private fun findItalic(text: String, from: Int): InlineToken? {
    var start = text.indexOf('*', from)
    while (start >= 0) {
        val isBoldToken = text.getOrNull(start + 1) == '*' || text.getOrNull(start - 1) == '*'
        if (!isBoldToken) {
            val end = text.indexOf('*', start + 1)
            if (end > start + 1 && text.getOrNull(end + 1) != '*') {
                return InlineToken(start, end + 1, MarkdownInline.Italic(text.substring(start + 1, end)))
            }
        }
        start = text.indexOf('*', start + 1)
    }
    return null
}

private fun findLink(text: String, from: Int): InlineToken? {
    val labelStart = text.indexOf('[', from)
    if (labelStart < 0) return null
    val labelEnd = text.indexOf("](", labelStart)
    if (labelEnd < 0) return null
    val urlStart = labelEnd + 2
    val urlEnd = text.indexOf(')', urlStart)
    if (urlEnd < 0) return null
    val label = text.substring(labelStart + 1, labelEnd)
    val url = text.substring(urlStart, urlEnd)
    if (label.isBlank() || !url.isWebUrl()) return null
    return InlineToken(labelStart, urlEnd + 1, MarkdownInline.Link(label, url))
}

private fun String.isWebUrl(): Boolean {
    return startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)
}

private fun List<MarkdownInline>.mergeAdjacentText(): List<MarkdownInline> {
    val merged = mutableListOf<MarkdownInline>()
    forEach { inline ->
        val previous = merged.lastOrNull()
        if (previous is MarkdownInline.Text && inline is MarkdownInline.Text) {
            merged[merged.lastIndex] = MarkdownInline.Text(previous.text + inline.text)
        } else {
            merged.add(inline)
        }
    }
    return merged.filterNot { it is MarkdownInline.Text && it.text.isEmpty() }
}
