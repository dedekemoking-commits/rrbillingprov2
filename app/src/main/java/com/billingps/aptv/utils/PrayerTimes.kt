package com.billingps.aptv.utils

import com.billingps.aptv.models.PrayerConfig
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

data class PrayerTime(
    val name: String,
    val hour: Int,
    val minute: Int,
    val atMillis: Long,
) {
    val label: String get() = String.format(Locale.US, "%02d:%02d", hour, minute)
}

data class PrayerSchedule(
    val subuh: PrayerTime,
    val dzuhur: PrayerTime,
    val ashar: PrayerTime,
    val maghrib: PrayerTime,
    val isya: PrayerTime,
) {
    fun all(): List<PrayerTime> = listOf(subuh, dzuhur, ashar, maghrib, isya)
}

object PrayerTimes {
    private const val FAJR_ANGLE = 20.0
    private const val ISHA_ANGLE = 18.0
    private const val SUN_ALTITUDE = -0.833
    private const val IHTIYAT_MINUTES = 2.0

    fun scheduleFor(
        date: Calendar,
        latitude: Double,
        longitude: Double,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): PrayerSchedule? {
        if (!latitude.isFinite() || !longitude.isFinite() ||
            latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || longitude > 180.0
        ) return null
        val year = date.get(Calendar.YEAR)
        val month = date.get(Calendar.MONTH) + 1
        val day = date.get(Calendar.DAY_OF_MONTH)
        val jd = julianDay(year, month, day)
        val d = jd - 2451545.0
        val g = Math.toRadians(357.529 + 0.98560028 * d)
        val q = 280.459 + 0.98564736 * d
        val l = Math.toRadians(q + 1.915 * sin(g) + 0.020 * sin(2 * g))
        val e = Math.toRadians(23.439 - 0.00000036 * d)
        val declination = asin(sin(e) * sin(l))
        val rightAscension = atan2(cos(e) * sin(l), cos(l))
        var equationOfTime = q / 15.0 - Math.toDegrees(rightAscension) / 15.0
        while (equationOfTime > 12.0) equationOfTime -= 24.0
        while (equationOfTime < -12.0) equationOfTime += 24.0
        val base = Calendar.getInstance(timeZone).apply {
            clear()
            set(year, month - 1, day, 0, 0, 0)
        }
        val tzOffset = timeZone.getOffset(base.timeInMillis).toDouble() / 3_600_000.0
        val solarNoon = 12.0 + tzOffset - longitude / 15.0 - equationOfTime
        val latRad = Math.toRadians(latitude)
        val fajrHourAngle = hourAngle(-FAJR_ANGLE, latRad, declination) ?: return null
        val sunriseHourAngle = hourAngle(SUN_ALTITUDE, latRad, declination) ?: return null
        val ishaHourAngle = hourAngle(-ISHA_ANGLE, latRad, declination) ?: return null
        val asharAltitude = Math.toDegrees(atan(1.0 / (1.0 + tan(abs(latRad - declination)))))
        val asharHourAngle = hourAngle(asharAltitude, latRad, declination) ?: return null
        val ihtiyat = IHTIYAT_MINUTES / 60.0
        return PrayerSchedule(
            subuh = prayerTime("Subuh", solarNoon - fajrHourAngle + ihtiyat, base),
            dzuhur = prayerTime("Dzuhur", solarNoon + ihtiyat, base),
            ashar = prayerTime("Ashar", solarNoon + asharHourAngle + ihtiyat, base),
            maghrib = prayerTime("Maghrib", solarNoon + sunriseHourAngle + ihtiyat, base),
            isya = prayerTime("Isya", solarNoon + ishaHourAngle + ihtiyat, base),
        )
    }

    fun scheduleForToday(config: PrayerConfig): PrayerSchedule? {
        if (!config.locationSet) return null
        return scheduleFor(
            Calendar.getInstance(),
            config.latitude,
            config.longitude,
        )
    }

    fun nextPrayer(config: PrayerConfig, now: Long = System.currentTimeMillis()): PrayerTime? {
        if (!config.locationSet) return null
        val today = Calendar.getInstance().apply { timeInMillis = now }
        scheduleFor(today, config.latitude, config.longitude)
            ?.all()
            ?.firstOrNull { it.atMillis > now }
            ?.let { return it }
        val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        return scheduleFor(tomorrow, config.latitude, config.longitude)?.subuh
    }

    private fun julianDay(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = Math.floor(y / 100.0)
        val b = 2 - a + Math.floor(a / 4.0)
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun hourAngle(altitudeDegrees: Double, latitudeRad: Double, declinationRad: Double): Double? {
        val numerator = sin(Math.toRadians(altitudeDegrees)) - sin(latitudeRad) * sin(declinationRad)
        val denominator = cos(latitudeRad) * cos(declinationRad)
        if (abs(denominator) < 0.000001) return null
        val cosHourAngle = numerator / denominator
        if (cosHourAngle < -1.0 || cosHourAngle > 1.0) return null
        return Math.toDegrees(acos(cosHourAngle)) / 15.0
    }

    private fun prayerTime(name: String, hourValue: Double, base: Calendar): PrayerTime {
        val minutes = Math.round(hourValue * 60.0).toInt()
        val atMillis = base.timeInMillis + minutes * 60_000L
        val hour = ((minutes / 60) % 24 + 24) % 24
        val minute = ((minutes % 60) + 60) % 60
        return PrayerTime(name, hour, minute, atMillis)
    }
}
