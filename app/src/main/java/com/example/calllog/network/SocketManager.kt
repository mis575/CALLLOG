package com.example.calllog.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URISyntaxException

object SocketManager {
    private const val TAG = "SocketManager"
    private var socket: Socket? = null
    private var isConnected = false
    private var appContext: Context? = null
    private var lastState = TelephonyManager.EXTRA_STATE_IDLE

    // We can change this to match BACKEND_BASE_URL if it dynamically shifts,
    // or just connect to the constant.
    private const val SOCKET_URL = "https://call-log-qaq7.onrender.com/"

    fun init(context: Context) {
        appContext = context.applicationContext
        setupSocket()
    }

    private fun setupSocket() {
        try {
            if (socket != null) return
            
            val opts = IO.Options().apply {
                forceNew = true
                reconnection = true
            }
            socket = IO.socket(SOCKET_URL, opts)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Socket Connected")
                isConnected = true
                registerDevice()
            }?.on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "Socket Disconnected")
                isConnected = false
            }?.on("make-call") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as? JSONObject
                    val phoneNumber = data?.optString("phoneNumber") ?: ""
                    Log.d(TAG, "make-call event received: $phoneNumber")
                    if (phoneNumber.isNotEmpty()) {
                        triggerSystemCall(phoneNumber)
                    }
                }
            }

            socket?.connect()
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Socket syntax error", e)
        }
    }

    fun registerDevice() {
        if (!isConnected || SyncManager.userEmpId.isEmpty()) {
            Log.d(TAG, "Cannot register: Connected=$isConnected, EmpId=${SyncManager.userEmpId}")
            return
        }
        val data = JSONObject().apply {
            put("empId", SyncManager.userEmpId)
            put("type", "mobile")
        }
        socket?.emit("register", data)
        Log.d(TAG, "Sent register event to backend for empId: ${SyncManager.userEmpId}")
    }

    private fun triggerSystemCall(phoneNumber: String) {
        val context = appContext ?: return
        try {
            // Register call in AppCallTracker so that getFilteredCallLogs detects and syncs it
            com.example.calllog.data.AppCallTracker.recordCall(context, phoneNumber, SyncManager.userEmpId)
            
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            emitCallState(phoneNumber, "Dialing")
        } catch (e: SecurityException) {
            Log.e(TAG, "CALL_PHONE permission not granted", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start call intent", e)
        }
    }

    fun handlePhoneStateChange(state: String, incomingNumber: String? = null) {
        if (state == lastState) return
        lastState = state
        
        val status = when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> "Ringing"
            TelephonyManager.EXTRA_STATE_OFFHOOK -> "Active"
            TelephonyManager.EXTRA_STATE_IDLE -> "Completed"
            else -> "Unknown"
        }
        
        Log.d(TAG, "Phone state changed: $status")
        emitCallState(incomingNumber ?: "", status)
    }

    private fun emitCallState(phoneNumber: String, status: String) {
        if (!isConnected || SyncManager.userEmpId.isEmpty()) return
        
        val data = JSONObject().apply {
            put("empId", SyncManager.userEmpId)
            put("phoneNumber", phoneNumber)
            put("status", status)
        }
        socket?.emit("call-state-change", data)
        Log.d(TAG, "Emitted call-state-change: $status for $phoneNumber")
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
        isConnected = false
    }
}
