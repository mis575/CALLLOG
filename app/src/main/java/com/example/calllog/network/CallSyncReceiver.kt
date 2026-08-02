package com.example.calllog.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.example.calllog.data.CallLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CallSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SyncManager.init(context) // Ensure initialized for background sync
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            if (state == TelephonyManager.EXTRA_STATE_IDLE) {
                Log.d("CallSyncReceiver", "Call ended, preparing to sync latest log...")
                
                // Use a Coroutine to handle the async sync process
                val repository = CallLogRepository(context)
                CoroutineScope(Dispatchers.IO).launch {
                    // Reduced delay for faster feedback (5 seconds)
                    delay(5000)
                    
                    val calendar = java.util.Calendar.getInstance()
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    calendar.set(java.util.Calendar.MINUTE, 0)
                    calendar.set(java.util.Calendar.SECOND, 0)
                    calendar.set(java.util.Calendar.MILLISECOND, 0)
                    
                    val todaysLogs = repository.getFilteredCallLogs(SyncManager.userEmpId, calendar.timeInMillis, System.currentTimeMillis())
                    if (todaysLogs.isNotEmpty()) {
                        Log.d("CallSyncReceiver", "Syncing today's logs: ${todaysLogs.size}")
                        val success = SyncManager.syncLogs(todaysLogs)
                        if (success) {
                            Log.d("CallSyncReceiver", "Auto-sync successful")
                        } else {
                            Log.e("CallSyncReceiver", "Auto-sync failed")
                        }
                    } else {
                        Log.w("CallSyncReceiver", "No logs from today found")
                    }
                }
            }
        }
    }
}
