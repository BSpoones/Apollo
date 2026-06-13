package com.beespoon.apollo
import java.io.File
import kotlin.test.*
import com.beespoon.apollo.util.NaturalSortComparator
class NaturalSortComparatorTest {
    private fun s(vararg n: String) = n.map { File(it) }.sortedWith(NaturalSortComparator).map { it.name }
    @Test fun numeric() { assertEquals(listOf("1.json", "2.json", "3.json", "10.json", "11.json"), s("10.json", "2.json", "1.json", "11.json", "3.json")) }
    @Test fun alphaCaseInsensitive() { assertEquals(listOf("a.json", "B.json"), s("B.json", "a.json")) }
    @Test fun mixedDigitsAndLetters() { assertEquals(listOf("file1.txt", "file2.txt", "file10.txt"), s("file10.txt", "file1.txt", "file2.txt")) }
    @Test fun identicalNames() { assertEquals(0, NaturalSortComparator.compare(File("test.json"), File("test.json"))) }
    @Test fun shorterSortsFirst() { assertTrue(NaturalSortComparator.compare(File(""), File("a")) < 0) }
    @Test fun leadingZeros() { assertEquals(listOf("01.json", "2.json", "10.json"), s("10.json", "01.json", "2.json")) }
    @Test fun overlongDigitRuns() {
        val small = "12345678901234567890.json"
        val mid = "12345678901234567899.json"
        val large = "99999999999999999999999.json"
        assertEquals(listOf(small, mid, large), s(large, mid, small))
    }
    @Test fun equalValueDifferentLeadingZeros() { assertEquals(listOf("1.json", "01.json"), s("01.json", "1.json")) }
    @Test fun equalNumberThenText() { assertEquals(listOf("1-alpha.json", "1-beta.json"), s("1-beta.json", "1-alpha.json")) }
}
