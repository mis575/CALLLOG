package com.example.calllog.data

import kotlinx.serialization.Serializable

@Serializable
data class CallRecord(
    val id: String,
    val number: String,
    val name: String?,
    val type: Int, // Incoming, Outgoing, Missed, etc.
    val date: Long,
    val duration: Long,
    val description: String = "",
    val contactName: String = "",
    val companyName: String = "",
    val address: String = "",
    val status: String = "Select Status", // Interested, Not Interested, Prospect
    val email: String = "",
    val format: String = "Select Format",
    val isSaved: Boolean = false,
    val syncedBy: String? = null,
    val syncedByEmpId: String? = null,
    val enquiryReceived: String = "",
    val category: String = "",
    val followUpDate: String = ""
)

val CallRecord.effectiveDuration: Long
    get() {
        val statusLower = (status ?: "").lowercase().trim()
        val isUnconnected = statusLower == "busy" || statusLower == "not answering" || statusLower == "no answer" || statusLower == "missed" || statusLower == "rejected" || statusLower == "unconnected"
        return if (isUnconnected) 0L else duration
    }

@Serializable
data class MongoInsertRequest(
    val dataSource: String,
    val database: String,
    val collection: String,
    val documents: List<CallRecord>
)

@Serializable
data class GoogleSheetRequest(
    val action: String = "append",
    val data: List<CallRecord>
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class SyncRequest(
    val userName: String,
    val empId: String = "",
    val logs: List<CallRecord>
)

@Serializable
data class SyncResponse(
    val saved: Boolean = false,
    val sheetsSynced: Boolean = false
)

@Serializable
data class LoginResponse(
    val success: Boolean,
    val message: String,
    val userName: String? = null,
    val role: String? = null,
    val empId: String? = null,
    val category: String? = null
)

@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val phone: String,
    val empId: String,
    val password: String,
    val role: String = "Executive",
    val reportsTo: String = "",
    val category: String = ""
)

@Serializable
data class TeamMember(
    val empId: String,
    val name: String,
    val role: String? = null, // Role can be null in DB
    val email: String = "",
    val phone: String = "",
    val reportsTo: String = "",
    val category: String = "",
    val isDeleted: Boolean = false
)

@Serializable
data class DeleteUserRequest(
    val empId: String
)

@Serializable
data class GetTeamLogsRequest(
    val targetEmpIds: List<String>,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val includeSubordinates: Boolean = false
)

@Serializable
data class GetLogsRequest(
    val ids: List<String>
)

@Serializable
data class UpdateHierarchyRequest(
    val empId: String,
    val role: String,
    val reportsTo: String
)

@Serializable
data class AssignedContact(
    val number: String,
    val name: String? = null,
    val assignedTo: String,
    val assignedToName: String,
    val assignedBy: String,
    val assignedByName: String,
    val assignedAt: Long,
    val status: String
)

@Serializable
data class LeadInfo(
    val number: String,
    val name: String = ""
)

@Serializable
data class ContactAssignmentRequest(
    val leads: List<LeadInfo> = emptyList(),
    val numbers: List<String> = emptyList(),
    val assignedTo: String,
    val assignedBy: String
)

@Serializable
data class AssignResponse(
    val success: Boolean,
    val message: String
)

@Serializable
data class VersionInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val updateUrl: String,
    val forceUpdate: Boolean = false
)
