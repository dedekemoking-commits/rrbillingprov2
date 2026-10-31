package com.rrbillingpro.monitor

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class TvLogDoc(
    val tvId: String = "",
    val app: String = "",
    val version: String = "",
    val errorCount: Int = 0,
    val totalLines: Int = 0,
    val lastReportMs: Long = 0L,
    val lines: List<String> = emptyList(),
)

data class MonitorCommand(
    val command: String = "",
    val commandId: String = "",
    val status: String = "",
    val result: String = "",
    val issuedAt: Long = 0L,
)

object MonitorRepository {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    suspend fun ensureAuth() {
        if (auth.currentUser != null) return
        suspendCancellableCoroutine<Unit> { cont ->
            auth.signInAnonymously()
                .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }
    }

    fun listenLogs(
        username: String,
        onUpdate: (List<TvLogDoc>) -> Unit,
        onError: (String) -> Unit,
    ): ListenerRegistration =
        db.collection("billingps_users").document(username)
            .collection("tvLogs")
            .orderBy("lastReportMs", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.message ?: "Gagal memuat log")
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents?.map { doc ->
                    TvLogDoc(
                        tvId = doc.id,
                        app = doc.getString("app") ?: "",
                        version = doc.getString("version") ?: "",
                        errorCount = (doc.getLong("errorCount") ?: 0L).toInt(),
                        totalLines = (doc.getLong("totalLines") ?: 0L).toInt(),
                        lastReportMs = doc.getLong("lastReportMs") ?: 0L,
                        lines = (doc.get("lines") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                    )
                } ?: emptyList()
                onUpdate(docs)
            }

    fun listenLog(
        username: String,
        tvId: String,
        onUpdate: (TvLogDoc?) -> Unit,
        onError: (String) -> Unit,
    ): ListenerRegistration =
        db.collection("billingps_users").document(username)
            .collection("tvLogs").document(tvId)
            .addSnapshotListener { doc, error ->
                if (error != null) {
                    onError(error.message ?: "Gagal memuat log")
                    return@addSnapshotListener
                }
                if (doc == null || !doc.exists()) {
                    onUpdate(null)
                    return@addSnapshotListener
                }
                onUpdate(
                    TvLogDoc(
                        tvId = doc.id,
                        app = doc.getString("app") ?: "",
                        version = doc.getString("version") ?: "",
                        errorCount = (doc.getLong("errorCount") ?: 0L).toInt(),
                        totalLines = (doc.getLong("totalLines") ?: 0L).toInt(),
                        lastReportMs = doc.getLong("lastReportMs") ?: 0L,
                        lines = (doc.get("lines") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                    )
                )
            }

    fun listenCommand(
        username: String,
        tvId: String,
        onUpdate: (MonitorCommand?) -> Unit,
    ): ListenerRegistration =
        db.collection("billingps_users").document(username)
            .collection("tvCommands").document(tvId)
            .addSnapshotListener { doc, _ ->
                if (doc == null || !doc.exists()) {
                    onUpdate(null)
                    return@addSnapshotListener
                }
                onUpdate(
                    MonitorCommand(
                        command = doc.getString("monitorCommand") ?: "",
                        commandId = doc.getString("monitorCommandId") ?: "",
                        status = doc.getString("monitorStatus") ?: "",
                        result = doc.getString("monitorResult") ?: "",
                        issuedAt = doc.getTimestamp("monitorIssuedAt")?.toDate()?.time ?: 0L,
                    )
                )
            }

    fun sendCommand(
        username: String,
        tvId: String,
        command: String,
        onResult: (Boolean, String) -> Unit,
    ) {
        val commandId = UUID.randomUUID().toString()
        db.collection("billingps_users").document(username)
            .collection("tvCommands").document(tvId)
            .set(
                mapOf(
                    "monitorCommand" to command,
                    "monitorCommandId" to commandId,
                    "monitorIssuedAt" to FieldValue.serverTimestamp(),
                    "monitorStatus" to "pending",
                    "monitorResult" to "",
                    "monitorRequestedBy" to "rr-monitor",
                ),
                SetOptions.merge(),
            )
            .addOnSuccessListener { onResult(true, "") }
            .addOnFailureListener { onResult(false, it.message ?: "Gagal mengirim perintah") }
    }
}
