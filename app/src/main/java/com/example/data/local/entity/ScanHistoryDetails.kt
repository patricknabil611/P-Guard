package com.example.data.local.entity

import org.json.JSONArray
import org.json.JSONObject

data class HistoryChecklistEntry(
    val id: String,
    val title: String,
    val status: String,
    val explanation: String
)

data class HistorySuspiciousEntry(
    val appName: String,
    val packageName: String,
    val riskLevel: String,
    val reasons: List<String>
)

data class HistoryAppEntry(
    val appName: String,
    val packageName: String,
    val detail: String = ""
)

data class HistoryPermissionChangeEntry(
    val appName: String,
    val packageName: String,
    val addedPermissions: List<String>,
    val hasRiskyPermission: Boolean
)

data class ScanHistoryDetails(
    val developerOptionsEnabled: Boolean = false,
    val usbDebuggingEnabled: Boolean = false,
    val isRooted: Boolean = false,
    val rootTriggers: List<String> = emptyList(),
    val checklist: List<HistoryChecklistEntry> = emptyList(),
    val suspiciousApps: List<HistorySuspiciousEntry> = emptyList(),
    val newlySuspiciousApps: List<HistoryAppEntry> = emptyList(),
    val signatureChangedApps: List<HistoryAppEntry> = emptyList(),
    val hiddenApps: List<HistoryAppEntry> = emptyList(),
    val newApps: List<HistoryAppEntry> = emptyList(),
    val permissionChanges: List<HistoryPermissionChangeEntry> = emptyList()
) {
    fun toJson(): String {
        val root = JSONObject()
        root.put("developerOptionsEnabled", developerOptionsEnabled)
        root.put("usbDebuggingEnabled", usbDebuggingEnabled)
        root.put("isRooted", isRooted)
        root.put("rootTriggers", JSONArray(rootTriggers))

        root.put(
            "checklist",
            JSONArray().apply {
                checklist.forEach { item ->
                    put(
                        JSONObject().apply {
                            put("id", item.id)
                            put("title", item.title)
                            put("status", item.status)
                            put("explanation", item.explanation)
                        }
                    )
                }
            }
        )

        root.put(
            "suspiciousApps",
            JSONArray().apply {
                suspiciousApps.forEach { item ->
                    put(
                        JSONObject().apply {
                            put("appName", item.appName)
                            put("packageName", item.packageName)
                            put("riskLevel", item.riskLevel)
                            put("reasons", JSONArray(item.reasons))
                        }
                    )
                }
            }
        )

        root.put("newlySuspiciousApps", encodeAppList(newlySuspiciousApps))
        root.put("signatureChangedApps", encodeAppList(signatureChangedApps))
        root.put("hiddenApps", encodeAppList(hiddenApps))
        root.put("newApps", encodeAppList(newApps))

        root.put(
            "permissionChanges",
            JSONArray().apply {
                permissionChanges.forEach { item ->
                    put(
                        JSONObject().apply {
                            put("appName", item.appName)
                            put("packageName", item.packageName)
                            put("addedPermissions", JSONArray(item.addedPermissions))
                            put("hasRiskyPermission", item.hasRiskyPermission)
                        }
                    )
                }
            }
        )

        return root.toString()
    }

    companion object {
        fun fromJson(raw: String): ScanHistoryDetails {
            if (raw.isBlank()) return ScanHistoryDetails()
            return try {
                val root = JSONObject(raw)
                ScanHistoryDetails(
                    developerOptionsEnabled = root.optBoolean("developerOptionsEnabled", false),
                    usbDebuggingEnabled = root.optBoolean("usbDebuggingEnabled", false),
                    isRooted = root.optBoolean("isRooted", false),
                    rootTriggers = decodeStringArray(root.optJSONArray("rootTriggers")),
                    checklist = decodeChecklist(root.optJSONArray("checklist")),
                    suspiciousApps = decodeSuspicious(root.optJSONArray("suspiciousApps")),
                    newlySuspiciousApps = decodeAppList(root.optJSONArray("newlySuspiciousApps")),
                    signatureChangedApps = decodeAppList(root.optJSONArray("signatureChangedApps")),
                    hiddenApps = decodeAppList(root.optJSONArray("hiddenApps")),
                    newApps = decodeAppList(root.optJSONArray("newApps")),
                    permissionChanges = decodePermissionChanges(root.optJSONArray("permissionChanges"))
                )
            } catch (_: Exception) {
                ScanHistoryDetails()
            }
        }

        fun buildSummaryText(
            newAppsCount: Int,
            signatureChangesCount: Int,
            newSuspiciousCount: Int,
            permissionChangesCount: Int,
            devModeTurnedOn: Boolean
        ): String {
            val parts = buildList {
                if (signatureChangesCount == 1) add("1 signature change")
                else if (signatureChangesCount > 1) add("$signatureChangesCount signature changes")

                if (newAppsCount == 1) add("1 new app")
                else if (newAppsCount > 1) add("$newAppsCount new apps")

                if (newSuspiciousCount == 1) add("1 new suspicious app")
                else if (newSuspiciousCount > 1) add("$newSuspiciousCount new suspicious apps")

                if (permissionChangesCount == 1) add("1 permission change")
                else if (permissionChangesCount > 1) add("$permissionChangesCount permission changes")

                if (devModeTurnedOn) add("Developer settings enabled")
            }
            return parts.joinToString(", ")
        }

        private fun encodeAppList(list: List<HistoryAppEntry>): JSONArray = JSONArray().apply {
            list.forEach { item ->
                put(
                    JSONObject().apply {
                        put("appName", item.appName)
                        put("packageName", item.packageName)
                        put("detail", item.detail)
                    }
                )
            }
        }

        private fun decodeStringArray(arr: JSONArray?): List<String> {
            if (arr == null) return emptyList()
            return List(arr.length()) { idx -> arr.optString(idx, "") }.filter { it.isNotEmpty() }
        }

        private fun decodeAppList(arr: JSONArray?): List<HistoryAppEntry> {
            if (arr == null) return emptyList()
            return List(arr.length()) { idx ->
                val obj = arr.optJSONObject(idx) ?: return@List null
                HistoryAppEntry(
                    appName = obj.optString("appName", ""),
                    packageName = obj.optString("packageName", ""),
                    detail = obj.optString("detail", "")
                )
            }.filterNotNull()
        }

        private fun decodeChecklist(arr: JSONArray?): List<HistoryChecklistEntry> {
            if (arr == null) return emptyList()
            return List(arr.length()) { idx ->
                val obj = arr.optJSONObject(idx) ?: return@List null
                HistoryChecklistEntry(
                    id = obj.optString("id", ""),
                    title = obj.optString("title", ""),
                    status = obj.optString("status", "PASS"),
                    explanation = obj.optString("explanation", "")
                )
            }.filterNotNull()
        }

        private fun decodeSuspicious(arr: JSONArray?): List<HistorySuspiciousEntry> {
            if (arr == null) return emptyList()
            return List(arr.length()) { idx ->
                val obj = arr.optJSONObject(idx) ?: return@List null
                HistorySuspiciousEntry(
                    appName = obj.optString("appName", ""),
                    packageName = obj.optString("packageName", ""),
                    riskLevel = obj.optString("riskLevel", "LOW"),
                    reasons = decodeStringArray(obj.optJSONArray("reasons"))
                )
            }.filterNotNull()
        }

        private fun decodePermissionChanges(arr: JSONArray?): List<HistoryPermissionChangeEntry> {
            if (arr == null) return emptyList()
            return List(arr.length()) { idx ->
                val obj = arr.optJSONObject(idx) ?: return@List null
                HistoryPermissionChangeEntry(
                    appName = obj.optString("appName", ""),
                    packageName = obj.optString("packageName", ""),
                    addedPermissions = decodeStringArray(obj.optJSONArray("addedPermissions")),
                    hasRiskyPermission = obj.optBoolean("hasRiskyPermission", false)
                )
            }.filterNotNull()
        }
    }
}
