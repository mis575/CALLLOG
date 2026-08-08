package com.example.calllog.data

import android.content.Context
import android.provider.CallLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat


class CallLogRepository(private val context: Context) {

    suspend fun getCallLogs(): List<CallRecord> = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return@withContext emptyList()
        }
        val callLogs = mutableListOf<CallRecord>()
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            null,
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(CallLog.Calls._ID)
            val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
            val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
            val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
            val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)

            while (it.moveToNext()) {
                val id = it.getString(idIndex)
                val number = it.getString(numberIndex)
                val name = it.getString(nameIndex)
                val type = it.getInt(typeIndex)
                val date = it.getLong(dateIndex)
                val duration = it.getLong(durationIndex)

                callLogs.add(
                    CallRecord(
                        id = id,
                        number = number,
                        name = name,
                        type = type,
                        date = date,
                        duration = duration
                    )
                )
            }
        }
        callLogs
    }

    suspend fun getLatestCallLog(): CallRecord? = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return@withContext null
        }
        var latestCall: CallRecord? = null
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            null,
            null,
            null,
            "${CallLog.Calls.DATE} DESC LIMIT 1"
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val idIndex = it.getColumnIndex(CallLog.Calls._ID)
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)

                latestCall = CallRecord(
                    id = it.getString(idIndex),
                    number = it.getString(numberIndex),
                    name = it.getString(nameIndex),
                    type = it.getInt(typeIndex),
                    date = it.getLong(dateIndex),
                    duration = it.getLong(durationIndex)
                )
            }
        }
        latestCall
    }

    suspend fun getFilteredCallLogs(empId: String, startTime: Long? = null, endTime: Long? = null): List<CallRecord> = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return@withContext emptyList()
        }
        val callLogs = mutableListOf<CallRecord>()
        
        var selection: String? = null
        var selectionArgs: Array<String>? = null
        
        if (startTime != null && endTime != null) {
            selection = "${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.DATE} <= ?"
            selectionArgs = arrayOf(startTime.toString(), endTime.toString())
        } else if (startTime != null) {
            selection = "${CallLog.Calls.DATE} >= ?"
            selectionArgs = arrayOf(startTime.toString())
        }

        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            null,
            selection,
            selectionArgs,
            "${CallLog.Calls.DATE} DESC"
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(CallLog.Calls._ID)
            val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
            val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
            val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
            val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)

            while (it.moveToNext()) {
                val logNumber = it.getString(numberIndex)
                val logDate = it.getLong(dateIndex)
                
                // Only add if call was initiated via this app for this specific user
                if (AppCallTracker.isAppInitiated(context, logNumber, logDate, empId)) {
                    callLogs.add(
                        CallRecord(
                            id = it.getString(idIndex),
                            number = logNumber,
                            name = it.getString(nameIndex),
                            type = it.getInt(typeIndex),
                            date = logDate,
                            duration = it.getLong(durationIndex)
                        )
                    )
                }
            }
        }
        callLogs
    }
}
