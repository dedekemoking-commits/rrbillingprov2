package com.billingps.tvclient.firestore

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TvCommand(
    val showWarning: Boolean = false,
    val warningMessage: String = "",
    val playVideo: Boolean = false,
    val videoUrl: String = "",
    val stopVideo: Boolean = false,
    val cacheMedia: Boolean = false,
    val mediaUrl: String = "",
    val warningImageUrl: String = "",
    val warningVideoUrl: String = "",
    val hdmiPort: Int = -1,
    val playCachedMedia: Boolean = false,
    val hideWarning: Boolean = false,
    val hdmiTestId: String = "",
    val hdmiTestPort: Int = 0,
    val hdmiTestCommand: String = "",
)

data class TvStatus(
    val sisaDetik: Long = 0,
    val timerActive: Boolean = false,
    val paketAktif: String = "",
    val ip: String = "",
    val hdmiPort: Int = 0,
    val hdmiInputHint: String = "",
    val hdmiCommand: String = "",
)

class CloudHelper {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var commandListener: ListenerRegistration? = null
    private var tvStatusListener: ListenerRegistration? = null

    suspend fun ensureSignedIn(): Boolean = withContext(Dispatchers.IO) {
        val user = auth.currentUser
        if (user != null) return@withContext true
        try {
            val task = auth.signInAnonymously()
            Tasks.await(task, 15, java.util.concurrent.TimeUnit.SECONDS)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun listenTvCommands(username: String, tvId: String, onCommand: (TvCommand) -> Unit) {
        commandListener?.remove()
        commandListener = firestore
            .collection("billingps_users").document(username)
            .collection("tvCommands").document(tvId)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null || !snap.exists()) return@addSnapshotListener
                val showWarning = snap.getBoolean("showWarning") ?: false
                val warningMessage = snap.getString("warningMessage") ?: "⚠️ Waktu hampir habis!"
                val playVideo = snap.getBoolean("playVideo") ?: false
                val videoUrl = snap.getString("videoUrl") ?: ""
                val stopVideo = snap.getBoolean("stopVideo") ?: false
                val cacheMedia = snap.getBoolean("cacheMedia") ?: false
                val mediaUrl = snap.getString("mediaUrl") ?: ""
                val warningImageUrl = snap.getString("warningImageUrl") ?: ""
                val warningVideoUrl = snap.getString("warningVideoUrl") ?: ""
                val hdmiPort = snap.getLong("hdmiPort")?.toInt() ?: -1
                val playCachedMedia = snap.getBoolean("playCachedMedia") ?: false
                val hideWarning = snap.getBoolean("hideWarning") ?: false
                val hdmiTestId = snap.getString("hdmiTestId") ?: ""
                val hdmiTestPort = snap.getLong("hdmiTestPort")?.toInt() ?: 0
                val hdmiTestCommand = snap.getString("hdmiTestCommand") ?: ""
                if (showWarning || hideWarning || playVideo || stopVideo || cacheMedia || warningImageUrl.isNotEmpty() || warningVideoUrl.isNotEmpty() || hdmiPort > 0 || playCachedMedia || hdmiTestId.isNotEmpty()) {
                    onCommand(
                        TvCommand(
                            showWarning = showWarning,
                            warningMessage = warningMessage,
                            playVideo = playVideo,
                            videoUrl = videoUrl,
                            stopVideo = stopVideo,
                            cacheMedia = cacheMedia,
                            mediaUrl = mediaUrl,
                            warningImageUrl = warningImageUrl,
                            warningVideoUrl = warningVideoUrl,
                            hdmiPort = hdmiPort,
                            playCachedMedia = playCachedMedia,
                            hideWarning = hideWarning,
                            hdmiTestId = hdmiTestId,
                            hdmiTestPort = hdmiTestPort,
                            hdmiTestCommand = hdmiTestCommand,
                        )
                    )
                    if (showWarning) {
                        firestore.collection("billingps_users").document(username)
                            .collection("tvCommands").document(tvId)
                            .update("showWarning", false)
                    }
                    if (hideWarning) {
                        firestore.collection("billingps_users").document(username)
                            .collection("tvCommands").document(tvId)
                            .update("hideWarning", false)
                    }
                    if (stopVideo) {
                        firestore.collection("billingps_users").document(username)
                            .collection("tvCommands").document(tvId)
                            .update("stopVideo", false)
                    }
                    if (playCachedMedia) {
                        firestore.collection("billingps_users").document(username)
                            .collection("tvCommands").document(tvId)
                            .update("playCachedMedia", false)
                    }
                }
            }
    }

    fun listenTvStatus(username: String, tvId: String, onStatus: (TvStatus) -> Unit) {
        tvStatusListener?.remove()
        tvStatusListener = firestore
            .collection("billingps_users").document(username)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null || !snap.exists()) return@addSnapshotListener
                try {
                    val rawList = snap.get("tvList") as? List<Map<String, Any>> ?: return@addSnapshotListener
                    val match = rawList.firstOrNull { it["id"] == tvId } ?: return@addSnapshotListener
                    val sisaDetik = (match["sisaDetik"] as? Number)?.toLong() ?: 0
                    val timerActive = match["timerActive"] as? Boolean ?: false
                    val paketAktif = match["paketAktif"] as? String ?: ""
                    val ip = match["ip"] as? String ?: ""
                    val hdmiPort = (match["hdmiPort"] as? Number)?.toInt() ?: 0
                    val hdmiInputHint = match["hdmiInputHint"] as? String ?: ""
                    val hdmiCommand = match["hdmiCommand"] as? String ?: ""
                    onStatus(TvStatus(sisaDetik, timerActive, paketAktif, ip, hdmiPort, hdmiInputHint, hdmiCommand))
                } catch (e: Exception) {
                    Log.e("CloudHelper", "listenTvStatus parse error: ${e.message}")
                }
            }
    }

    fun reportHdmiTest(username: String, tvId: String, testId: String, result: String) {
        firestore.collection("billingps_users").document(username)
            .collection("tvCommands").document(tvId)
            .set(
                mapOf(
                    "hdmiTestId" to testId,
                    "hdmiTestResult" to result,
                    "hdmiTestAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            )
    }

    fun reportVideoPlaying(username: String, tvId: String, playing: Boolean) {
        firestore.collection("billingps_users").document(username)
            .collection("tvCommands").document(tvId)
            .set(mapOf("videoPlaying" to playing), com.google.firebase.firestore.SetOptions.merge())
    }

    suspend fun sendHeartbeat(username: String, tvId: String, version: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Tasks.await(
                firestore.collection("billingps_users").document(username)
                    .collection("tvCommands").document(tvId)
                    .set(
                        mapOf(
                            "clientOnline" to true,
                            "clientVersion" to version,
                            "lastSeen" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        ),
                        com.google.firebase.firestore.SetOptions.merge(),
                    ),
                15, java.util.concurrent.TimeUnit.SECONDS
            )
            true
        } catch (e: Exception) {
            Log.e("CloudHelper", "sendHeartbeat failed: ${e.message}")
            false
        }
    }

    fun cleanup() {
        commandListener?.remove()
        tvStatusListener?.remove()
    }
}
