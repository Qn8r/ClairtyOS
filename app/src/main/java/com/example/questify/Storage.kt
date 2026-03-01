package com.example.questify

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.KeyStore
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
val Context.dataStore by preferencesDataStore(name = "living_life_mmo")

object Keys {
    val DATA_VERSION = intPreferencesKey("data_version")
    val BALANCE = intPreferencesKey("balance")
    val GRIMOIRE = stringPreferencesKey("grimoire_pages")

    val LAST_DAY = longPreferencesKey("last_day_epoch")
    val ROUTINES = stringPreferencesKey("routines_serialized")
    val COMPLETED = stringPreferencesKey("completed_ids")
    val EARNED = stringPreferencesKey("earned_ids")
    val REFRESH_COUNT = intPreferencesKey("refresh_count")

    val AVATAR_PRESET = stringPreferencesKey("avatar_preset")
    val AVATAR_URI = stringPreferencesKey("avatar_uri")

    // Character Colors
    val CHAR_HEAD = longPreferencesKey("char_head")
    val CHAR_BODY = longPreferencesKey("char_body")
    val CHAR_LEGS = longPreferencesKey("char_legs")
    val CHAR_SHOES = longPreferencesKey("char_shoes")

    // Main routines & Tutorials
    val MILESTONES = stringPreferencesKey("milestones_v1")
    val TUTORIAL_INV = booleanPreferencesKey("tut_inv")
    val TUTORIAL_MILESTONES = booleanPreferencesKey("tut_mq")
    val TUTORIAL_POOL = booleanPreferencesKey("tut_pool")
    val TUTORIAL_CATALOG = booleanPreferencesKey("tut_shop_seen")
    val TUTORIAL_CALENDAR = booleanPreferencesKey("tut_calendar_seen")
    val TUTORIAL_ROUTINES = booleanPreferencesKey("tut_routines_seen")
    val CATALOG_HOLD_HINT_SEEN = booleanPreferencesKey("shop_hold_hint_seen")

    val HISTORY = stringPreferencesKey("history_serialized")
    val INVENTORY = stringPreferencesKey("inventory_serialized")

    val APP_THEME = stringPreferencesKey("app_theme")
    val ACCENT_ARGB = intPreferencesKey("accent_argb")
    val AUTO_NEW_DAY = booleanPreferencesKey("auto_new_day")
    val CONFIRM_COMPLETE = booleanPreferencesKey("confirm_complete")
    val REFRESH_INCOMPLETE_ONLY = booleanPreferencesKey("refresh_incomplete_only")
    val ADMIN_MODE = booleanPreferencesKey("admin_mode")

    val CUSTOM_TEMPLATES = stringPreferencesKey("custom_templates")

    // NEW: Template Library Storage
    val SAVED_TEMPLATES = stringPreferencesKey("saved_templates")

    val AUTH_ACCESS_TOKEN = stringPreferencesKey("auth_access_token")
    val AUTH_REFRESH_TOKEN = stringPreferencesKey("auth_refresh_token")
    val AUTH_USER_EMAIL = stringPreferencesKey("auth_user_email")
    val AUTH_USER_ID = stringPreferencesKey("auth_user_id")
    val AUTH_PROVIDER = stringPreferencesKey("auth_provider")
    val SALARY_PROFILE = stringPreferencesKey("SALARY_PROFILE")
    val PURCHASE_TRANSACTIONS = stringPreferencesKey("PURCHASE_TRANSACTIONS")
    val CATALOG_ITEMS = stringPreferencesKey("CATALOG_ITEMS")
    val CALENDAR_PLANS = stringPreferencesKey("calendar_plans")
    val BALANCE_PER_MINUTE = intPreferencesKey("BALANCE_PER_MINUTE")
    val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    val ONBOARDING_GOAL = stringPreferencesKey("onboarding_goal")
    val ONBOARDING_DIFFICULTY = stringPreferencesKey("onboarding_difficulty")
    val PREMIUM_UNLOCKED = booleanPreferencesKey("premium_unlocked")
    val DAILY_ROUTINE_TARGET = intPreferencesKey("DAILY_ROUTINE_TARGET")
    val CLOUD_SYNC_ENABLED = booleanPreferencesKey("cloud_sync_enabled")
    val CLOUD_ACCOUNT_EMAIL = stringPreferencesKey("cloud_account_email")
    val CLOUD_ACCESS_TOKEN = stringPreferencesKey("cloud_access_token")
    val CLOUD_LAST_SYNC_AT = longPreferencesKey("cloud_last_sync_at")
    val CLOUD_LAST_SNAPSHOT = stringPreferencesKey("cloud_last_snapshot")
    // Advanced settings
    val ADVANCED_OPTIONS = booleanPreferencesKey("advanced_options")
    val HIGH_CONTRAST_TEXT = booleanPreferencesKey("high_contrast_text")
    val COMPACT_MODE = booleanPreferencesKey("compact_mode")
    val LARGE_TOUCH_TARGETS = booleanPreferencesKey("large_touch_targets")
    val REDUCE_ANIMATIONS = booleanPreferencesKey("reduce_animations")
    val DECORATIVE_BORDERS = booleanPreferencesKey("decorative_borders")
    val NEON_LIGHT_BOOST = booleanPreferencesKey("neon_light_boost")
    val NEON_FLOW_ENABLED = booleanPreferencesKey("neon_flow_enabled")
    val NEON_FLOW_SPEED = intPreferencesKey("neon_flow_speed")
    val NEON_GLOW_PALETTE = stringPreferencesKey("neon_glow_palette")
    val ALWAYS_SHOW_ROUTINE_PROGRESS = booleanPreferencesKey("ALWAYS_SHOW_ROUTINE_PROGRESS")
    val HIDE_COMPLETED_routines = booleanPreferencesKey("HIDE_COMPLETED_routines")
    val CONFIRM_DESTRUCTIVE = booleanPreferencesKey("confirm_destructive")
    val DAILY_RESET_HOUR = intPreferencesKey("daily_reset_hour")
    val DAILY_REMINDERS_ENABLED = booleanPreferencesKey("daily_reminders_enabled")
    val HAPTICS = booleanPreferencesKey("haptics")
    val SOUND_EFFECTS = booleanPreferencesKey("sound_effects")
    val FONT_STYLE = stringPreferencesKey("font_style")
    val FONT_SCALE_PERCENT = intPreferencesKey("font_scale_percent")
    val BACKGROUND_IMAGE_URI = stringPreferencesKey("background_image_uri")
    val BACKGROUND_IMAGE_TINT_ENABLED = booleanPreferencesKey("background_image_tint_enabled")
    val BACKGROUND_IMAGE_TRANSPARENCY_PERCENT = intPreferencesKey("background_image_transparency_percent")
    val TEXT_COLOR_ARGB = intPreferencesKey("text_color_argb")
    val APP_BACKGROUND_ARGB = intPreferencesKey("app_background_argb")
    val CHROME_BACKGROUND_ARGB = intPreferencesKey("chrome_background_argb")
    val CARD_COLOR_ARGB = intPreferencesKey("card_color_argb")
    val BUTTON_COLOR_ARGB = intPreferencesKey("button_color_argb")
    val JOURNAL_PAGE_COLOR_ARGB = intPreferencesKey("journal_page_color_argb")
    val JOURNAL_ACCENT_COLOR_ARGB = intPreferencesKey("journal_accent_color_argb")
    val JOURNAL_NAME = stringPreferencesKey("journal_name")
    val PLAYER_NAME = stringPreferencesKey("player_name")
    val TRANSPARENCY_ACCENT = intPreferencesKey("transparency_accent")
    val TRANSPARENCY_TEXT = intPreferencesKey("transparency_text")
    val TRANSPARENCY_APP_BG = intPreferencesKey("transparency_app_bg")
    val TRANSPARENCY_CHROME_BG = intPreferencesKey("transparency_chrome_bg")
    val TRANSPARENCY_CARD_BG = intPreferencesKey("transparency_card_bg")
    val TRANSPARENCY_JOURNAL_PAGE = intPreferencesKey("transparency_journal_page")
    val TRANSPARENCY_JOURNAL_ACCENT = intPreferencesKey("transparency_journal_accent")
    val TRANSPARENCY_BUTTON = intPreferencesKey("transparency_button")
    val APP_LANGUAGE = stringPreferencesKey("app_language")
    val HEALTH_DAILY_SNAPSHOT = stringPreferencesKey("health_daily_snapshot")
    val FINANCE_DEFAULT_ITEM_TYPE = stringPreferencesKey("finance_default_item_type")
    val FINANCE_WARN_THRESHOLD_PERCENT = intPreferencesKey("finance_warn_threshold_percent")
    val FINANCE_SHOW_HISTORY_HINTS = booleanPreferencesKey("finance_show_history_hints")
}


const val CURRENT_DATA_VERSION = 7
private const val MAX_TEMPLATE_PAYLOAD_CHARS = 250_000
private const val MAX_TEMPLATE_COMPRESSED_BYTES = 256 * 1024
private const val MAX_TEMPLATE_JSON_BYTES = 1_000_000
private const val MAX_BACKUP_BLOB_CHARS = 2_000_000
private const val MAX_BACKUP_PACKED_BYTES = 1_500_000
private const val MAX_BACKUP_JSON_BYTES = 2_000_000
private const val BACKUP_BLOB_PREFIX = "v4:"
private const val BACKUP_KEY_ALIAS = "questify_backup_key_v4"
private const val AUTH_STATE_BLOB_PREFIX = "encv1:"
private const val AUTH_STATE_KEY_ALIAS = "questify_auth_state_key_v1"

/* ===================== SERIALIZERS ===================== */

private val gson = Gson()

fun serializeInventory(list: List<InventoryItem>): String {
    return list.joinToString(";;") { i ->
        val safeName = i.name.replace("|", " ").replace(";;", " ")
        val safeDesc = i.description.replace("|", " ").replace(";;", " ")
        "${i.id}|${i.icon}|${i.cost}|${i.ownedCount}|1|$safeName|$safeDesc"
    }
}

fun deserializeInventory(serialized: String): List<InventoryItem> {
    if (serialized.isBlank()) return emptyList()
    return serialized.split(";;").mapNotNull { part ->
        val bits = part.split("|")
        if (bits.size < 7) return@mapNotNull null
        val id = bits[0]
        val icon = bits[1]
        val cost = bits[2].toIntOrNull() ?: 0
        val owned = bits[3].toIntOrNull() ?: 0
        val name = bits[5]
        val desc = bits.subList(6, bits.size).joinToString("|")
        InventoryItem(id, name, icon, desc, cost, owned, true)
    }
}

fun serializeRoutines(list: List<Routine>): String {
    return list.joinToString(";;") { q ->
        val safeTitle = q.title.replace("|", " ").replace(";;", " ")
        val safeIcon = q.icon.replace("|", " ").replace(";;", " ")
        val safeUri = (q.imageUri ?: "").replace("|", " ").replace(";;", " ")
        val safeObjective = q.objectiveType.name
        val safeTargetSeconds = q.targetSeconds ?: 0
        val safeHealthMetric = q.healthMetric.orEmpty().replace("|", " ").replace(";;", " ")
        val safeHealthAggregation = q.healthAggregation.orEmpty().replace("|", " ").replace(";;", " ")
        "${q.id}|${q.category.name}|$safeIcon|$safeTitle|${q.target}|${q.currentProgress}|$safeUri|$safeObjective|$safeTargetSeconds|$safeHealthMetric|$safeHealthAggregation|${q.packageId}"
    }
}

fun deserializeRoutines(serialized: String): List<Routine> {
    if (serialized.isBlank()) return emptyList()
    return serialized.split(";;").mapNotNull { part ->
        val bits = part.split("|")
        if (bits.size < 6) return@mapNotNull null
        val id = bits[0].toIntOrNull() ?: return@mapNotNull null
        val cat = runCatching { RoutineCategory.valueOf(bits[1]) }.getOrNull() ?: return@mapNotNull null
        val isLegacy = bits.size >= 13 && bits[2].toIntOrNull() != null
        if (isLegacy) {
            val icon = bits[4]
            val title = bits[5]
            val target = if (bits.size > 6) bits[6].toIntOrNull() ?: 1 else 1
            val progress = if (bits.size > 7) bits[7].toIntOrNull() ?: 0 else 0
            val imageUri = if (bits.size > 8 && bits[8].isNotBlank()) bits[8] else null
            val objectiveType = if (bits.size > 9) runCatching { RoutineObjectiveType.valueOf(bits[9]) }.getOrDefault(RoutineObjectiveType.COUNT) else RoutineObjectiveType.COUNT
            val targetSeconds = if (bits.size > 10) (bits[10].toIntOrNull() ?: 0).takeIf { it > 0 } else null
            val healthMetric = if (bits.size > 11 && bits[11].isNotBlank()) bits[11] else null
            val healthAggregation = if (bits.size > 12 && bits[12].isNotBlank()) bits[12] else null
            val packageId = if (bits.size > 13 && bits[13].isNotBlank()) bits[13] else "user_created"
            Routine(id, title, icon, cat, target, progress, false, imageUri, packageId, objectiveType, targetSeconds, healthMetric, healthAggregation)
        } else {
            val icon = bits[2]
            val title = bits[3]
            val target = if (bits.size > 4) bits[4].toIntOrNull() ?: 1 else 1
            val progress = if (bits.size > 5) bits[5].toIntOrNull() ?: 0 else 0
            val imageUri = if (bits.size > 6 && bits[6].isNotBlank()) bits[6] else null
            val objectiveType = if (bits.size > 7) runCatching { RoutineObjectiveType.valueOf(bits[7]) }.getOrDefault(RoutineObjectiveType.COUNT) else RoutineObjectiveType.COUNT
            val targetSeconds = if (bits.size > 8) (bits[8].toIntOrNull() ?: 0).takeIf { it > 0 } else null
            val healthMetric = if (bits.size > 9 && bits[9].isNotBlank()) bits[9] else null
            val healthAggregation = if (bits.size > 10 && bits[10].isNotBlank()) bits[10] else null
            val packageId = if (bits.size > 11 && bits[11].isNotBlank()) bits[11] else "user_created"
            Routine(id, title, icon, cat, target, progress, false, imageUri, packageId, objectiveType, targetSeconds, healthMetric, healthAggregation)
        }
    }
}

fun parseIds(serialized: String?): Set<Int> {
    if (serialized.isNullOrBlank()) return emptySet()
    return serialized.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
}

fun serializeHistory(map: Map<Long, HistoryEntry>): String {
    return map.entries.joinToString(";") { (day, e) ->
        "$day|${e.done}|${e.total}|${if (e.allDone) 1 else 0}"
    }
}

fun parseHistory(raw: String): Map<Long, HistoryEntry> {
    if (raw.isBlank()) return emptyMap()
    val out = mutableMapOf<Long, HistoryEntry>()
    raw.split(";").forEach { part ->
        val bits = part.split("|")
        if (bits.size < 4) return@forEach
        val day = bits[0].toLongOrNull() ?: return@forEach
        val done = bits[1].toIntOrNull() ?: 0
        val total = bits[2].toIntOrNull() ?: 0
        val allDone = (bits[3].toIntOrNull() ?: 0) == 1
        out[day] = HistoryEntry(done = done, total = total, allDone = allDone)
    }
    return out
}

fun serializeSalaryProfile(profile: SalaryProfile): String = gson.toJson(profile)

fun deserializeSalaryProfile(raw: String?): SalaryProfile? {
    if (raw.isNullOrBlank()) return null
    return runCatching { gson.fromJson(raw, SalaryProfile::class.java) }.getOrNull()
}

private const val PURCHASE_TRANSACTION_LIMIT = 1000

fun serializePurchaseTransactions(list: List<PurchaseTransaction>): String {
    return gson.toJson(list.takeLast(PURCHASE_TRANSACTION_LIMIT))
}

fun deserializePurchaseTransactions(raw: String?): List<PurchaseTransaction> {
    if (raw.isNullOrBlank()) return emptyList()
    val type = object : TypeToken<List<PurchaseTransaction>>() {}.type
    return runCatching { gson.fromJson<List<PurchaseTransaction>>(raw, type) }.getOrDefault(emptyList())
}

suspend fun saveSalaryProfile(context: Context, profile: SalaryProfile) {
    context.dataStore.edit { prefs ->
        prefs[Keys.SALARY_PROFILE] = serializeSalaryProfile(profile)
    }
}

suspend fun getSalaryProfile(context: Context): SalaryProfile? {
    val prefs = context.dataStore.data.first()
    return deserializeSalaryProfile(prefs[Keys.SALARY_PROFILE])
}

suspend fun addTransaction(context: Context, transaction: PurchaseTransaction): List<PurchaseTransaction> {
    val prefs = context.dataStore.data.first()
    val existing = deserializePurchaseTransactions(prefs[Keys.PURCHASE_TRANSACTIONS])
    val updated = (existing + transaction).takeLast(PURCHASE_TRANSACTION_LIMIT)
    context.dataStore.edit { next ->
        next[Keys.PURCHASE_TRANSACTIONS] = serializePurchaseTransactions(updated)
    }
    return updated
}

private fun currentCycleStartMillis(profile: SalaryProfile, nowMillis: Long = System.currentTimeMillis()): Long {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val startDay = profile.cycleStartDay.coerceIn(1, 28)
    val thisMonthStart = today.withDayOfMonth(startDay.coerceAtMost(today.lengthOfMonth()))
    val cycleStartDate: LocalDate = if (today.dayOfMonth >= thisMonthStart.dayOfMonth) {
        thisMonthStart
    } else {
        val previousMonth = today.minusMonths(1)
        previousMonth.withDayOfMonth(startDay.coerceAtMost(previousMonth.lengthOfMonth()))
    }
    return cycleStartDate.atStartOfDay(zone).toInstant().toEpochMilli()
}

fun calculateRemainingBalance(
    profile: SalaryProfile?,
    transactions: List<PurchaseTransaction>,
    nowMillis: Long = System.currentTimeMillis()
): Double {
    if (profile == null) return 0.0
    val cycleStart = currentCycleStartMillis(profile, nowMillis)
    val monthlySpent = transactions
        .asSequence()
        .filter { it.purchasedAtEpoch >= cycleStart }
        .sumOf { it.amount }
    return profile.monthlyIncome - monthlySpent
}

fun calculateCycleSpent(
    profile: SalaryProfile?,
    transactions: List<PurchaseTransaction>,
    nowMillis: Long = System.currentTimeMillis()
): Double {
    if (profile == null) return 0.0
    val cycleStart = currentCycleStartMillis(profile, nowMillis)
    return transactions
        .asSequence()
        .filter { it.purchasedAtEpoch >= cycleStart }
        .sumOf { it.amount }
}

fun calculateCycleTransactionCount(
    profile: SalaryProfile?,
    transactions: List<PurchaseTransaction>,
    nowMillis: Long = System.currentTimeMillis()
): Int {
    if (profile == null) return 0
    val cycleStart = currentCycleStartMillis(profile, nowMillis)
    return transactions.count { it.purchasedAtEpoch >= cycleStart }
}

enum class CatalogPurchaseFailure {
    INVALID_PRICE,
    ITEM_NOT_FOUND,
    OUT_OF_STOCK,
    SALARY_NOT_SET,
    INSUFFICIENT_BALANCE
}

data class CatalogPurchaseResult(
    val success: Boolean,
    val failure: CatalogPurchaseFailure? = null,
    val purchasedItem: CatalogItem? = null,
    val updatedCatalogItems: List<CatalogItem> = emptyList(),
    val updatedTransactions: List<PurchaseTransaction> = emptyList(),
    val updatedBalance: Int = 0,
    val remainingBudget: Double? = null
)

suspend fun performCatalogPurchase(
    context: Context,
    itemId: String,
    requireSalaryProfile: Boolean = true,
    nowMillis: Long = System.currentTimeMillis()
): CatalogPurchaseResult {
    var result = CatalogPurchaseResult(success = false)
    context.dataStore.edit { prefs ->
        val catalog = deserializeCatalogItems(prefs[Keys.CATALOG_ITEMS])
        val idx = catalog.indexOfFirst { it.id == itemId }
        if (idx < 0) {
            result = CatalogPurchaseResult(success = false, failure = CatalogPurchaseFailure.ITEM_NOT_FOUND)
            return@edit
        }
        val item = catalog[idx]
        if (item.cost <= 0) {
            result = CatalogPurchaseResult(success = false, failure = CatalogPurchaseFailure.INVALID_PRICE)
            return@edit
        }
        if (item.stock <= 0) {
            result = CatalogPurchaseResult(success = false, failure = CatalogPurchaseFailure.OUT_OF_STOCK)
            return@edit
        }

        val profile = deserializeSalaryProfile(prefs[Keys.SALARY_PROFILE])
        val existingTransactions = deserializePurchaseTransactions(prefs[Keys.PURCHASE_TRANSACTIONS])
        val currentBalance = prefs[Keys.BALANCE] ?: 0
        if (requireSalaryProfile && profile == null) {
            result = CatalogPurchaseResult(
                success = false,
                failure = CatalogPurchaseFailure.SALARY_NOT_SET,
                purchasedItem = item,
                updatedCatalogItems = catalog,
                updatedTransactions = existingTransactions,
                updatedBalance = currentBalance,
                remainingBudget = null
            )
            return@edit
        }

        val available = if (profile != null) {
            calculateRemainingBalance(profile, existingTransactions, nowMillis)
        } else {
            currentBalance.toDouble()
        }
        if (available < item.cost.toDouble()) {
            result = CatalogPurchaseResult(
                success = false,
                failure = CatalogPurchaseFailure.INSUFFICIENT_BALANCE,
                purchasedItem = item,
                updatedCatalogItems = catalog,
                updatedTransactions = existingTransactions,
                updatedBalance = currentBalance,
                remainingBudget = if (profile != null) available else null
            )
            return@edit
        }

        val updatedCatalog = catalog.toMutableList()
        val purchasedItem = item.copy(stock = (item.stock - 1).coerceAtLeast(0))
        updatedCatalog[idx] = purchasedItem
        prefs[Keys.CATALOG_ITEMS] = serializeCatalogItems(updatedCatalog)

        var updatedBalance = currentBalance
        var updatedTransactions = existingTransactions
        var remainingBudget: Double? = null
        if (profile != null) {
            val tx = PurchaseTransaction(
                id = UUID.randomUUID().toString(),
                itemId = item.id,
                itemName = item.name,
                amount = item.cost.toDouble(),
                purchasedAtEpoch = nowMillis
            )
            updatedTransactions = (existingTransactions + tx).takeLast(PURCHASE_TRANSACTION_LIMIT)
            prefs[Keys.PURCHASE_TRANSACTIONS] = serializePurchaseTransactions(updatedTransactions)
            remainingBudget = calculateRemainingBalance(profile, updatedTransactions, nowMillis)
        } else {
            updatedBalance = (currentBalance - item.cost).coerceAtLeast(0)
            prefs[Keys.BALANCE] = updatedBalance
        }

        result = CatalogPurchaseResult(
            success = true,
            purchasedItem = purchasedItem,
            updatedCatalogItems = updatedCatalog,
            updatedTransactions = updatedTransactions,
            updatedBalance = updatedBalance,
            remainingBudget = remainingBudget
        )
    }
    return result
}

/* ===================== UPDATED SERIALIZERS (With Package ID) ===================== */

fun serializeCustomTemplates(list: List<CustomTemplate>): String {
    return list.joinToString(";;") { t ->
        val safeTitle = t.title.replace("|", " ").replace(";;", " ")
        val safeIcon = t.icon.replace("|", " ").replace(";;", " ")
        val safeUri = (t.imageUri ?: "").replace("|", " ").replace(";;", " ")
        val safeObjective = t.objectiveType.name
        val safeTargetSeconds = t.targetSeconds ?: 0
        val safeHealthMetric = t.healthMetric.orEmpty().replace("|", " ").replace(";;", " ")
        val safeHealthAggregation = t.healthAggregation.orEmpty().replace("|", " ").replace(";;", " ")
        "${t.id}|${t.category.name}|$safeIcon|$safeTitle|${if(t.isPinned) 1 else 0}|${t.target}|$safeUri|${t.packageId}|${if(t.isActive) 1 else 0}|$safeObjective|$safeTargetSeconds|$safeHealthMetric|$safeHealthAggregation"
    }
}

fun deserializeCustomTemplates(serialized: String): List<CustomTemplate> {
    if (serialized.isBlank()) return emptyList()
    return serialized.split(";;").mapNotNull { part ->
        val bits = part.split("|")
        if (bits.size < 6) return@mapNotNull null
        val id = bits[0]
        val cat = runCatching { RoutineCategory.valueOf(bits[1]) }.getOrNull() ?: return@mapNotNull null
        val isLegacy = bits.size >= 15 && bits[2].toIntOrNull() != null
        if (isLegacy) {
            val icon = bits[4]
            val title = bits[5]
            val isPinned = if (bits.size > 6) (bits[6].toIntOrNull() ?: 0) == 1 else false
            val target = if (bits.size > 7) bits[7].toIntOrNull() ?: 1 else 1
            val imageUri = if (bits.size > 8 && bits[8].isNotBlank()) bits[8] else null
            val packageId = if (bits.size > 9 && bits[9].isNotBlank()) bits[9] else "user_created"
            val isActive = if (bits.size > 10) (bits[10].toIntOrNull() ?: 1) == 1 else true
            val objectiveType = if (bits.size > 11) runCatching { RoutineObjectiveType.valueOf(bits[11]) }.getOrDefault(RoutineObjectiveType.COUNT) else RoutineObjectiveType.COUNT
            val targetSeconds = if (bits.size > 12) (bits[12].toIntOrNull() ?: 0).takeIf { it > 0 } else null
            val healthMetric = if (bits.size > 13 && bits[13].isNotBlank()) bits[13] else null
            val healthAggregation = if (bits.size > 14 && bits[14].isNotBlank()) bits[14] else null
            CustomTemplate(id, cat, title, icon, target, isPinned, imageUri, packageId, isActive, objectiveType, targetSeconds, healthMetric, healthAggregation)
        } else {
            val icon = bits[2]
            val title = bits[3]
            val isPinned = if (bits.size > 4) (bits[4].toIntOrNull() ?: 0) == 1 else false
            val target = if (bits.size > 5) bits[5].toIntOrNull() ?: 1 else 1
            val imageUri = if (bits.size > 6 && bits[6].isNotBlank()) bits[6] else null
            val packageId = if (bits.size > 7 && bits[7].isNotBlank()) bits[7] else "user_created"
            val isActive = if (bits.size > 8) (bits[8].toIntOrNull() ?: 1) == 1 else true
            val objectiveType = if (bits.size > 9) runCatching { RoutineObjectiveType.valueOf(bits[9]) }.getOrDefault(RoutineObjectiveType.COUNT) else RoutineObjectiveType.COUNT
            val targetSeconds = if (bits.size > 10) (bits[10].toIntOrNull() ?: 0).takeIf { it > 0 } else null
            val healthMetric = if (bits.size > 11 && bits[11].isNotBlank()) bits[11] else null
            val healthAggregation = if (bits.size > 12 && bits[12].isNotBlank()) bits[12] else null
            CustomTemplate(id, cat, title, icon, target, isPinned, imageUri, packageId, isActive, objectiveType, targetSeconds, healthMetric, healthAggregation)
        }
    }
}
fun serializeMilestones(list: List<Milestone>): String {
    return list.joinToString(";;") { q ->
        val safeTitle = q.title.replace("|", " ").replace(";;", " ")
        val safeDesc = q.description.replace("|", " ").replace(";;", " ")
        val stepsStr = q.steps.joinToString("~") { it.replace("~", " ") }
        val safeIcon = q.icon.replace("|", " ").replace(";;", " ")
        val safeImageUri = (q.imageUri ?: "").replace("|", " ").replace(";;", " ")
        "${q.id}|${q.currentStep}|$safeTitle|$safeDesc|$stepsStr|${q.prerequisiteId ?: ""}|${q.packageId}|${if(q.isActive) 1 else 0}|$safeIcon|$safeImageUri"
    }
}

fun deserializeMilestones(raw: String): List<Milestone> {
    if (raw.isBlank()) return emptyList()
    return raw.split(";;").mapNotNull { part ->
        val bits = part.split("|")
        if (bits.size < 7) return@mapNotNull null
        val id = bits[0]
        val legacy = bits.size >= 13
        if (legacy) {
            val step = bits[2].toIntOrNull() ?: 0
            val title = bits[4]
            val desc = bits[5]
            val stepsRaw = bits[6]
            val steps = if (stepsRaw.isBlank()) emptyList() else stepsRaw.split("~")
            val prereq = if (bits.size > 8 && bits[8].isNotBlank()) bits[8] else null
            val packageId = if (bits.size > 9 && bits[9].isNotBlank()) bits[9] else "user_created"
            val isActive = if (bits.size > 10) (bits[10].toIntOrNull() ?: 1) == 1 else true
            val icon = if (bits.size > 11 && bits[11].isNotBlank()) bits[11] else "🏆"
            val imageUri = if (bits.size > 12 && bits[12].isNotBlank()) bits[12] else null
            Milestone(id, title, desc, steps, step, prereq, packageId, isActive, icon, imageUri)
        } else {
            val step = bits[1].toIntOrNull() ?: 0
            val title = bits[2]
            val desc = bits[3]
            val stepsRaw = bits[4]
            val steps = if (stepsRaw.isBlank()) emptyList() else stepsRaw.split("~")
            val prereq = if (bits.size > 5 && bits[5].isNotBlank()) bits[5] else null
            val packageId = if (bits.size > 6 && bits[6].isNotBlank()) bits[6] else "user_created"
            val isActive = if (bits.size > 7) (bits[7].toIntOrNull() ?: 1) == 1 else true
            val icon = if (bits.size > 8 && bits[8].isNotBlank()) bits[8] else "🏆"
            val imageUri = if (bits.size > 9 && bits[9].isNotBlank()) bits[9] else null
            Milestone(id, title, desc, steps, step, prereq, packageId, isActive, icon, imageUri)
        }
    }
}


suspend fun loadJournal(context: Context): List<JournalPage> {
    val prefs = context.dataStore.data.first()
    val raw = prefs[Keys.GRIMOIRE].orEmpty()
    if (raw.isBlank()) return emptyList()
    val jsonLoaded = runCatching {
        val type = object : TypeToken<List<JournalPage>>() {}.type
        gson.fromJson<List<JournalPage>>(raw, type)
    }.getOrNull()
    if (!jsonLoaded.isNullOrEmpty()) {
        return jsonLoaded.mapNotNull { page ->
            runCatching {
                val allUris = (page.voiceNoteUris + listOfNotNull(page.voiceNoteUri))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                val cleanMap = page.voiceNoteTranscripts
                    .mapNotNull { (k, v) ->
                        val key = k.trim()
                        val value = v.trim()
                        if (key.isBlank() || value.isBlank()) null else key to value
                    }
                    .toMap()
                    .filterKeys { allUris.contains(it) }
                val cleanSubmittedAt = page.voiceNoteSubmittedAt
                    .filterKeys { allUris.contains(it) }
                    .mapNotNull { (k, v) ->
                        val key = k.trim()
                        if (key.isBlank() || v <= 0L) null else key to v
                    }
                    .toMap()
                val cleanNames = page.voiceNoteNames
                    .filterKeys { allUris.contains(it) }
                    .mapNotNull { (k, v) ->
                        val key = k.trim()
                        val value = v.trim()
                        if (key.isBlank() || value.isBlank()) null else key to value
                    }
                    .toMap()
                page.copy(
                    voiceNoteUri = null,
                    voiceNoteUris = allUris,
                    voiceNoteSubmittedAt = cleanSubmittedAt,
                    voiceNoteNames = cleanNames,
                    voiceTranscript = null,
                    voiceNoteTranscripts = cleanMap
                )
            }.getOrNull()
        }
    }

    // Legacy fallback parser.
    return raw.split(";;").mapNotNull { part ->
        if (part.isBlank()) return@mapNotNull null
        val bits = part.split("|")
        if (bits.size < 3) return@mapNotNull null
        val day = bits[0].toLongOrNull() ?: return@mapNotNull null
        val edited = bits[1].toLongOrNull() ?: System.currentTimeMillis()
        if (bits.size >= 4) {
            val title = bits[2]
            val text = bits.subList(3, bits.size).joinToString("|")
            JournalPage(dateEpochDay = day, text = text, title = title, editedAtMillis = edited)
        } else {
            val text = bits.subList(2, bits.size).joinToString("|")
            JournalPage(dateEpochDay = day, text = text, title = "", editedAtMillis = edited)
        }
    }
}

suspend fun persistJournal(context: Context, pages: List<JournalPage>) {
    val serialized = gson.toJson(pages.take(365))
    context.dataStore.edit { prefs -> prefs[Keys.GRIMOIRE] = serialized }
}

/* ===================== GSON FOR IMPORT/EXPORT ===================== */

fun exportRoutinePool(templates: List<RoutineTemplate>): String {
    return gson.toJson(RoutinePool(templates))
}

fun importRoutinePool(json: String): List<RoutineTemplate> {
    return try {
        gson.fromJson(json, RoutinePool::class.java).templates
    } catch (e: Exception) {
        emptyList()
    }
}

// ===================== TEMPLATE SHARING (GSON) =====================

// ===================== TEMPLATE SHARING (COMPRESSED) =====================

fun exportGameTemplate(template: GameTemplate): String {
    return try {
        val json = gson.toJson(template)
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        // URL_SAFE makes sure there are no weird characters that break WhatsApp links!
        Base64.encodeToString(bos.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
    } catch (e: Exception) {
        ""
    }
}

private fun sanitizeRoutineTemplate(q: RoutineTemplate, fallbackPackageId: String): RoutineTemplate {
    val safePackage = q.packageId.ifBlank { fallbackPackageId }
    val objectiveType = q.objectiveType
    val safeTargetSeconds = q.targetSeconds?.coerceIn(30, 24 * 60 * 60)
    val safeHealthMetric = q.healthMetric?.trim()?.take(32)?.takeIf { it.isNotBlank() }
    val safeHealthAggregation = q.healthAggregation?.trim()?.take(32)?.takeIf { it.isNotBlank() }
    return q.copy(
        title = q.title.trim().take(64).ifBlank { "Untitled Routine" },
        icon = q.icon.trim().ifBlank { "✅" }.take(4),
        target = q.target.coerceIn(1, 500),
        imageUri = q.imageUri?.takeIf { it.isNotBlank() },
        packageId = safePackage,
        objectiveType = objectiveType,
        targetSeconds = safeTargetSeconds,
        healthMetric = safeHealthMetric,
        healthAggregation = safeHealthAggregation
    )
}

private fun sanitizeMilestone(
    q: Milestone,
    validIds: Set<String>,
    fallbackPackageId: String
): Milestone {
    val safeSteps = q.steps.map { it.trim().take(64) }.filter { it.isNotBlank() }.ifEmpty {
        listOf("Preparation", "Execution", "Completion")
    }.take(8)
    val safeCurrentStep = q.currentStep.coerceIn(0, safeSteps.size)
    val safePackage = q.packageId.ifBlank { fallbackPackageId }
    val safePrereq = q.prerequisiteId?.takeIf { validIds.contains(it) && it != q.id }
    return q.copy(
        title = q.title.trim().take(64).ifBlank { "Untitled Main Routine" },
        description = q.description.trim().take(220),
        steps = safeSteps,
        currentStep = safeCurrentStep,
        prerequisiteId = safePrereq,
        packageId = safePackage,
        icon = q.icon.trim().ifBlank { "🏆" }.take(4),
        imageUri = q.imageUri?.takeIf { it.isNotBlank() }
    )
}

private fun sanitizeShopItem(s: CatalogItem, fallbackPackageId: String): CatalogItem {
    val baseId = s.id.trim().ifBlank {
        s.name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { UUID.randomUUID().toString().take(12) }
    }
    val safeId = "${fallbackPackageId}_${baseId.take(36)}"
    val safeMax = s.maxStock.coerceIn(1, 99)
    return s.copy(
        id = safeId,
        name = s.name.trim().take(48).ifBlank { "Untitled Item" },
        icon = s.icon.trim().ifBlank { "🧩" }.take(4),
        description = s.description.trim().take(160),
        cost = s.cost.coerceIn(1, 20000),
        stock = s.stock.coerceIn(0, safeMax),
        maxStock = safeMax,
        imageUri = s.imageUri?.takeIf { it.isNotBlank() }
    )
}

private fun sanitizeTemplateSettings(settings: TemplateSettings?): TemplateSettings? {
    if (settings == null) return null
    return settings.copy(
        neonFlowSpeed = settings.neonFlowSpeed.coerceIn(0, 2),
        dailyResetHour = settings.dailyResetHour.coerceIn(0, 23),
        fontScalePercent = settings.fontScalePercent.coerceIn(80, 125),
        backgroundImageTransparencyPercent = settings.backgroundImageTransparencyPercent?.coerceIn(0, 100),
        backgroundImageUri = settings.backgroundImageUri?.takeIf { it.isNotBlank() }
    )
}

fun normalizeGameTemplateSafe(template: GameTemplate): GameTemplate {
    val safePackageId = template.packageId.ifBlank { UUID.randomUUID().toString() }
    val safeMainRaw = template.milestones
        .distinctBy { it.id.ifBlank { it.title.lowercase() } }
        .map { it.copy(id = it.id.ifBlank { UUID.randomUUID().toString() }) }
    val validMainIds = safeMainRaw.map { it.id }.toSet()
    val safeMain = safeMainRaw.map { sanitizeMilestone(it, validMainIds, safePackageId) }
    val safeDaily = template.dailyRoutines
        .distinctBy { it.title.trim().lowercase() }
        .map { sanitizeRoutineTemplate(it, safePackageId) }
    val safeShop = template.catalogItems
        .distinctBy { (it.id.ifBlank { it.name }).trim().lowercase() }
        .map { sanitizeShopItem(it, safePackageId) }
        .distinctBy { it.id }
    val safeTheme = runCatching { AppTheme.valueOf(template.appTheme.name) }.getOrDefault(AppTheme.DEFAULT)
    return template.copy(
        templateName = template.templateName.trim().take(60).ifBlank { "Imported Template" },
        appTheme = safeTheme,
        dailyRoutines = safeDaily,
        milestones = safeMain,
        catalogItems = safeShop,
        packageId = safePackageId,
        templateSettings = sanitizeTemplateSettings(template.templateSettings),
        accentArgb = template.accentArgb?.toInt()?.toLong()
    )
}

fun importGameTemplate(payload: String): GameTemplate? {
    val trimmed = payload.trim()
    if (trimmed.isBlank() || trimmed.length > MAX_TEMPLATE_PAYLOAD_CHARS) return null
    return try {
        val bytes = Base64.decode(trimmed, Base64.URL_SAFE or Base64.NO_WRAP)
        if (bytes.size > MAX_TEMPLATE_COMPRESSED_BYTES) return null
        val json = gunzipToUtf8Limited(bytes, MAX_TEMPLATE_JSON_BYTES) ?: return null
        gson.fromJson(json, GameTemplate::class.java)?.let { normalizeGameTemplateSafe(it) }
    } catch (e: Exception) {
        if (trimmed.length > MAX_TEMPLATE_JSON_BYTES) return null
        try {
            gson.fromJson(trimmed, GameTemplate::class.java)?.let { normalizeGameTemplateSafe(it) }
        } catch (e2: Exception) {
            null
        }
    }
}


fun serializeSavedTemplates(list: List<GameTemplate>): String {
    return try { gson.toJson(list) } catch(e: Exception) { "" }
}

fun deserializeSavedTemplates(json: String?): List<GameTemplate> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val type = object : com.google.gson.reflect.TypeToken<List<GameTemplate>>() {}.type
        val parsed: List<GameTemplate> = gson.fromJson(json, type) ?: emptyList()
        parsed.mapNotNull { runCatching { normalizeGameTemplateSafe(it) }.getOrNull() }
    } catch (e: Exception) {
        emptyList()
    }
}

fun serializeStringSet(values: Set<String>): String {
    return try { gson.toJson(values.toList()) } catch (e: Exception) { "[]" }
}

fun deserializeStringSet(json: String?): Set<String> {
    if (json.isNullOrBlank()) return emptySet()
    return try {
        val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
        (gson.fromJson<List<String>>(json, type) ?: emptyList()).toSet()
    } catch (e: Exception) {
        emptySet()
    }
}

fun serializeRatingsMap(map: Map<String, Int>): String {
    return try { gson.toJson(map) } catch (e: Exception) { "{}" }
}

fun deserializeRatingsMap(json: String?): Map<String, Int> {
    if (json.isNullOrBlank()) return emptyMap()
    return try {
        val type = object : com.google.gson.reflect.TypeToken<Map<String, Int>>() {}.type
        gson.fromJson<Map<String, Int>>(json, type) ?: emptyMap()
    } catch (e: Exception) {
        emptyMap()
    }
}

fun serializeCatalogItems(list: List<CatalogItem>): String {
    return try { gson.toJson(list) } catch (e: Exception) { "[]" }
}

fun deserializeCatalogItems(json: String?): List<CatalogItem> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val type = object : com.google.gson.reflect.TypeToken<List<CatalogItem>>() {}.type
        val raw = gson.fromJson<List<CatalogItem>>(json, type) ?: emptyList()
        raw.map { item ->
            val normalizedType = item.type ?: CatalogItemType.WANT
            val normalizedHistory = item.priceHistory
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(PricePoint(price = item.cost.coerceAtLeast(1), epoch = System.currentTimeMillis()))
            item.copy(
                isConsumable = true,
                type = normalizedType,
                priceHistory = normalizedHistory
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

fun serializeCalendarPlans(map: Map<Long, List<String>>): String {
    return try { gson.toJson(map) } catch (e: Exception) { "{}" }
}

suspend fun runDataMigrations(context: Context) {
    val prefs = context.dataStore.data.first()
    val fromVersion = prefs[Keys.DATA_VERSION] ?: 0
    if (fromVersion >= CURRENT_DATA_VERSION) return
    val legacyQuestsKey = stringPreferencesKey("quests_serialized")
    val legacyMainQuestsKey = stringPreferencesKey("main_quests_v1")
    val legacyShopItemsKey = stringPreferencesKey("shop_items_serialized")

    context.dataStore.edit { p ->
        if (fromVersion < 1) {
            // Community migration removed
        }
        if (fromVersion < 2) {
            val safeHour = (p[Keys.DAILY_RESET_HOUR] ?: 0).coerceIn(0, 23)
            p[Keys.DAILY_RESET_HOUR] = safeHour
            val safeScale = (p[Keys.FONT_SCALE_PERCENT] ?: 100).coerceIn(80, 140)
            p[Keys.FONT_SCALE_PERCENT] = safeScale
        }
        if (fromVersion < 3) {
            if (p[Keys.DAILY_REMINDERS_ENABLED] == null) {
                p[Keys.DAILY_REMINDERS_ENABLED] = true
            }
        }
        if (fromVersion < 5) {
            if (p[Keys.DAILY_ROUTINE_TARGET] == null) p[Keys.DAILY_ROUTINE_TARGET] = 5
            if (p[Keys.CLOUD_SYNC_ENABLED] == null) p[Keys.CLOUD_SYNC_ENABLED] = false
            if (p[Keys.CLOUD_ACCOUNT_EMAIL].isNullOrBlank()) p[Keys.CLOUD_ACCOUNT_EMAIL] = ""
            if (p[Keys.CLOUD_ACCESS_TOKEN].isNullOrBlank()) p[Keys.CLOUD_ACCESS_TOKEN] = ""
        }
        if (fromVersion < 6) {
            if (p[Keys.TUTORIAL_CATALOG] == null) p[Keys.TUTORIAL_CATALOG] = false
            if (p[Keys.TUTORIAL_CALENDAR] == null) p[Keys.TUTORIAL_CALENDAR] = false
            if (p[Keys.TUTORIAL_ROUTINES] == null) p[Keys.TUTORIAL_ROUTINES] = false
            if (p[Keys.CATALOG_HOLD_HINT_SEEN] == null) p[Keys.CATALOG_HOLD_HINT_SEEN] = false
        }
        if (fromVersion < 7) {
            if (p[Keys.ROUTINES].isNullOrBlank()) {
                val legacyRoutines = p[legacyQuestsKey]
                if (!legacyRoutines.isNullOrBlank()) p[Keys.ROUTINES] = legacyRoutines
            }
            if (p[Keys.MILESTONES].isNullOrBlank()) {
                val legacyMilestones = p[legacyMainQuestsKey]
                if (!legacyMilestones.isNullOrBlank()) p[Keys.MILESTONES] = legacyMilestones
            }
            if (p[Keys.CATALOG_ITEMS].isNullOrBlank()) {
                val legacyCatalog = p[legacyShopItemsKey]
                if (!legacyCatalog.isNullOrBlank()) p[Keys.CATALOG_ITEMS] = legacyCatalog
            }
        }
        p[Keys.DATA_VERSION] = CURRENT_DATA_VERSION
    }
}

fun deserializeCalendarPlans(json: String?): Map<Long, List<String>> {
    if (json.isNullOrBlank()) return emptyMap()
    return try {
        val type = object : com.google.gson.reflect.TypeToken<Map<String, List<String>>>() {}.type
        val raw = gson.fromJson<Map<String, List<String>>>(json, type) ?: emptyMap()
        raw.mapNotNull { (k, v) -> k.toLongOrNull()?.let { it to v.filter { s -> s.isNotBlank() } } }.toMap()
    } catch (e: Exception) {
        // Backward-compat with old single-plan format
        try {
            val oldType = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
            val old = gson.fromJson<Map<String, String>>(json, oldType) ?: emptyMap()
            old.mapNotNull { (k, v) -> k.toLongOrNull()?.let { it to listOf(v) } }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }
}


private fun backupKey(packageName: String, includeVersionName: Boolean): ByteArray {
    val material = if (includeVersionName) {
        "$packageName|questify_backup_v2|${BuildConfig.VERSION_NAME}"
    } else {
        "$packageName|questify_backup_v3"
    }
    return MessageDigest.getInstance("SHA-256").digest(material.toByteArray(StandardCharsets.UTF_8))
}

fun exportFullBackupEncrypted(payload: FullBackupPayload, packageName: String): String {
    val jsonBytes = runCatching { gson.toJson(payload).toByteArray(StandardCharsets.UTF_8) }.getOrNull() ?: return ""
    if (jsonBytes.size > MAX_BACKUP_JSON_BYTES) return ""
    val key = getOrCreateBackupKey() ?: return exportLegacyBackup(payload, packageName)
    return try {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(jsonBytes)
        val packed = iv + encrypted
        if (packed.size > MAX_BACKUP_PACKED_BYTES) return ""
        BACKUP_BLOB_PREFIX + Base64.encodeToString(packed, Base64.URL_SAFE or Base64.NO_WRAP)
    } catch (_: Exception) {
        ""
    }
}

fun importFullBackupEncrypted(blob: String, packageName: String): FullBackupPayload? {
    val trimmed = blob.trim()
    if (trimmed.isBlank() || trimmed.length > MAX_BACKUP_BLOB_CHARS) return null
    return if (trimmed.startsWith(BACKUP_BLOB_PREFIX)) {
        val encoded = trimmed.removePrefix(BACKUP_BLOB_PREFIX)
        importKeystoreBackup(encoded) ?: importLegacyBackup(encoded, packageName)
    } else {
        importLegacyBackup(trimmed, packageName)
    }
}

fun preferencesToStringMap(prefs: androidx.datastore.preferences.core.Preferences): Map<String, String> {
    return prefs.asMap().mapKeys { it.key.name }.mapValues { (_, v) -> v?.toString().orEmpty() }
}

fun parseBackupMap(json: String): Map<String, String> {
    return runCatching {
        val type = object : TypeToken<Map<String, String>>() {}.type
        gson.fromJson<Map<String, String>>(json, type) ?: emptyMap()
    }.getOrDefault(emptyMap())
}

fun isEncryptedAuthState(value: String): Boolean {
    return value.startsWith(AUTH_STATE_BLOB_PREFIX)
}

fun encryptAuthState(value: String): String {
    if (value.isBlank()) return ""
    val key = getOrCreateAuthStateKey() ?: return value
    return runCatching {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        AUTH_STATE_BLOB_PREFIX + Base64.encodeToString(iv + encrypted, Base64.URL_SAFE or Base64.NO_WRAP)
    }.getOrDefault(value)
}

fun decryptAuthState(value: String): String {
    if (value.isBlank()) return ""
    if (!isEncryptedAuthState(value)) return value
    val encoded = value.removePrefix(AUTH_STATE_BLOB_PREFIX)
    val packed = runCatching { Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP) }.getOrNull() ?: return ""
    if (packed.size <= 12) return ""
    val key = getOrCreateAuthStateKey() ?: return ""
    return runCatching {
        val iv = packed.copyOfRange(0, 12)
        val encrypted = packed.copyOfRange(12, packed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }.getOrDefault("")
}

private fun gunzipToUtf8Limited(compressed: ByteArray, maxBytes: Int): String? {
    if (compressed.size > MAX_TEMPLATE_COMPRESSED_BYTES) return null
    return try {
        GZIPInputStream(ByteArrayInputStream(compressed)).use { gzip ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = gzip.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) return null
                out.write(buffer, 0, read)
            }
            String(out.toByteArray(), StandardCharsets.UTF_8)
        }
    } catch (_: Exception) {
        null
    }
}

private fun getOrCreateBackupKey(): SecretKey? {
    return runCatching {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val existing = ks.getKey(BACKUP_KEY_ALIAS, null) as? SecretKey
        if (existing != null) return@runCatching existing
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            BACKUP_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        keyGenerator.generateKey()
    }.getOrNull()
}

private fun getOrCreateAuthStateKey(): SecretKey? {
    return runCatching {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val existing = ks.getKey(AUTH_STATE_KEY_ALIAS, null) as? SecretKey
        if (existing != null) return@runCatching existing
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            AUTH_STATE_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        keyGenerator.generateKey()
    }.getOrNull()
}

private fun parseFullBackupPayload(jsonBytes: ByteArray): FullBackupPayload? {
    if (jsonBytes.size > MAX_BACKUP_JSON_BYTES) return null
    val json = String(jsonBytes, StandardCharsets.UTF_8)
    return runCatching { gson.fromJson(json, FullBackupPayload::class.java) }.getOrNull()
}

private fun importKeystoreBackup(encoded: String): FullBackupPayload? {
    val packed = runCatching { Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP) }.getOrNull() ?: return null
    if (packed.size <= 12 || packed.size > MAX_BACKUP_PACKED_BYTES) return null
    val key = getOrCreateBackupKey() ?: return null
    return try {
        val iv = packed.copyOfRange(0, 12)
        val encrypted = packed.copyOfRange(12, packed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        parseFullBackupPayload(cipher.doFinal(encrypted))
    } catch (_: Exception) {
        null
    }
}

private fun exportLegacyBackup(payload: FullBackupPayload, packageName: String): String {
    return runCatching {
        val json = gson.toJson(payload)
        val jsonBytes = json.toByteArray(StandardCharsets.UTF_8)
        if (jsonBytes.size > MAX_BACKUP_JSON_BYTES) return@runCatching ""
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = SecretKeySpec(backupKey(packageName, includeVersionName = false).copyOf(16), "AES")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(jsonBytes)
        val packed = iv + encrypted
        if (packed.size > MAX_BACKUP_PACKED_BYTES) return@runCatching ""
        Base64.encodeToString(packed, Base64.URL_SAFE or Base64.NO_WRAP)
    }.getOrDefault("")
}

private fun importLegacyBackup(blob: String, packageName: String): FullBackupPayload? {
    val packed = runCatching { Base64.decode(blob, Base64.URL_SAFE or Base64.NO_WRAP) }.getOrNull() ?: return null
    if (packed.size <= 12 || packed.size > MAX_BACKUP_PACKED_BYTES) return null
    val iv = packed.copyOfRange(0, 12)
    val encrypted = packed.copyOfRange(12, packed.size)

    val candidateKeys = listOf(
        SecretKeySpec(backupKey(packageName, includeVersionName = false).copyOf(16), "AES"),
        SecretKeySpec(backupKey(packageName, includeVersionName = true).copyOf(16), "AES")
    )
    for (key in candidateKeys) {
        val decoded = runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            parseFullBackupPayload(cipher.doFinal(encrypted))
        }.getOrNull()
        if (decoded != null) return decoded
    }
    return null
}

suspend fun completeNextRoutineFromExternalAction(context: Context): Boolean {
    val prefs = context.dataStore.data.first()
    val base = deserializeRoutines(prefs[Keys.ROUTINES].orEmpty())
    if (base.isEmpty()) return false

    val completed = parseIds(prefs[Keys.COMPLETED])
    val nextRoutine = base.firstOrNull { !completed.contains(it.id) } ?: return false
    val updatedCompleted = completed + nextRoutine.id

    val earned = parseIds(prefs[Keys.EARNED]).toMutableSet()
    if (!earned.contains(nextRoutine.id)) {
        earned += nextRoutine.id
    }

    val resetHour = (prefs[Keys.DAILY_RESET_HOUR] ?: 0).coerceIn(0, 23)
    val day = prefs[Keys.LAST_DAY] ?: epochDayNowAtHour(resetHour)
    val history = parseHistory(prefs[Keys.HISTORY].orEmpty()).toMutableMap()
    val total = base.size
    val done = updatedCompleted.size.coerceAtMost(total)
    history[day] = HistoryEntry(done = done, total = total, allDone = total > 0 && done == total)
    val trimmedHistory = history.toList()
        .sortedByDescending { it.first }
        .take(60)
        .associate { it.first to it.second }

    context.dataStore.edit { p ->
        p[Keys.COMPLETED] = updatedCompleted.joinToString(",")
        p[Keys.EARNED] = earned.joinToString(",")
        p[Keys.HISTORY] = serializeHistory(trimmedHistory)
    }
    return true
}




























