package com.example.calllog.network

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
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

    private const val SOCKET_URL = "https://call-log-qaq7.onrender.com/"

    fun init(context: Context) {
        appContext = context.applicationContext
        setupSocket()
    }

    private fun setupSocket() {
        try {
            if (socket != null) {
                if (socket?.connected() == false) {
                    socket?.connect()
                }
                return
            }
            
            val opts = IO.Options().apply {
                forceNew = true
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 1000
                timeout = 20000
            }
            socket = IO.socket(SOCKET_URL, opts)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Socket Connected: id=${socket?.id()}")
                isConnected = true
                registerDevice()
            }?.on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "Socket Disconnected")
                isConnected = false
            }?.on("make-call") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as? JSONObject
                    val rawPhone = data?.optString("phoneNumber") ?: ""
                    Log.d(TAG, "make-call event received: $rawPhone")
                    if (rawPhone.isNotEmpty()) {
                        triggerSystemCall(rawPhone)
                    }
                }
            }

            socket?.connect()
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Socket syntax error", e)
        }
    }

    fun registerDevice() {
        val empId = SyncManager.userEmpId.trim()
        if (empId.isEmpty()) {
            Log.d(TAG, "Cannot register device: userEmpId is empty")
            return
        }

        if (socket == null) {
            setupSocket()
            return
        }

        if (socket?.connected() != true) {
            Log.d(TAG, "Socket not connected yet (isConnected=$isConnected). Triggering socket connect()...")
            socket?.connect()
            return
        }

        val data = JSONObject().apply {
            put("empId", empId)
            put("type", "mobile")
        }
        socket?.emit("register", data)
        Log.d(TAG, "Sent register event to backend for empId: '$empId' (socketId=${socket?.id()})")
    }

    private fun triggerSystemCall(phoneNumber: String) {
        val context = appContext ?: return
        
        // Sanitize phone number to keep digits and leading +
        val cleanNumber = phoneNumber.replace("[^0-9+]".toRegex(), "")
        if (cleanNumber.isEmpty()) {
            Log.e(TAG, "Cleaned phone number is empty")
            emitCallState(phoneNumber, "Failed")
            emitCallError("Invalid phone number format: $phoneNumber")
            return
        }

        // Check CALL_PHONE permission explicitly
        val permissionCheck = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "CALL_PHONE permission not granted")
            emitCallError("CALL_PHONE permission missing on mobile app. Please grant Phone Call permission in phone settings.")
            return
        }

        try {
            com.example.calllog.data.AppCallTracker.recordCall(context, cleanNumber, SyncManager.userEmpId)
            
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$cleanNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            emitCallState(cleanNumber, "Dialing")
        } catch (e: SecurityException) {
            Log.e(TAG, "CALL_PHONE permission error", e)
            emitCallError("SecurityException: CALL_PHONE permission denied on mobile")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start call intent", e)
            emitCallError("Failed to start call on phone: ${e.localizedMessage}")
        }
    }

    private fun emitCallError(message: String) {
        if (!isConnected || SyncManager.userEmpId.isEmpty()) return
        val data = JSONObject().apply {
            put("empId", SyncManager.userEmpId)
            put("message", message)
        }
        socket?.emit("call-error-from-mobile", data)
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

