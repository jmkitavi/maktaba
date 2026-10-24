package com.maktaba.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

enum class LibraryReportReason(val wireValue: String, val label: String) {
    INAPPROPRIATE_NAME("inappropriate_name", "Inappropriate library name"),
    HARASSMENT("harassment", "Harassment or hateful language"),
    IMPERSONATION("impersonation", "Impersonation"),
    SPAM("spam", "Spam or misleading name"),
    OTHER("other", "Other concern")
}

internal fun reportValidationError(reason: LibraryReportReason?, details: String): String? = when {
    reason == null -> "Choose a reason for this report."
    details.length > 500 -> "Keep the details to 500 characters or fewer."
    else -> null
}

object LibraryReports {
    data class Target(val libraryName: String, val ownerDisplayName: String)

    suspend fun target(ownerUid: String): Target {
        require(ownerUid.isNotBlank() && '/' !in ownerUid) { "This library address is invalid." }
        val snapshot = FirebaseFirestore.getInstance().collection("sharedLibraries")
            .document(ownerUid).get(Source.SERVER).await()
        check(snapshot.exists() && snapshot.getBoolean("published") == true) {
            "This library is no longer shared."
        }
        val name = (snapshot.get("libraryName") as? String)?.takeIf(String::isNotBlank)
            ?: error("This shared library is missing its name.")
        val owner = (snapshot.get("ownerDisplayName") as? String)?.takeIf(String::isNotBlank)
            ?: error("This shared library is missing its reader name.")
        return Target(name, owner)
    }

    suspend fun submit(
        ownerUid: String,
        reason: LibraryReportReason,
        details: String,
        requestId: String
    ): String {
        requireNotNull(FirebaseSession.currentUser) { "Sign in before submitting a report." }
        require(reportValidationError(reason, details) == null) { "Keep the details to 500 characters or fewer." }
        val result = FirebaseFunctions.getInstance().getHttpsCallable("reportLibrary").call(
            mapOf(
                "ownerUid" to ownerUid, "reason" to reason.wireValue,
                "details" to details.trim(), "requestId" to requestId
            )
        ).await().getData() as? Map<*, *> ?: error("The report service returned an invalid response.")
        return (result["reportId"] as? String)?.takeIf(String::isNotBlank)
            ?: error("The report service did not return a receipt. Retry without changing this report.")
    }
}
