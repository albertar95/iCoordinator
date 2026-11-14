package com.icoordinator.personal

enum class BlockType(val code: String) {
    PARAGRAPH("p"), HEADING("h"), BULLET("b"), NUMBERED("n"), CHECK("c"), IMAGE("i");

    companion object {
        fun fromCode(code: String): BlockType = values().firstOrNull { it.code == code } ?: PARAGRAPH
    }
}

enum class SpanKind(val code: String) {
    BOLD("b"), ITALIC("i"), UNDERLINE("u"), HIGHLIGHT("h"), COLOR("c"), SIZE("s");

    companion object {
        fun fromCode(code: String): SpanKind? = values().firstOrNull { it.code == code }
    }
}

/** [start, end) character range of NoteBlock.text. value: palette index for HIGHLIGHT (0..3) and COLOR (0..5); size step for SIZE (-1 small, 1 large, 2 huge); 0 for the others. */
data class TextSpan(val start: Int, val end: Int, val kind: SpanKind, val value: Int = 0)

/** imageName: file name inside filesDir/note_images (IMAGE blocks only, text empty). */
data class NoteBlock(
    val id: Long,
    val type: BlockType,
    val text: String = "",
    val spans: List<TextSpan> = emptyList(),
    val checked: Boolean = false,
    val imageName: String? = null,
)

data class NoteDocument(val blocks: List<NoteBlock>) {
    companion object {
        fun empty(): NoteDocument = NoteDocument(listOf(NoteBlock(1L, BlockType.PARAGRAPH)))
    }
}

object NoteCodec {
    fun encode(doc: NoteDocument): String {
        val sb = StringBuilder()
        sb.append("{\"v\":1,\"blocks\":[")
        doc.blocks.forEachIndexed { i, b ->
            if (i > 0) sb.append(',')
            sb.append("{\"id\":").append(b.id)
            sb.append(",\"t\":"); writeString(sb, b.type.code)
            sb.append(",\"x\":"); writeString(sb, b.text)
            sb.append(",\"s\":[")
            b.spans.forEachIndexed { j, s ->
                if (j > 0) sb.append(',')
                sb.append("[\""); sb.append(s.kind.code); sb.append("\",")
                sb.append(s.start).append(',').append(s.end).append(',').append(s.value).append(']')
            }
            sb.append("],\"c\":").append(if (b.checked) "true" else "false")
            sb.append(",\"img\":")
            if (b.imageName == null) sb.append("null") else writeString(sb, b.imageName)
            sb.append('}')
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (ch in s) {
            when {
                ch == '"' -> sb.append("\\\"")
                ch == '\\' -> sb.append("\\\\")
                ch == '\n' -> sb.append("\\n")
                ch == '\r' -> sb.append("\\r")
                ch == '\t' -> sb.append("\\t")
                ch < ' ' -> {
                    sb.append("\\u")
                    val h = Integer.toHexString(ch.code)
                    for (k in h.length until 4) sb.append('0')
                    sb.append(h)
                }
                else -> sb.append(ch)
            }
        }
        sb.append('"')
    }

    fun decode(text: String?): NoteDocument {
        if (text == null || text.isBlank()) return NoteDocument.empty()
        val doc = try {
            Reader(text).readDocument()
        } catch (e: Exception) {
            null
        }
        if (doc != null) return doc
        return NoteDocument(listOf(NoteBlock(1L, BlockType.PARAGRAPH, text)))
    }

    private class ParseError : Exception()

    /** Small tolerant JSON reader producing Kotlin values: Map, List, String, Double/Long (as Number), Boolean, null. */
    private class Reader(private val src: String) {
        private var pos = 0

        fun readDocument(): NoteDocument? {
            val root = readValue()
            skipWs()
            if (pos != src.length) throw ParseError()
            if (root !is Map<*, *>) return null
            val blocksRaw = root["blocks"] as? List<*> ?: return null
            val blocks = ArrayList<NoteBlock>()
            // ids must be unique and > 0 (they are LazyColumn keys): the first block with an id keeps it,
            // a missing, non-positive or repeated id becomes (largest id kept so far) + 1
            val usedIds = HashSet<Long>()
            var maxId = 0L
            for (raw in blocksRaw) {
                val m = raw as? Map<*, *> ?: continue
                val rawId = (m["id"] as? Number)?.toLong()
                val id = if (rawId != null && rawId > 0 && !usedIds.contains(rawId)) rawId else maxId + 1
                usedIds.add(id)
                maxId = maxOf(maxId, id)
                val type = BlockType.fromCode(m["t"] as? String ?: "p")
                val x = m["x"] as? String ?: ""
                val spans = ArrayList<TextSpan>()
                (m["s"] as? List<*>)?.forEach { sr ->
                    val arr = sr as? List<*> ?: return@forEach
                    if (arr.size < 3) return@forEach
                    val kind = (arr[0] as? String)?.let { SpanKind.fromCode(it) } ?: return@forEach
                    val st = (arr[1] as? Number)?.toInt() ?: return@forEach
                    val en = (arr[2] as? Number)?.toInt() ?: return@forEach
                    val v = (arr.getOrNull(3) as? Number)?.toInt() ?: 0
                    spans.add(TextSpan(st, en, kind, v))
                }
                val checked = m["c"] as? Boolean ?: false
                val img = m["img"] as? String
                blocks.add(NoteBlock(id, type, x, SpanOps.normalize(spans, x.length), checked, img))
            }
            if (blocks.isEmpty()) return NoteDocument.empty()
            return NoteDocument(blocks)
        }

        private fun skipWs() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        private fun readValue(): Any? {
            skipWs()
            if (pos >= src.length) throw ParseError()
            return when (val c = src[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) readNumber() else throw ParseError()
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!src.startsWith(word, pos)) throw ParseError()
            pos += word.length
            return value
        }

        private fun readNumber(): Any {
            val start = pos
            if (src[pos] == '-') pos++
            while (pos < src.length && (src[pos].isDigit() || src[pos] in ".eE+-")) pos++
            val t = src.substring(start, pos)
            return t.toLongOrNull() ?: t.toDoubleOrNull() ?: throw ParseError()
        }

        private fun readString(): String {
            pos++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (pos >= src.length) throw ParseError()
                val c = src[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= src.length) throw ParseError()
                        when (src[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000c')
                            'u' -> {
                                if (pos + 4 > src.length) throw ParseError()
                                val code = src.substring(pos, pos + 4).toIntOrNull(16) ?: throw ParseError()
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> throw ParseError()
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun readArray(): List<Any?> {
            pos++
            val out = ArrayList<Any?>()
            skipWs()
            if (pos < src.length && src[pos] == ']') { pos++; return out }
            while (true) {
                out.add(readValue())
                skipWs()
                if (pos >= src.length) throw ParseError()
                when (src[pos++]) {
                    ',' -> {}
                    ']' -> return out
                    else -> throw ParseError()
                }
            }
        }

        private fun readObject(): Map<String, Any?> {
            pos++
            val out = LinkedHashMap<String, Any?>()
            skipWs()
            if (pos < src.length && src[pos] == '}') { pos++; return out }
            while (true) {
                skipWs()
                if (pos >= src.length || src[pos] != '"') throw ParseError()
                val key = readString()
                skipWs()
                if (pos >= src.length || src[pos] != ':') throw ParseError()
                pos++
                out[key] = readValue()
                skipWs()
                if (pos >= src.length) throw ParseError()
                when (src[pos++]) {
                    ',' -> {}
                    '}' -> return out
                    else -> throw ParseError()
                }
            }
        }
    }
}

object SpanOps {
    fun normalize(spans: List<TextSpan>, textLength: Int): List<TextSpan> {
        val clamped = spans.mapNotNull {
            val s = it.start.coerceIn(0, textLength)
            val e = it.end.coerceIn(0, textLength)
            if (e > s) TextSpan(s, e, it.kind, it.value) else null
        }
        val result = ArrayList<TextSpan>()
        val groups = clamped.groupBy { it.kind to it.value }
        for ((_, group) in groups) {
            var cur: TextSpan? = null
            for (sp in group.sortedBy { it.start }) {
                val c = cur
                if (c != null && sp.start <= c.end) {
                    cur = c.copy(end = maxOf(c.end, sp.end))
                } else {
                    if (c != null) result.add(c)
                    cur = sp
                }
            }
            if (cur != null) result.add(cur)
        }
        return result.sortedWith(compareBy({ it.start }, { it.kind.ordinal }, { it.value }, { it.end }))
    }

    fun applyEdit(oldText: String, newText: String, spans: List<TextSpan>): List<TextSpan> {
        if (oldText == newText) return normalize(spans, newText.length)
        val minLen = minOf(oldText.length, newText.length)
        var p = 0
        while (p < minLen && oldText[p] == newText[p]) p++
        var q = 0
        while (q < minLen - p && oldText[oldText.length - 1 - q] == newText[newText.length - 1 - q]) q++
        val delStart = p
        val delEnd = oldText.length - q
        val delLen = delEnd - delStart
        val n = newText.length - p - q
        val out = spans.mapNotNull { sp ->
            // deletion
            fun shift(pos: Int): Int = when {
                pos <= delStart -> pos
                pos >= delEnd -> pos - delLen
                else -> delStart
            }
            val s = shift(sp.start)
            val e = shift(sp.end)
            if (e <= s) return@mapNotNull null
            // insertion at p
            if (n <= 0) TextSpan(s, e, sp.kind, sp.value)
            else if (s < p && p <= e) TextSpan(s, e + n, sp.kind, sp.value)
            else if (s >= p) TextSpan(s + n, e + n, sp.kind, sp.value)
            else TextSpan(s, e, sp.kind, sp.value)
        }
        return normalize(out, newText.length)
    }

    fun isActive(spans: List<TextSpan>, start: Int, end: Int, kind: SpanKind): Boolean {
        if (start == end) {
            val pos = if (start == 0) 0 else start - 1
            return spans.any { it.kind == kind && it.start <= pos && pos < it.end }
        }
        return (start until end).all { i -> spans.any { it.kind == kind && it.start <= i && i < it.end } }
    }

    fun valueAt(spans: List<TextSpan>, start: Int, end: Int, kind: SpanKind): Int? {
        val range = if (start == end) listOf(if (start == 0) 0 else start - 1) else (start until end).toList()
        var value: Int? = null
        for (i in range) {
            val sp = spans.firstOrNull { it.kind == kind && it.start <= i && i < it.end } ?: return null
            if (value == null) value = sp.value else if (value != sp.value) return null
        }
        return value
    }

    /** Removes [kind] from [start, end), splitting spans that cross the range edges. */
    private fun clearKind(spans: List<TextSpan>, start: Int, end: Int, kind: SpanKind): List<TextSpan> {
        val out = ArrayList<TextSpan>()
        for (sp in spans) {
            if (sp.kind != kind || sp.end <= start || sp.start >= end) { out.add(sp); continue }
            if (sp.start < start) out.add(sp.copy(end = start))
            if (sp.end > end) out.add(sp.copy(start = end))
        }
        return out
    }

    fun toggle(spans: List<TextSpan>, textLength: Int, start: Int, end: Int, kind: SpanKind): List<TextSpan> {
        val s = start.coerceIn(0, textLength)
        val e = end.coerceIn(0, textLength)
        if (e <= s) return spans
        val result = if (isActive(spans, s, e, kind)) clearKind(spans, s, e, kind)
        else spans + TextSpan(s, e, kind, 0)
        return normalize(result, textLength)
    }

    fun setValue(spans: List<TextSpan>, textLength: Int, start: Int, end: Int, kind: SpanKind, value: Int?): List<TextSpan> {
        val s = start.coerceIn(0, textLength)
        val e = end.coerceIn(0, textLength)
        if (e <= s) return spans
        val cleared = clearKind(spans, s, e, kind)
        val result = if (value == null) cleared else cleared + TextSpan(s, e, kind, value)
        return normalize(result, textLength)
    }
}

object NoteBlocks {
    fun nextId(doc: NoteDocument): Long = (doc.blocks.maxOfOrNull { it.id } ?: 0L) + 1L

    fun split(block: NoteBlock, cursor: Int, newId: Long): Pair<NoteBlock, NoteBlock> {
        val c = cursor.coerceIn(0, block.text.length)
        val firstSpans = ArrayList<TextSpan>()
        val secondSpans = ArrayList<TextSpan>()
        for (sp in block.spans) {
            if (sp.start < c) firstSpans.add(sp.copy(end = minOf(sp.end, c)))
            if (sp.end > c) secondSpans.add(sp.copy(start = maxOf(sp.start, c) - c, end = sp.end - c))
        }
        val first = block.copy(text = block.text.substring(0, c), spans = SpanOps.normalize(firstSpans, c))
        val secondText = block.text.substring(c)
        val secondType = if (block.type == BlockType.HEADING) BlockType.PARAGRAPH else block.type
        val second = NoteBlock(
            id = newId,
            type = secondType,
            text = secondText,
            spans = SpanOps.normalize(secondSpans, secondText.length),
            checked = false,
            imageName = null,
        )
        return first to second
    }

    fun mergeIntoPrevious(previous: NoteBlock, block: NoteBlock): NoteBlock {
        if (previous.type == BlockType.IMAGE) return previous
        val offset = previous.text.length
        val shifted = block.spans.map { it.copy(start = it.start + offset, end = it.end + offset) }
        val text = previous.text + block.text
        return previous.copy(text = text, spans = SpanOps.normalize(previous.spans + shifted, text.length))
    }

    fun numberFor(blocks: List<NoteBlock>, index: Int): Int {
        if (index !in blocks.indices || blocks[index].type != BlockType.NUMBERED) return 0
        var i = index
        var n = 1
        while (i > 0 && blocks[i - 1].type == BlockType.NUMBERED) { i--; n++ }
        return n
    }

    fun imageNames(doc: NoteDocument): Set<String> =
        doc.blocks.mapNotNull { if (it.type == BlockType.IMAGE) it.imageName else null }.toSet()
}

object NoteText {
    fun plainText(doc: NoteDocument): String =
        doc.blocks.filter { it.type != BlockType.IMAGE }.joinToString("\n") {
            when {
                it.type != BlockType.CHECK -> it.text
                it.checked -> "☑ " + it.text
                else -> "☐ " + it.text
            }
        }

    fun preview(doc: NoteDocument, maxChars: Int = 120): String {
        val t = plainText(doc).replace("\r", "").replace('\n', ' ').trim()
        return if (t.length <= maxChars) t else t.substring(0, maxChars).trimEnd() + "…"
    }

    private fun norm(s: String): String = s.replace('ي', 'ی').replace('ك', 'ک').lowercase()

    fun matches(title: String, plain: String, query: String): Boolean {
        val q = norm(query.trim())
        if (q.isEmpty()) return true
        return norm(title).contains(q) || norm(plain).contains(q)
    }
}
