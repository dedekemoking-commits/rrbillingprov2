package com.billingps.aptv.utils

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object TimeUtils {
    val WIB: ZoneId = ZoneId.of("Asia/Jakarta")

    private const val FIVE_MINUTES_MILLIS = 5L * 60L * 1000L
    private const val SIX_HOURS_45_MILLIS = 6L * 60L * 60L * 1000L + 45L * 60L * 1000L
    private const val SEVEN_HOURS_MILLIS = 7L * 60L * 60L * 1000L
    private const val SEVEN_HOURS_15_MILLIS = 7L * 60L * 60L * 1000L + 15L * 60L * 1000L

    private val UTC_FORMAT = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .withZone(ZoneOffset.UTC)
    private val WIB_DISPLAY = DateTimeFormatter
        .ofPattern("dd MMM HH:mm", Locale("id", "ID"))
        .withZone(WIB)
    private val WIB_FULL = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
        .withZone(WIB)
    private val WIB_DAY = DateTimeFormatter
        .ofPattern("yyyy-MM-dd", Locale.US)
        .withZone(WIB)
    private val EPOCH_ID = Regex("^(?:TRX-|tx_)(\\d{12,})$")

    fun nowUtcIso(): String = UTC_FORMAT.format(Instant.now())

    fun toUtcIso(instant: Instant): String = UTC_FORMAT.format(instant)

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
        return when {
            diff in SIX_HOURS_45_MILLIS..SEVEN_HOURS_15_MILLIS ->
                parsed.minusMillis(SEVEN_HOURS_MILLIS)
            diff in -FIVE_MINUTES_MILLIS..FIVE_MINUTES_MILLIS -> parsed
            else -> parsed
        }
    }

    fun normalizeTransactionTime(id: String, raw: String?): String {
        if (raw.isNullOrBlank()) return raw.orEmpty()
        val instant = transactionInstant(id, raw) ?: return raw
        return toUtcIso(instant)
    }

    fun migrateLegacyLocalTransactionTime(id: String, raw: String?): String {
        if (raw.isNullOrBlank()) return raw.orEmpty()
        if (idEpochMillis(id) != null) return normalizeTransactionTime(id, raw)
        val value = raw.trim()
        if (!value.endsWith("Z", ignoreCase = true)) return normalizeTransactionTime(id, raw)
        val parsed = parseInstant(value) ?: return raw
        return toUtcIso(parsed.minusMillis(SEVEN_HOURS_MILLIS))
    }

    fun transactionZoned(id: String, raw: String?): ZonedDateTime? =
        transactionInstant(id, raw)?.atZone(WIB)

    fun formatWib(id: String, raw: String?): String =
        transactionZoned(id, raw)?.format(WIB_DISPLAY) ?: raw.orEmpty()

    fun formatWibFull(id: String, raw: String?): String =
        transactionZoned(id, raw)?.format(WIB_FULL) ?: raw.orEmpty()

    fun wibDayKey(id: String, raw: String?): String? =
        transactionZoned(id, raw)?.format(WIB_DAY)

    fun idEpochMillis(id: String): Long? {
        val match = EPOCH_ID.matchEntire(id) ?: return null
        return match.groupValues[1].toLongOrNull()
    }
}
