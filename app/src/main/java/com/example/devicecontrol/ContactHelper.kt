package com.example.devicecontrol

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

object ContactHelper {

    fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Resolves a contact name or partial name into a usable phone number.
     * If the input is already a phone number (e.g. "+1234567890" or "01712345678"), returns formatted phone.
     */
    fun resolvePhoneNumber(context: Context, query: String): String? {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return null

        // Check if query is already predominantly digits / phone format
        val digitsOnly = trimmed.filter { it.isDigit() || it == '+' }
        if (digitsOnly.length >= 7 && (digitsOnly.length.toDouble() / trimmed.length) > 0.7) {
            return digitsOnly
        }

        // Query system Contacts if permission is granted
        if (!hasContactsPermission(context)) {
            return null
        }

        return try {
            val contentResolver = context.contentResolver
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$trimmed%")

            val cursor: Cursor? = contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.TIMES_CONTACTED} DESC"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (numIdx >= 0) {
                        val rawNumber = it.getString(numIdx)
                        return@use rawNumber?.filter { ch -> ch.isDigit() || ch == '+' }
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
