package com.billingps.owner.util

import com.billingps.owner.data.ClientStatus
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object OwnerTime {
    val WIB: ZoneId = ZoneId.of("Asia/Jakarta")

    private const val SIX_HOURS_45_MIN = 6L * 60L * 60L * 1000L + 45L * 60L * 1000L
    private const val SEVEN_HOURS_15_MIN = 7L * 60L * 60L * 1000L + 15L * 60L * 1000L
    private const val SEVEN_HOURS = 7L * 60L * 60L * 1000L
    private const val ONLINE_STALE_MS = 90_000L

    private val display = DateTimeFormatter
        .ofPattern("dd MMM HH:mm", Locale("id", "ID"))
        .withZone(WIB)
    private val fullDisplay = DateTimeFormatter
        .ofPattern("dd MMM yyyy HH:mm:ss", Locale("id", "ID"))
        .withZone(WIB)
    private val dayKeyFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd", Locale.US)
        .withZone(WIB)
    private val epochId = Regex("^(?:TRX-|tx_)(\\d{12,})$")

    fun parseInstant(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        val value = raw.trim()
        return try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(value).toInstant()
            } catch (_: DateTimeParseException) {
                try {
                    LocalDateTime.parse(value).atZone(WIB).toInstant()
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
    }

    fun transactionInstant(id: String, raw: String?): Instant? {
        val parsed = parseInstant(raw) ?: return null
        val epoch = idEpochMillis(id) ?: return parsed
        val diff = parsed.toEpochMilli() - epoch
        return if (diff in SIX_HOURS_45_MIN..SEVEN_HOURS_15_MIN) {
            parsed.minusMillis(SEVEN_HOURS)
        } else {
            parsed
        }
    }

    fun formatWib(id: String, raw: String?): String =
        transactionInstant(id, raw)?.let { display.format(it) } ?: raw.orEmpty()

    fun formatWibFull(id: String, raw: String?): String =
        transactionInstant(id, raw)?.let { fullDisplay.format(it) } ?: raw.orEmpty()

    fun dayKey(id: String, raw: String?): String? =
        transactionInstant(id, raw)?.let { dayKeyFormatter.format(it) }

    fun formatDateTime(millis: Long): String =
        if (millis <= 0L) "-" else display.format(Instant.ofEpochMilli(millis))

    fun formatDuration(seconds: Long): String {
        val total = seconds.coerceAtLeast(0L)
        val hours = total / 3600L
        val minutes = (total % 3600L) / 60L
        val secs = total % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, secs)
        }
    }

    fun isOnline(status: ClientStatus?): Boolean {
        if (status == null || !status.online || status.lastSeen <= 0L) return false
        return System.currentTimeMillis() - status.lastSeen <= ONLINE_STALE_MS
    }

    fun lastSeenText(status: ClientStatus?): String {
        if (status == null || status.lastSeen <= 0L) return "Belum ada heartbeat"
        val diff = (System.currentTimeMillis() - status.lastSeen).coerceAtLeast(0L)
        return when {
            diff < 10_000L -> "Baru saja"
            diff < 60_000L -> "${diff / 1000L} detik lalu"
            diff < 3_600_000L -> "${diff / 60_000L} menit lalu"
            diff < 86_400_000L -> "${diff / 3_600_000L} jam lalu"
            else -> "${diff / 86_400_000L} hari lalu"
        }
    }

    fun idEpochMillis(id: String): Long? =
        epochId.matchEntire(id)?.groupValues?.getOrNull(1)?.toLongOrNull()

    fun utcNow(): Instant = Instant.now()
}
