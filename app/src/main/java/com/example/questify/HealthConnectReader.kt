package com.example.questify

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.annotation.RequiresApi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

object HealthConnectReader {
    const val STATUS_AVAILABLE = 0
    const val STATUS_NEEDS_INSTALL = 1
    const val STATUS_UNSUPPORTED = 2

    private val providerPackages = listOf(
        "com.google.android.apps.healthdata",
        "com.google.android.healthconnect.controller"
    )

    fun sdkStatus(context: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return STATUS_UNSUPPORTED
        }
        val providerStatuses = providerPackages.mapNotNull { pkg ->
            runCatching { HealthConnectClient.getSdkStatus(context, pkg) }.getOrNull()
        }
        if (providerStatuses.any { it == HealthConnectClient.SDK_AVAILABLE }) return STATUS_AVAILABLE
        if (providerStatuses.any { it == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED }) {
            return STATUS_NEEDS_INSTALL
        }
        if (runCatching { HealthConnectClient.getOrCreate(context) }.isSuccess) return STATUS_AVAILABLE
        val installed = providerPackages.any { pkg ->
            runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
        }
        return when {
            installed -> STATUS_NEEDS_INSTALL
            Build.VERSION.SDK_INT >= 34 -> STATUS_NEEDS_INSTALL
            else -> STATUS_UNSUPPORTED
        }
    }

    @Suppress("unused")
    fun isAvailable(context: Context): Boolean {
        return sdkStatus(context) == STATUS_AVAILABLE
    }

    fun readPermissionForMetric(metric: String?): String? {
        return when (metric?.trim()?.lowercase(Locale.getDefault())) {
            "steps" -> HealthPermission.getReadPermission(StepsRecord::class)
            "heart_rate" -> HealthPermission.getReadPermission(HeartRateRecord::class)
            "distance_m" -> HealthPermission.getReadPermission(DistanceRecord::class)
            "calories_kcal" -> HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
            else -> null
        }
    }

    @Suppress("unused")
    fun readPermissionsForMetrics(metrics: Iterable<String?>): Set<String> {
        return metrics.mapNotNull(::readPermissionForMetric).toSet()
    }

    suspend fun readTodaySnapshot(
        context: Context,
        grantedPermissions: Set<String>? = null
    ): HealthDailySnapshot {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return HealthDailySnapshot(
                epochDay = epochDayNow(),
                steps = 0,
                avgHeartRate = null,
                distanceMeters = 0f,
                caloriesKcal = 0f,
                source = "health_connect_unavailable"
            )
        }
        return readTodaySnapshotO(context, grantedPermissions)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun readTodaySnapshotO(
        context: Context,
        grantedPermissions: Set<String>?
    ): HealthDailySnapshot {
        val client = HealthConnectClient.getOrCreate(context)
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val end = Instant.now()

        val knownGrantedPermissions = grantedPermissions
            ?: runCatching { client.permissionController.getGrantedPermissions() }.getOrNull()

        val stepsPermission = HealthPermission.getReadPermission(StepsRecord::class)
        val distancePermission = HealthPermission.getReadPermission(DistanceRecord::class)
        val caloriesPermission = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
        val heartPermission = HealthPermission.getReadPermission(HeartRateRecord::class)

        fun canRead(permission: String): Boolean {
            return knownGrantedPermissions?.contains(permission) ?: true
        }

        val steps = if (canRead(stepsPermission)) {
            runCatching {
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = StepsRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(start, end)
                    )
                ).records.sumOf { it.count }
                    .coerceIn(0L, Int.MAX_VALUE.toLong())
                    .toInt()
            }.onFailure { AppLog.w("health_read_steps_failed", it) }.getOrDefault(0)
        } else {
            0
        }

        val distanceMeters = if (canRead(distancePermission)) {
            runCatching {
                val raw = client.readRecords(
                    ReadRecordsRequest(
                        recordType = DistanceRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(start, end)
                    )
                ).records.sumOf { it.distance.inMeters }.toFloat()
                if (raw.isFinite()) raw else 0f
            }.onFailure { AppLog.w("health_read_distance_failed", it) }.getOrDefault(0f)
        } else {
            0f
        }

        val caloriesKcal = if (canRead(caloriesPermission)) {
            runCatching {
                val raw = client.readRecords(
                    ReadRecordsRequest(
                        recordType = TotalCaloriesBurnedRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(start, end)
                    )
                ).records.sumOf { it.energy.inKilocalories }.toFloat()
                if (raw.isFinite()) raw else 0f
            }.onFailure { AppLog.w("health_read_calories_failed", it) }.getOrDefault(0f)
        } else {
            0f
        }

        val avgHeart = if (canRead(heartPermission)) {
            runCatching {
                val samples = client.readRecords(
                    ReadRecordsRequest(
                        recordType = HeartRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(start, end)
                    )
                ).records.flatMap { it.samples }
                if (samples.isNotEmpty()) {
                    val avg = samples.sumOf { it.beatsPerMinute.toDouble() } / samples.size.toDouble()
                    if (avg.isFinite()) avg.toInt() else null
                } else {
                    null
                }
            }.onFailure { AppLog.w("health_read_heart_failed", it) }.getOrDefault(null)
        } else {
            null
        }

        return HealthDailySnapshot(
            epochDay = epochDayNow(),
            steps = steps.coerceAtLeast(0),
            avgHeartRate = avgHeart,
            distanceMeters = distanceMeters.coerceAtLeast(0f),
            caloriesKcal = caloriesKcal.coerceAtLeast(0f),
            source = "health_connect"
        )
    }
}

























