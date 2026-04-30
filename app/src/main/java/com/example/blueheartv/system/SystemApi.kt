package com.example.blueheartv.system

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds

class SystemApi(
    private val context: Context
) {
    private val appContext = context.applicationContext

    @SuppressLint("QueryAllPackagesPermission")
    fun listApps(type: String): JSONObject {
        require(type in setOf("all", "third", "system")) { "type 必须是 all、third 或 system。" }
        val packageManager = appContext.packageManager
        val apps = packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        val result = JSONObject()
        apps.asSequence()
            .filter { app ->
                val isSystem = app.isSystemApp()
                when (type) {
                    "third" -> !isSystem
                    "system" -> isSystem
                    else -> true
                }
            }
            .sortedBy { it.packageName }
            .forEach { app ->
                result.put(app.packageName, app.loadLabel(packageManager).toString())
            }
        return result
    }

    fun createEvent(event: JSONObject): Long {
        requireCalendarWritePermission()
        val calendarId = firstWritableCalendarId()
        val values = eventValues(event).apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            if (!containsKey(CalendarContract.Events.EVENT_TIMEZONE)) {
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
        }
        require(values.containsKey(CalendarContract.Events.DTSTART)) { "event.dtstart 缺失。" }
        require(
            values.containsKey(CalendarContract.Events.DTEND) ||
                    values.containsKey(CalendarContract.Events.DURATION)
        ) { "event.dtend 或 event.duration 至少需要一个。" }

        val uri = appContext.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: error("创建日程失败。")
        return ContentUris.parseId(uri)
    }

    fun listEvents(start: Long, end: Long): JSONArray {
        requireCalendarReadPermission()
        require(start <= end) { "start 不能大于 end。" }
        val selection = "(${CalendarContract.Events.DTSTART} BETWEEN ? AND ?) OR " +
                "(${CalendarContract.Events.DTEND} BETWEEN ? AND ?)"
        val args = arrayOf(start.toString(), end.toString(), start.toString(), end.toString())
        val result = JSONArray()
        appContext.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            EVENT_PROJECTION,
            selection,
            args,
            "${CalendarContract.Events.DTSTART} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                result.put(cursor.toEventJson())
            }
        }
        return result
    }

    fun updateEvent(event: JSONObject) {
        requireCalendarWritePermission()
        require(event.has("_id")) { "event._id 缺失。" }
        val id = event.getLong("_id")
        val values = eventValues(event)
        require(values.size() > 0) { "没有可更新的日程字段。" }
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
        val updated = appContext.contentResolver.update(uri, values, null, null)
        require(updated > 0) { "未找到日程: $id。" }
    }

    fun listReminders(eventId: Long): JSONArray {
        requireCalendarReadPermission()
        val result = JSONArray()
        appContext.contentResolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            REMINDER_PROJECTION,
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            "${CalendarContract.Reminders.MINUTES} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                result.put(
                    JSONObject().apply {
                        put("minutes", cursor.getInt(0))
                        put("method", reminderMethodToString(cursor.getInt(1)))
                    }
                )
            }
        }
        return result
    }

    fun updateReminders(eventId: Long, reminders: JSONArray) {
        requireCalendarWritePermission()
        val resolver = appContext.contentResolver
        resolver.delete(
            CalendarContract.Reminders.CONTENT_URI,
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString())
        )
        for (index in 0 until reminders.length()) {
            val reminder = reminders.getJSONObject(index)
            val values = android.content.ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, reminder.requiredInt("minutes"))
                put(CalendarContract.Reminders.METHOD, reminderMethodFromString(reminder.requiredString("method")))
            }
            resolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
                ?: error("创建提醒失败。")
        }
    }

    suspend fun getLocation(): JSONObject {
        requireLocationPermission()
        val location = requestCurrentLocation()
        return JSONObject().apply {
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            put("accuracy", location.accuracy)
            put("timestamp", location.time)
        }
    }

    private fun firstWritableCalendarId(): Long {
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        appContext.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            selection,
            args,
            "${CalendarContract.Calendars._ID} ASC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        error("没有可写日历。")
    }

    private fun eventValues(event: JSONObject): android.content.ContentValues {
        return android.content.ContentValues().apply {
            putStringIfPresent(event, "title", CalendarContract.Events.TITLE)
            putStringIfPresent(event, "description", CalendarContract.Events.DESCRIPTION)
            putStringIfPresent(event, "eventLocation", CalendarContract.Events.EVENT_LOCATION)
            putLongIfPresent(event, "dtstart", CalendarContract.Events.DTSTART)
            putLongIfPresent(event, "dtend", CalendarContract.Events.DTEND)
            putBooleanIfPresent(event, "allDay", CalendarContract.Events.ALL_DAY)
            putStringIfPresent(event, "eventTimezone", CalendarContract.Events.EVENT_TIMEZONE)
            putStringIfPresent(event, "duration", CalendarContract.Events.DURATION)
            putStringIfPresent(event, "rrule", CalendarContract.Events.RRULE)
            if (event.has("availability") && !event.isNull("availability")) {
                put(
                    CalendarContract.Events.AVAILABILITY,
                    availabilityFromString(event.getString("availability"))
                )
            }
            if (event.has("status") && !event.isNull("status")) {
                put(CalendarContract.Events.STATUS, statusFromString(event.getString("status")))
            }
        }
    }

    private fun requireCalendarReadPermission() {
        require(
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR) ==
                    PackageManager.PERMISSION_GRANTED
        ) { "READ_CALENDAR 权限未授予。" }
    }

    private fun requireCalendarWritePermission() {
        require(
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.WRITE_CALENDAR) ==
                    PackageManager.PERMISSION_GRANTED
        ) { "WRITE_CALENDAR 权限未授予。" }
    }

    private fun requireLocationPermission() {
        val fineGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        require(fineGranted || coarseGranted) { "位置权限未授予。" }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocation(): Location {
        val locationManager = appContext.getSystemService(LocationManager::class.java)
            ?: error("LocationManager 不可用。")
        val fineGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val providers = buildList {
            add(LocationManager.NETWORK_PROVIDER)
            if (fineGranted) add(LocationManager.GPS_PROVIDER)
        }
            .filter { locationManager.isProviderEnabled(it) }
        require(providers.isNotEmpty()) { "没有可用的定位提供方。" }

        return try {
            withTimeout(30_000.milliseconds) {
                suspendCancellableCoroutine { continuation ->
                    lateinit var listener: LocationListener

                    fun cleanup() {
                        runCatching { locationManager.removeUpdates(listener) }
                    }

                    listener = LocationListener { location ->
                        if (!continuation.isActive) return@LocationListener
                        cleanup()
                        continuation.resume(location)
                    }

                    continuation.invokeOnCancellation { cleanup() }

                    var registered = 0
                    providers.forEach { provider ->
                        runCatching {
                            locationManager.requestLocationUpdates(
                                provider,
                                0L,
                                0f,
                                ContextCompat.getMainExecutor(appContext),
                                listener
                            )
                            registered += 1
                        }
                    }
                    if (registered == 0 && continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException("无法注册定位监听。"))
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            error("30秒内未获取到当前位置，请确认定位服务可用并重试。")
        }
    }

    private companion object {
        private val EVENT_PROJECTION = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.AVAILABILITY,
            CalendarContract.Events.STATUS
        )

        private val REMINDER_PROJECTION = arrayOf(
            CalendarContract.Reminders.MINUTES,
            CalendarContract.Reminders.METHOD
        )
    }
}

private fun ApplicationInfo.isSystemApp(): Boolean {
    return (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
}

private fun android.content.ContentValues.putStringIfPresent(
    source: JSONObject,
    jsonKey: String,
    column: String
) {
    if (source.has(jsonKey) && !source.isNull(jsonKey)) {
        put(column, source.getString(jsonKey))
    }
}

private fun android.content.ContentValues.putLongIfPresent(
    source: JSONObject,
    jsonKey: String,
    column: String
) {
    if (source.has(jsonKey) && !source.isNull(jsonKey)) {
        put(column, source.getLong(jsonKey))
    }
}

private fun android.content.ContentValues.putBooleanIfPresent(
    source: JSONObject,
    jsonKey: String,
    column: String
) {
    if (source.has(jsonKey) && !source.isNull(jsonKey)) {
        put(column, if (source.getBoolean(jsonKey)) 1 else 0)
    }
}

private fun android.database.Cursor.toEventJson(): JSONObject {
    return JSONObject().apply {
        put("_id", getLong(0))
        putNullable("title", getString(1))
        putNullable("description", getString(2))
        putNullable("eventLocation", getString(3))
        put("dtstart", getLong(4))
        put("dtend", if (isNull(5)) JSONObject.NULL else getLong(5))
        put("allDay", getInt(6) != 0)
        putNullable("eventTimezone", getString(7))
        putNullable("duration", getString(8))
        putNullable("rrule", getString(9))
        put("availability", availabilityToString(getInt(10)))
        put("status", statusToString(getInt(11)))
    }
}

private fun JSONObject.putNullable(key: String, value: String?) {
    put(key, value ?: JSONObject.NULL)
}

private fun availabilityFromString(value: String): Int {
    return when (value) {
        "busy" -> CalendarContract.Events.AVAILABILITY_BUSY
        "free" -> CalendarContract.Events.AVAILABILITY_FREE
        "tentative" -> CalendarContract.Events.AVAILABILITY_TENTATIVE
        else -> error("availability 必须是 busy、free 或 tentative。")
    }
}

private fun availabilityToString(value: Int): String {
    return when (value) {
        CalendarContract.Events.AVAILABILITY_FREE -> "free"
        CalendarContract.Events.AVAILABILITY_TENTATIVE -> "tentative"
        else -> "busy"
    }
}

private fun statusFromString(value: String): Int {
    return when (value) {
        "confirmed" -> CalendarContract.Events.STATUS_CONFIRMED
        "tentative" -> CalendarContract.Events.STATUS_TENTATIVE
        "cancelled" -> CalendarContract.Events.STATUS_CANCELED
        else -> error("status 必须是 confirmed、tentative 或 cancelled。")
    }
}

private fun statusToString(value: Int): String {
    return when (value) {
        CalendarContract.Events.STATUS_TENTATIVE -> "tentative"
        CalendarContract.Events.STATUS_CANCELED -> "cancelled"
        else -> "confirmed"
    }
}

private fun reminderMethodFromString(value: String): Int {
    return when (value) {
        "alert" -> CalendarContract.Reminders.METHOD_ALERT
        "alarm" -> CalendarContract.Reminders.METHOD_ALARM
        else -> error("method 必须是 alert 或 alarm。")
    }
}

private fun reminderMethodToString(value: Int): String {
    return when (value) {
        CalendarContract.Reminders.METHOD_ALARM -> "alarm"
        else -> "alert"
    }
}

private fun JSONObject.requiredString(key: String): String {
    require(has(key) && !isNull(key)) { "$key 缺失。" }
    return getString(key)
}

private fun JSONObject.requiredInt(key: String): Int {
    require(has(key) && !isNull(key)) { "$key 缺失。" }
    return getInt(key)
}
