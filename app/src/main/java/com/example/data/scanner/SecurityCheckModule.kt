package com.example.data.scanner

/**
 * Extensible registry of security scanner modules in P Guard.
 * Future phases can add implementations for each upcoming module ID and plug
 * their findings directly into the Dashboard summary and navigation graph.
 */
enum class SecurityCheckId {
    NEW_APPS,
    SUSPICIOUS_APPS,
    HIDDEN_APPS,
    RECENTLY_ACTIVE,
    SENSITIVE_ACCESS,
    DEVELOPER_MODE
}

data class UpcomingSecurityModule(
    val id: SecurityCheckId,
    val titleRes: Int,
    val descriptionRes: Int
)
