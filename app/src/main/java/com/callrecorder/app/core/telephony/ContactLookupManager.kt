package com.callrecorder.app.core.telephony

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

/**
 * Result of querying device contacts.
 */
data class ContactInfo(
    val displayName: String,
    val photoUri: String?,
    val isKnown: Boolean
)

/**
 * Resolves phone numbers against the device phonebook (ContactsContract).
 * Differentiates known saved contacts from unknown callers.
 */
class ContactLookupManager(private val context: Context) {

    /**
     * Looks up a phone number in the device contacts.
     */
    fun lookupContact(phoneNumber: String): ContactInfo {
        val trimmed = phoneNumber.trim()
        if (trimmed.isBlank() || trimmed.equals("Unknown Number", ignoreCase = true) || trimmed.equals("Unknown", ignoreCase = true)) {
            return ContactInfo(
                displayName = "Private Number",
                photoUri = null,
                isKnown = false
            )
        }

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(trimmed)
        )

        val projection = arrayOf(
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
        )

        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    val photoIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)

                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    val photo = if (photoIndex >= 0) cursor.getString(photoIndex) else null

                    if (!name.isNullOrBlank()) {
                        return ContactInfo(
                            displayName = name,
                            photoUri = photo,
                            isKnown = true
                        )
                    }
                }
            }
        } catch (_: SecurityException) {
            // Permission denied or restricted
        } catch (_: Exception) {
            // Fallback for query error
        }

        return ContactInfo(
            displayName = formatPhoneNumber(trimmed),
            photoUri = null,
            isKnown = false
        )
    }

    /**
     * Normalizes and cleans phone numbers for consistent display.
     */
    fun formatPhoneNumber(rawNumber: String): String {
        val cleaned = rawNumber.trim()
        if (cleaned.isBlank() || cleaned.equals("Unknown Number", ignoreCase = true) || cleaned.equals("Unknown", ignoreCase = true)) {
            return "Private Number"
        }
        return cleaned
    }
}
