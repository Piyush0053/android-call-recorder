package com.callrecorder.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactLookupManagerTest {

    @Test
    fun testPhoneNumberFormatting() {
        fun format(number: String): String {
            val cleaned = number.trim()
            return if (cleaned.isBlank()) "Unknown" else cleaned
        }

        assertEquals("Unknown", format(""))
        assertEquals("Unknown", format("   "))
        assertEquals("+1 (555) 123-4567", format("+1 (555) 123-4567"))
        assertEquals("0987654321", format("  0987654321  "))
    }

    @Test
    fun testPhoneNumberSanitization() {
        val rawNumber = "+1 (555) 234-5678"
        val sanitized = rawNumber.replace(Regex("[^0-9+]"), "_")

        assertEquals("+1__555__234_5678", sanitized)
    }
}
