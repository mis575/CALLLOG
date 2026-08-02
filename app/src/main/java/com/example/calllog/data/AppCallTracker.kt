package com.example.calllog.data

import android.content.Context
import android.telephony.PhoneNumberUtils

object AppCallTracker {
    private const val PREFS_NAME = "AppCallTrackerPrefs"
    private const val CALL_WINDOW_MS = 60000 // 1 minute window

    fun recordCall(context: Context, number: String, empId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val timestamp = System.currentTimeMillis()
        // Save as "timestamp:number:empId"
        val callRecord = "$timestamp:$number:$empId"
        val currentCalls = prefs.getStringSet("calls", mutableSetOf()) ?: mutableSetOf()
        val updatedCalls = currentCalls.toMutableSet()
        updatedCalls.add(callRecord)
        
        // Clean up old records (older than 24 hours) to keep it light
        val dayAgo = System.currentTimeMillis() - 86400000
        val finalCalls = updatedCalls.filter { 
            it.split(":").firstOrNull()?.toLongOrNull() ?: 0 > dayAgo 
        }.toSet()

        prefs.edit().putStringSet("calls", finalCalls).apply()
    }

    fun isAppInitiated(context: Context, logNumber: String, logDate: Long, empId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val appCalls = prefs.getStringSet("calls", emptySet()) ?: emptySet()

        return appCalls.any { record ->
            val parts = record.split(":")
            if (parts.size >= 2) {
                val timestamp = parts[0].toLongOrNull() ?: 0
                val number = parts[1]
                val recordEmpId = parts.getOrNull(2)
                
                // Check if number matches, call was made within the window, and empId matches
                val numberMatches = PhoneNumberUtils.compare(number, logNumber)
                val timeMatches = Math.abs(timestamp - logDate) < CALL_WINDOW_MS
                val empIdMatches = recordEmpId.isNullOrEmpty() || recordEmpId == empId
                
                numberMatches && timeMatches && empIdMatches
            } else false
        }
    }
}
