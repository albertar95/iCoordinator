package com.icoordinator.personal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteDocumentTest {
    private val B = SpanKind.BOLD

    private fun roundTrip(doc: NoteDocument) = assertEquals(doc, NoteCodec.decode(NoteCodec.encode(doc)))

    @Test fun roundTripPersianText() = roundTrip(NoteDocument(listOf(NoteBlock(1, BlockType.PARAGRAPH, "سلام دنیا ۱۲۳"))))

    @Test fun roundTripSpecialChars() = roundTrip(
        NoteDocument(listOf(NoteBlock(1, BlockType.PARAGRAPH, "he said \"hi\" \\ path\nline2\r\ttab\u0001 \u001f")))
    )

    @Test fun roundTripEmoji() = roundTrip(NoteDocument(listOf(NoteBlock(1, BlockType.PARAGRAPH, "ok 😀👍 end"))))

    @Test fun roundTripAllBlockTypesSpansCheckedImage() {
        val doc = NoteDocument(
            listOf(
                NoteBlock(1, BlockType.HEADING, "عنوان", listOf(TextSpan(0, 2, B))),
                NoteBlock(2, BlockType.BULLET, "الف"),
                NoteBlock(3, BlockType.NUMBERED, "ب", listOf(TextSpan(0, 1, SpanKind.COLOR, 3))),
                NoteBlock(4, BlockType.CHECK, "کار", checked = true),
                NoteBlock(5, BlockType.IMAGE, "", imageName = "img_1.jpg"),
                NoteBlock(6, BlockType.PARAGRAPH, "متن", listOf(TextSpan(0, 3, SpanKind.SIZE, -1), TextSpan(1, 2, SpanKind.HIGHLIGHT, 2))),
            )
        )
        roundTrip(doc)
    }

    @Test fun encodeShape() {
        val json = NoteCodec.encode(NoteDocument(listOf(NoteBlock(1, BlockType.PARAGRAPH, "text", listOf(TextSpan(0, 3, B))))))
        assertEquals("""{"v":1,"blocks":[{"id":1,"t":"p","x":"text","s":[["b",0,3,0]],"c":false,"img":null}]}""", json)
    }

    @Test fun decodeNullBlankGarbagePlain() {
        assertEquals(NoteDocument.empty(), NoteCodec.decode(null))
        assertEquals(NoteDocument.empty(), NoteCodec.decode("   "))
        val g = NoteCodec.decode("{not json")
        assertEquals(1, g.blocks.size)
        assertEquals("{not json", g.blocks[0].text)
        val plain = NoteCodec.decode("یادداشت ساده")
        assertEquals(BlockType.PARAGRAPH, plain.blocks[0].type)
        assertEquals("یادداشت ساده", plain.blocks[0].text)
    }

    @Test fun decodeIgnoresUnknownKeysAndKinds() {
        val d = NoteCodec.decode("""{"v":1,"zz":[1,2],"blocks":[{"id":7,"t":"zz","x":"ab","q":{"a":1},"s":[["b",0,1,0],["??",0,2,0]],"c":true,"img":null}]}""")
        val b = d.blocks.single()
        assertEquals(7L, b.id)
        assertEquals(BlockType.PARAGRAPH, b.type)
        assertEquals(listOf(TextSpan(0, 1, B)), b.spans)
        assertTrue(b.checked)
    }

    @Test fun decodeNormalizesSpansToTextLength() {
        val d = NoteCodec.decode("""{"v":1,"blocks":[{"id":1,"t":"p","x":"abc","s":[["b",-5,99,0]],"c":false,"img":null}]}""")
        assertEquals(listOf(TextSpan(0, 3, B)), d.blocks[0].spans)
    }

    @Test fun normalizeClampMergeDrop() {
        val r = SpanOps.normalize(
            listOf(TextSpan(0, 2, B), TextSpan(2, 4, B), TextSpan(3, 3, B), TextSpan(-2, 1, SpanKind.ITALIC), TextSpan(8, 9, B)),
            5,
        )
        assertEquals(listOf(TextSpan(0, 4, B), TextSpan(0, 1, SpanKind.ITALIC)), r)
    }

    @Test fun normalizeKeepsDifferentValuesSeparate() {
        val r = SpanOps.normalize(listOf(TextSpan(0, 2, SpanKind.COLOR, 1), TextSpan(2, 4, SpanKind.COLOR, 2)), 4)
        assertEquals(2, r.size)
    }

    @Test fun applyEditExamples() {
        assertEquals(listOf(TextSpan(0, 4, B)), SpanOps.applyEdit("abc", "abcd", listOf(TextSpan(0, 3, B))))
        assertEquals(listOf(TextSpan(1, 4, B)), SpanOps.applyEdit("abc", "xabc", listOf(TextSpan(0, 3, B))))
    }

    @Test fun applyEditInsideSpan() {
        assertEquals(listOf(TextSpan(0, 5, B)), SpanOps.applyEdit("abcd", "abXcd", listOf(TextSpan(0, 4, B))))
    }

    @Test fun applyEditDeleteAcrossBoundary() {
        // "abcdef", bold 2..4 ("cd"); delete "bcd" -> "aef"; bold vanishes
        assertEquals(emptyList<TextSpan>(), SpanOps.applyEdit("abcdef", "aef", listOf(TextSpan(2, 4, B))))
        // delete "de" (3..5): bold 2..4 shrinks to 2..3
        assertEquals(listOf(TextSpan(2, 3, B)), SpanOps.applyEdit("abcdef", "abcf", listOf(TextSpan(2, 4, B))))
        // delete before span shifts it left
        assertEquals(listOf(TextSpan(1, 3, B)), SpanOps.applyEdit("abcdef", "acdef", listOf(TextSpan(2, 4, B))))
    }

    @Test fun applyEditReplaceSelection() {
        assertEquals(listOf(TextSpan(0, 3, B)), SpanOps.applyEdit("abcd", "aXd", listOf(TextSpan(0, 4, B))))
    }

    @Test fun applyEditPasteInMiddle() {
        assertEquals(listOf(TextSpan(0, 8, B)), SpanOps.applyEdit("abcd", "abPASTcd", listOf(TextSpan(0, 4, B))))
    }

    @Test fun applyEditSpanAfterInsertionShifts() {
        assertEquals(listOf(TextSpan(4, 6, B)), SpanOps.applyEdit("abcd", "abXXcd", listOf(TextSpan(2, 4, B))))
    }

    @Test fun isActiveCollapsedAndRanged() {
        val s = listOf(TextSpan(2, 5, B))
        assertTrue(SpanOps.isActive(s, 5, 5, B))   // end of span
        assertTrue(SpanOps.isActive(s, 3, 3, B))
        assertFalse(SpanOps.isActive(s, 2, 2, B))  // before first char
        assertFalse(SpanOps.isActive(s, 6, 6, B))
        assertTrue(SpanOps.isActive(s, 2, 5, B))
        assertFalse(SpanOps.isActive(s, 1, 5, B))
        assertTrue(SpanOps.isActive(listOf(TextSpan(0, 2, B)), 0, 0, B))
    }

    @Test fun valueAtMixed() {
        val s = listOf(TextSpan(0, 2, SpanKind.COLOR, 1), TextSpan(2, 4, SpanKind.COLOR, 3))
        assertEquals(1, SpanOps.valueAt(s, 0, 2, SpanKind.COLOR))
        assertNull(SpanOps.valueAt(s, 0, 4, SpanKind.COLOR))
        assertNull(SpanOps.valueAt(s, 0, 5, SpanKind.COLOR))
        assertNull(SpanOps.valueAt(s, 0, 2, SpanKind.HIGHLIGHT))
    }

    @Test fun toggleAddPartialRemove() {
        val added = SpanOps.toggle(emptyList(), 10, 2, 6, B)
        assertEquals(listOf(TextSpan(2, 6, B)), added)
        // partially covered range -> add (extends)
        assertEquals(listOf(TextSpan(2, 8, B)), SpanOps.toggle(added, 10, 4, 8, B))
        // fully active -> remove middle, splits
        assertEquals(listOf(TextSpan(2, 3, B), TextSpan(5, 6, B)), SpanOps.toggle(added, 10, 3, 5, B))
        assertEquals(emptyList<TextSpan>(), SpanOps.toggle(added, 10, 2, 6, B))
        assertEquals(added, SpanOps.toggle(added, 10, 4, 4, B))
    }

    @Test fun setValueReplaceAndClear() {
        val s = listOf(TextSpan(0, 6, SpanKind.COLOR, 1))
        assertEquals(
            listOf(TextSpan(0, 2, SpanKind.COLOR, 1), TextSpan(2, 4, SpanKind.COLOR, 4), TextSpan(4, 6, SpanKind.COLOR, 1)),
            SpanOps.setValue(s, 6, 2, 4, SpanKind.COLOR, 4),
        )
        assertEquals(
            listOf(TextSpan(0, 2, SpanKind.COLOR, 1), TextSpan(4, 6, SpanKind.COLOR, 1)),
            SpanOps.setValue(s, 6, 2, 4, SpanKind.COLOR, null),
        )
    }

    @Test fun splitHeadingToParagraphWithSpans() {
        val b = NoteBlock(1, BlockType.HEADING, "abcdef", listOf(TextSpan(1, 5, B)))
        val (first, second) = NoteBlocks.split(b, 3, 2)
        assertEquals("abc", first.text)
        assertEquals(BlockType.HEADING, first.type)
        assertEquals(listOf(TextSpan(1, 3, B)), first.spans)
        assertEquals(BlockType.PARAGRAPH, second.type)
        assertEquals(2L, second.id)
        assertEquals("def", second.text)
        assertEquals(listOf(TextSpan(0, 2, B)), second.spans)
    }

    @Test fun splitCheckBecomesUnchecked() {
        val (first, second) = NoteBlocks.split(NoteBlock(1, BlockType.CHECK, "ab", checked = true), 1, 9)
        assertTrue(first.checked)
        assertEquals(BlockType.CHECK, second.type)
        assertFalse(second.checked)
    }

    @Test fun mergeIntoPreviousShiftsSpans() {
        val prev = NoteBlock(1, BlockType.PARAGRAPH, "ab", listOf(TextSpan(0, 2, B)))
        val cur = NoteBlock(2, BlockType.PARAGRAPH, "cd", listOf(TextSpan(0, 1, SpanKind.ITALIC)))
        val m = NoteBlocks.mergeIntoPrevious(prev, cur)
        assertEquals("abcd", m.text)
        assertEquals(1L, m.id)
        assertEquals(listOf(TextSpan(0, 2, B), TextSpan(2, 3, SpanKind.ITALIC)), m.spans)
    }

    @Test fun mergeIntoImagePreviousUnchanged() {
        val img = NoteBlock(1, BlockType.IMAGE, imageName = "a.jpg")
        assertEquals(img, NoteBlocks.mergeIntoPrevious(img, NoteBlock(2, BlockType.PARAGRAPH, "x")))
    }

    @Test fun numberForTwoRuns() {
        val bl = listOf(
            NoteBlock(1, BlockType.NUMBERED, "a"), NoteBlock(2, BlockType.NUMBERED, "b"),
            NoteBlock(3, BlockType.PARAGRAPH, "p"),
            NoteBlock(4, BlockType.NUMBERED, "c"), NoteBlock(5, BlockType.NUMBERED, "d"),
        )
        assertEquals(listOf(1, 2, 0, 1, 2), bl.indices.map { NoteBlocks.numberFor(bl, it) })
    }

    @Test fun nextIdAndImageNames() {
        assertEquals(1L, NoteBlocks.nextId(NoteDocument(emptyList())))
        val d = NoteDocument(listOf(NoteBlock(4, BlockType.IMAGE, imageName = "a.jpg"), NoteBlock(9, BlockType.IMAGE, imageName = "b.jpg"), NoteBlock(2, BlockType.PARAGRAPH)))
        assertEquals(10L, NoteBlocks.nextId(d))
        assertEquals(setOf("a.jpg", "b.jpg"), NoteBlocks.imageNames(d))
    }

    @Test fun plainTextCheckPrefixesAndImagesSkipped() {
        val d = NoteDocument(
            listOf(
                NoteBlock(1, BlockType.PARAGRAPH, "الف"),
                NoteBlock(2, BlockType.CHECK, "ب", checked = true),
                NoteBlock(3, BlockType.IMAGE, imageName = "x.jpg"),
                NoteBlock(4, BlockType.CHECK, "ج"),
            )
        )
        assertEquals("الف\n☑ ب\n☐ ج", NoteText.plainText(d))
    }

    @Test fun previewCut() {
        val d = NoteDocument(listOf(NoteBlock(1, BlockType.PARAGRAPH, "abcdef"), NoteBlock(2, BlockType.PARAGRAPH, "gh")))
        assertEquals("abcdef gh", NoteText.preview(d))
        assertEquals("abcd…", NoteText.preview(d, 4))
    }

    @Test fun matchesNormalizationCaseBlank() {
        assertTrue(NoteText.matches("", "کتاب", "كتاب"))
        assertTrue(NoteText.matches("", "علي", "علی"))
        assertTrue(NoteText.matches("Hello", "", "hello"))
        assertTrue(NoteText.matches("t", "p", "  "))
        assertFalse(NoteText.matches("abc", "def", "zzz"))
    }

    private fun ids(json: String) = NoteCodec.decode(json).blocks.map { it.id }

    private fun assertUniquePositive(ids: List<Long>) {
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it > 0 })
    }

    @Test fun decodeDuplicateIdsBecomeUnique() {
        val got = ids("""{"v":1,"blocks":[{"id":1,"x":"a"},{"id":1,"x":"b"},{"id":1,"x":"c"}]}""")
        assertEquals(listOf(1L, 2L, 3L), got)
        // a later valid id that collides with a reassigned one is reassigned too
        val got2 = ids("""{"v":1,"blocks":[{"id":1},{"id":1},{"id":2}]}""")
        assertUniquePositive(got2)
        assertEquals(1L, got2[0])
    }

    @Test fun decodeZeroAndNegativeIdsBecomePositive() {
        val got = ids("""{"v":1,"blocks":[{"id":0},{"id":-5},{"id":4},{"id":0}]}""")
        assertUniquePositive(got)
        assertEquals(listOf(1L, 2L, 4L, 5L), got)
    }

    @Test fun decodeMissingIdsGetUniqueIds() {
        val got = ids("""{"v":1,"blocks":[{"x":"a"},{"id":3},{"x":"b"},{"id":"zz"}]}""")
        assertUniquePositive(got)
        assertEquals(listOf(1L, 3L, 4L, 5L), got)
    }

    @Test fun decodeValidIdsAreKept() {
        assertEquals(listOf(5L, 2L, 9L), ids("""{"v":1,"blocks":[{"id":5},{"id":2},{"id":9}]}"""))
    }
}
