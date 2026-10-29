package com.harikrishnarao.financetracker.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat

data class RawSms(val id: Long, val address: String?, val body: String, val date: Long)

/** Where SMS come from. The phone inbox in the app, a fake list in tests. */
interface SmsSource {
    fun hasPermission(): Boolean
    /** Messages received after [sinceMillis], oldest first. */
    fun readInbox(sinceMillis: Long): List<RawSms>
}

/** Reads the phone's SMS inbox. Only called after the user grants READ_SMS. */
class SmsReader(private val context: Context) : SmsSource {

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    override fun readInbox(sinceMillis: Long): List<RawSms> {
        if (!hasPermission()) return emptyList()
        val out = ArrayList<RawSms>()
        val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} > ?",
            arrayOf(sinceMillis.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(Telephony.Sms._ID)
            val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val body = c.getString(iBody) ?: continue
                out += RawSms(c.getLong(iId), c.getString(iAddr), body, c.getLong(iDate))
            }
        }
        return out
    }
}
