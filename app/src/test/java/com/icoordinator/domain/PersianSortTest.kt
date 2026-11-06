package com.icoordinator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersianSortTest {

    @Test
    fun sortsPersianAlphabetically() {
        val sorted = listOf("گل", "کار", "پروژه", "ب").sortedWith(PersianSort.comparator)
        assertEquals(listOf("ب", "پروژه", "کار", "گل"), sorted)
    }

    @Test
    fun alefWithMaddaComesBeforeAlef() {
        assertTrue(PersianSort.compare("آب", "اب") < 0)
        assertTrue(PersianSort.compare("اب", "آب") > 0)
    }

    @Test
    fun arabicKafEqualsPersianKaf() {
        assertEquals(0, PersianSort.compare("كتاب", "کتاب"))
    }

    @Test
    fun arabicYehEqualsPersianYeh() {
        assertEquals(0, PersianSort.compare("علي", "علی"))
        assertEquals(0, PersianSort.compare("موسى", "موسی"))
    }

    @Test
    fun persianDigitEqualsAsciiDigit() {
        assertEquals(0, PersianSort.compare("۲", "2"))
    }

    @Test
    fun digitsComeBeforeLetters() {
        assertTrue(PersianSort.compare("9", "آ") < 0)
    }

    @Test
    fun persianComesBeforeLatin() {
        assertFalse(PersianSort.compare("ab", "ب") < 0)
        assertTrue(PersianSort.compare("ب", "ab") < 0)
    }

    @Test
    fun latinIsCaseInsensitive() {
        assertEquals(0, PersianSort.compare("Abc", "aBC"))
    }

    @Test
    fun shorterPrefixComesFirst() {
        assertTrue(PersianSort.compare("کار", "کارگر") < 0)
        assertTrue(PersianSort.compare("کارگر", "کار") > 0)
    }

    @Test
    fun zeroWidthNonJoinerAndOuterSpacesAreIgnored() {
        assertEquals(0, PersianSort.compare("می‌روم", "میروم"))
        assertEquals(0, PersianSort.compare("  کار ", "کار"))
    }

    @Test
    fun hamzaFormsAndTehMarbutaAreNormalized() {
        assertEquals(0, PersianSort.compare("أمید", "امید"))
        assertEquals(0, PersianSort.compare("إمام", "امام"))
        assertEquals(0, PersianSort.compare("رحمة", "رحمه"))
    }

    @Test
    fun alphabetOrderOfSpecialLetters() {
        val sorted = listOf("ی", "گ", "چ", "پ", "ژ", "ک").sortedWith(PersianSort.comparator)
        assertEquals(listOf("پ", "چ", "ژ", "ک", "گ", "ی"), sorted)
    }

    @Test
    fun yehWithHamzaEqualsYeh() {
        assertEquals(0, PersianSort.compare("ئین", "یین"))
        assertTrue(PersianSort.compare("ئا", "ب") > 0)
    }

    @Test
    fun wawWithHamzaEqualsWaw() {
        assertEquals(0, PersianSort.compare("مؤمن", "مومن"))
    }

    @Test
    fun hehWithYehAboveEqualsHeh() {
        assertEquals(0, PersianSort.compare("خانۀ", "خانه"))
    }

    @Test
    fun arabicIndicDigitsEqualAsciiDigits() {
        assertEquals(0, PersianSort.compare("٣", "3"))
        assertEquals(0, PersianSort.compare("٠", "0"))
        assertEquals(0, PersianSort.compare("٩", "9"))
        assertTrue(PersianSort.compare("٢", "٣") < 0)
    }

    @Test
    fun standaloneHamzaIsIgnored() {
        assertEquals(0, PersianSort.compare("جزء", "جز"))
        assertEquals(0, PersianSort.compare("ءاب", "اب"))
    }

    @Test
    fun arabicDiacriticsAreIgnored() {
        assertEquals(0, PersianSort.compare("كَتَبَ", "کتب"))
        assertEquals(0, PersianSort.compare("عِلْم", "علم"))
        assertEquals(0, PersianSort.compare("\u064B\u0652", ""))
    }
}
