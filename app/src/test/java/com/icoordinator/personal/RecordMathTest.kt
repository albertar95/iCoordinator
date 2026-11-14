package com.icoordinator.personal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordMathTest {
    @Test fun percentNormal() = assertEquals(75, RecordMath.percent(45.0, 60.0))
    @Test fun percentFloors() = assertEquals(99, RecordMath.percent(99.9, 100.0))
    @Test fun percentNoFloatingError() = assertEquals(29, RecordMath.percent(29.0, 100.0))
    @Test fun percentOver100() = assertEquals(100, RecordMath.percent(80.0, 60.0))
    @Test fun percentZeroTarget() {
        assertEquals(0, RecordMath.percent(5.0, 0.0))
        assertEquals(0, RecordMath.percent(5.0, -3.0))
        assertEquals(0, RecordMath.percent(5.0, Double.NaN))
    }
    @Test fun percentNegativeCurrent() = assertEquals(0, RecordMath.percent(-5.0, 10.0))

    @Test fun parsePersian() = assertEquals(12.5, RecordMath.parse("۱۲٫۵")!!, 0.0)
    @Test fun parseArabicIndic() = assertEquals(12.0, RecordMath.parse("١٢")!!, 0.0)
    @Test fun parseLatinWithThousands() = assertEquals(1250.5, RecordMath.parse("1,250.5")!!, 0.0)
    @Test fun parsePersianThousands() = assertEquals(1250.0, RecordMath.parse("۱٬۲۵۰")!!, 0.0)
    @Test fun parseSlashDecimalAndTrim() = assertEquals(3.5, RecordMath.parse("  3/5 ")!!, 0.0)
    @Test fun parseNegative() = assertEquals(-4.0, RecordMath.parse("-4")!!, 0.0)
    @Test fun parseBlanksAndJunk() {
        assertNull(RecordMath.parse(""))
        assertNull(RecordMath.parse("   "))
        assertNull(RecordMath.parse("abc"))
        assertNull(RecordMath.parse("1.2.3"))
        assertNull(RecordMath.parse("12a"))
        assertNull(RecordMath.parse("."))
        assertNull(RecordMath.parse("-"))
        assertNull(RecordMath.parse("1-2"))
    }

    @Test fun formatThousandsDecimal() = assertEquals("۱٬۲۵۰٫۵", RecordMath.format(1250.5))
    @Test fun formatWhole() = assertEquals("۳", RecordMath.format(3.0))
    @Test fun formatRoundsHalfUp() = assertEquals("۲٫۳۵", RecordMath.format(2.345))
    @Test fun formatNegative() = assertEquals("-۴", RecordMath.format(-4.0))
    @Test fun formatLarge() = assertEquals("۱٬۲۳۴٬۵۶۷", RecordMath.format(1234567.0))
    @Test fun formatTinyNegativeIsZero() = assertEquals("۰", RecordMath.format(-0.001))

    @Test fun validateTargetMessages() {
        assertEquals("مقدار هدف را وارد کن", RecordMath.validateTarget(""))
        assertEquals("عدد معتبر نیست", RecordMath.validateTarget("abc"))
        assertEquals("هدف باید بیشتر از صفر باشد", RecordMath.validateTarget("۰"))
        assertEquals("هدف باید بیشتر از صفر باشد", RecordMath.validateTarget("-3"))
        assertNull(RecordMath.validateTarget("۱۰۰"))
    }

    @Test fun validateCurrentMessages() {
        assertEquals("مقدار فعلی را وارد کن", RecordMath.validateCurrent("  "))
        assertEquals("عدد معتبر نیست", RecordMath.validateCurrent("x"))
        assertEquals("مقدار نمی‌تواند منفی باشد", RecordMath.validateCurrent("-1"))
        assertNull(RecordMath.validateCurrent("۰"))
        assertNull(RecordMath.validateCurrent("12.5"))
    }
}
