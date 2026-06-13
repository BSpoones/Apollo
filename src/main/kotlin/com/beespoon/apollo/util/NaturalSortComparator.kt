package com.beespoon.apollo.util

import java.io.File

internal object NaturalSortComparator : Comparator<File> {
    override fun compare(a: File, b: File): Int = compareNames(a.name, b.name)
    private fun compareNames(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            if (a[i].isDigit() && b[j].isDigit()) {
                val endA = endOfDigits(a, i)
                val endB = endOfDigits(b, j)
                val comparison = compareDigitRuns(a, i, endA, b, j, endB)
                if (comparison != 0) return comparison
                i = endA
                j = endB
            } else {
                val comparison = a[i].lowercaseChar().compareTo(b[j].lowercaseChar())
                if (comparison != 0) return comparison
                i++
                j++
            }
        }
        return a.length - b.length
    }

    private fun compareDigitRuns(a: String, startA: Int, endA: Int, b: String, startB: Int, endB: Int): Int {
        var i = startA
        var j = startB
        while (i < endA - 1 && a[i] == '0') i++
        while (j < endB - 1 && b[j] == '0') j++
        if (endA - i != endB - j) return (endA - i) - (endB - j)
        while (i < endA) {
            val comparison = a[i].compareTo(b[j])
            if (comparison != 0) return comparison
            i++
            j++
        }
        return 0
    }

    private fun endOfDigits(text: String, start: Int): Int {
        var end = start
        while (end < text.length && text[end].isDigit()) end++
        return end
    }
}
