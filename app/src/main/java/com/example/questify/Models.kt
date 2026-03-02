package com.example.questify

import android.net.Uri
import com.google.gson.annotations.SerializedName

/* ===================== DATA MODELS ===================== */

enum class RoutineCategory { FITNESS, STUDY, HYDRATION, DISCIPLINE, MIND }

enum class RoutineObjectiveType { COUNT, TIMER, HEALTH }

enum class AppTheme { DEFAULT, LIGHT, CYBERPUNK }
enum class AppFontStyle {
    DEFAULT,
    SANS,
    SERIF,
    MONO,
    DISPLAY,
    ROUNDED,
    TERMINAL,
    ELEGANT,
    HANDWRITTEN
}
enum class OnboardingGoal { BALANCE, FITNESS, STUDY, DISCIPLINE, WELLNESS }

data class OnboardingSetup(
    val name: String,
    val avatar: String,
    val avatarImageUri: String? = null,
    val templateId: String,
    val theme: AppTheme = AppTheme.DEFAULT,
    val accentArgb: Long? = null,
    val goal: OnboardingGoal,
    val reminderHour: Int
)

data class TemplateSettings(
    val autoNewDay: Boolean = true,
    val confirmComplete: Boolean = true,
    val refreshIncompleteOnly: Boolean = true,
    val customMode: Boolean = false,
    val advancedOptions: Boolean = false,
    val highContrastText: Boolean = false,
    val compactMode: Boolean = false,
    val largerTouchTargets: Boolean = false,
    val reduceAnimations: Boolean = false,
    val decorativeBorders: Boolean = false,
    val neonLightBoost: Boolean = false,
    val neonFlowEnabled: Boolean = false,
    val neonFlowSpeed: Int = 0,
    val neonGlowPalette: String = "magenta",
    val alwaysShowRoutineProgress: Boolean = true,
    val hideCompletedRoutines: Boolean = false,
    val confirmDestructiveActions: Boolean = true,
    val dailyResetHour: Int = 0,
    val dailyRemindersEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val soundEffectsEnabled: Boolean = true,
    val fontStyle: AppFontStyle = AppFontStyle.DEFAULT,
    val fontScalePercent: Int = 100,
    val backgroundImageUri: String? = null,
    val backgroundVideoUri: String? = null,
    val backgroundType: String = "color", // color, image, video, gif
    val backgroundVideoMuted: Boolean = true,
    val backgroundImageTintEnabled: Boolean = true,
    val backgroundImageTransparencyPercent: Int? = null,
    val accentTransparencyPercent: Int? = null,
    val textTransparencyPercent: Int? = null,
    val appBgTransparencyPercent: Int? = null,
    val chromeBgTransparencyPercent: Int? = null,
    val cardBgTransparencyPercent: Int? = null,
    val journalPageTransparencyPercent: Int? = null,
    val journalAccentTransparencyPercent: Int? = null,
    val buttonTransparencyPercent: Int? = null,
    val textColorArgb: Long? = null,
    val appBackgroundArgb: Long? = null,
    val chromeBackgroundArgb: Long? = null,
    val cardColorArgb: Long? = null,
    val buttonColorArgb: Long? = null,
    val journalPageColorArgb: Long? = null,
    val journalAccentColorArgb: Long? = null,
    val journalName: String = "Journal"
)

data class Routine(
    val id: Int,
    val title: String,
    val icon: String,
    val category: RoutineCategory,
    val target: Int = 1,
    val currentProgress: Int = 0,
    val completed: Boolean = false,
    val imageUri: String? = null,
    val packageId: String = "user_created", // NEW: Active routines need this tag too!
    val objectiveType: RoutineObjectiveType = RoutineObjectiveType.COUNT,
    val targetSeconds: Int? = null,
    val healthMetric: String? = null,
    val healthAggregation: String? = null
) {
    // Legacy compatibility constructor for old serialized/call-site shape.
    constructor(
        id: Int,
        title: String,
        scoreReward: Int,
        icon: String,
        category: RoutineCategory,
        difficulty: Int,
        target: Int = 1,
        currentProgress: Int = 0,
        completed: Boolean = false,
        imageUri: String? = null,
        packageId: String = "user_created",
        objectiveType: RoutineObjectiveType = RoutineObjectiveType.COUNT,
        targetSeconds: Int? = null,
        healthMetric: String? = null,
        healthAggregation: String? = null
    ) : this(
        id = id,
        title = title,
        icon = icon,
        category = category,
        target = target,
        currentProgress = currentProgress,
        completed = completed,
        imageUri = imageUri,
        packageId = packageId,
        objectiveType = objectiveType,
        targetSeconds = targetSeconds,
        healthMetric = healthMetric,
        healthAggregation = healthAggregation
    )
}

// Updated for Gson serialization
data class RoutineTemplate(
    val category: RoutineCategory,
    val title: String,
    val icon: String,
    val target: Int = 1,
    val isPinned: Boolean = false,
    val imageUri: String? = null,
    val packageId: String = "user_created", // NEW: Templates need this tag!
    val objectiveType: RoutineObjectiveType = RoutineObjectiveType.COUNT,
    val targetSeconds: Int? = null,
    val healthMetric: String? = null,
    val healthAggregation: String? = null
) {
    // Legacy compatibility constructor for old template shape.
    constructor(
        category: RoutineCategory,
        difficulty: Int,
        title: String,
        icon: String,
        scoreReward: Int,
        target: Int = 1,
        isPinned: Boolean = false,
        imageUri: String? = null,
        packageId: String = "user_created",
        objectiveType: RoutineObjectiveType = RoutineObjectiveType.COUNT,
        targetSeconds: Int? = null,
        healthMetric: String? = null,
        healthAggregation: String? = null
    ) : this(
        category = category,
        title = title,
        icon = icon,
        target = target,
        isPinned = isPinned,
        imageUri = imageUri,
        packageId = packageId,
        objectiveType = objectiveType,
        targetSeconds = targetSeconds,
        healthMetric = healthMetric,
        healthAggregation = healthAggregation
    )
}

sealed class Avatar {
    data class Preset(val emoji: String) : Avatar()
    data class Custom(val uri: Uri) : Avatar()
}

// Pixel Character Data
data class CharacterData(
    val headColor: Long = 0xFFFACE8D, // Skin
    val bodyColor: Long = 0xFF3F51B5, // Shirt
    val legsColor: Long = 0xFF212121, // Pants
    val shoesColor: Long = 0xFF4E342E  // Shoes
)

// NEW: Non-Player Character
data class Npc(
    val id: String,
    val name: String,
    val icon: String, // Emoji
    val dialogue: List<String>
)

data class TutorialFlags(
    val seenInventoryHelp: Boolean = false,
    val seenMilestoneHelp: Boolean = false,
    val seenPoolHelp: Boolean = false
)

data class InventoryItem(
    val id: String,
    val name: String,
    val icon: String,
    val description: String,
    val cost: Int,
    val ownedCount: Int = 0,
    val isConsumable: Boolean = true
)

// NEW FINANCE MODELS
data class SalaryProfile(
    val monthlyIncome: Double = 0.0,
    val currencyCode: String = "USD", 
    val cycleStartDay: Int = 1
)

data class PurchaseTransaction(
    val id: String,
    val itemId: String? = null,
    val itemName: String,
    val amount: Double,
    val purchasedAtEpoch: Long
)

enum class CatalogItemType {
    NEED,
    WANT
}

data class PricePoint(
    val price: Int,
    val epoch: Long
)

data class CatalogItem(
    val id: String,
    val name: String,
    val icon: String,
    val description: String,
    val cost: Int,
    val stock: Int = 5,
    val maxStock: Int = 5,
    val isConsumable: Boolean = true,
    val imageUri: String? = null,
    val type: CatalogItemType? = null,
    val priceHistory: List<PricePoint>? = null
)

data class HistoryEntry(val done: Int, val total: Int, val allDone: Boolean)

// Daily Routine Template
data class CustomTemplate(
    val id: String,
    val category: RoutineCategory,
    val title: String,
    val icon: String,
    val target: Int = 1,
    val isPinned: Boolean = false,
    val imageUri: String? = null,
    val packageId: String = "user_created",
    val isActive: Boolean = true, // NEW: The Toggle Switch
    val objectiveType: RoutineObjectiveType = RoutineObjectiveType.COUNT,
    val targetSeconds: Int? = null,
    val healthMetric: String? = null,
    val healthAggregation: String? = null
) {
    // Legacy compatibility constructor for old custom template shape.
    constructor(
        id: String,
        category: RoutineCategory,
        difficulty: Int,
        title: String,
        icon: String,
        scoreReward: Int,
        target: Int = 1,
        isPinned: Boolean = false,
        imageUri: String? = null,
        packageId: String = "user_created",
        isActive: Boolean = true,
        objectiveType: RoutineObjectiveType = RoutineObjectiveType.COUNT,
        targetSeconds: Int? = null,
        healthMetric: String? = null,
        healthAggregation: String? = null
    ) : this(
        id = id,
        category = category,
        title = title,
        icon = icon,
        target = target,
        isPinned = isPinned,
        imageUri = imageUri,
        packageId = packageId,
        isActive = isActive,
        objectiveType = objectiveType,
        targetSeconds = targetSeconds,
        healthMetric = healthMetric,
        healthAggregation = healthAggregation
    )
}

// Milestone (formerly Main Routine)
data class Milestone(
    val id: String,
    val title: String,
    val description: String,
    val steps: List<String> = listOf("Preparation", "Execution", "Completion"),
    val currentStep: Int = 0,
    val prerequisiteId: String? = null,
    val packageId: String = "user_created",
    val isActive: Boolean = true, // NEW: The Toggle Switch
    val icon: String = "🏆",
    val imageUri: String? = null
) {
    // Legacy compatibility constructor for old milestone shape.
    constructor(
        id: String,
        title: String,
        description: String,
        scoreReward: Int,
        steps: List<String> = listOf("Preparation", "Execution", "Completion"),
        currentStep: Int = 0,
        isClaimed: Boolean = false,
        hasStarted: Boolean = false,
        prerequisiteId: String? = null,
        packageId: String = "user_created",
        isActive: Boolean = true,
        icon: String = "🏆",
        imageUri: String? = null
    ) : this(
        id = id,
        title = title,
        description = description,
        steps = steps,
        currentStep = currentStep,
        prerequisiteId = prerequisiteId,
        packageId = packageId,
        isActive = isActive,
        icon = icon,
        imageUri = imageUri
    )
}


// NEW: Wrapper for JSON export
data class RoutinePool(
    @SerializedName("Routine_templates") val templates: List<RoutineTemplate>
)

data class JournalPage(
    val dateEpochDay: Long,
    val text: String,
    val title: String = "",
    val editedAtMillis: Long = System.currentTimeMillis(),
    val voiceNoteUri: String? = null, // legacy single-note field
    val voiceNoteUris: List<String> = emptyList(),
    val voiceNoteSubmittedAt: Map<String, Long> = emptyMap(),
    val voiceNoteNames: Map<String, String> = emptyMap(),
    val voiceTranscript: String? = null, // legacy single-transcript field
    val voiceNoteTranscripts: Map<String, String> = emptyMap(),
    val richBlocks: List<JournalBlock> = emptyList(),
    val pageLayout: String = "standard"
)

data class JournalSpan(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val colorArgb: Long? = null,
    val fontScalePercent: Int? = null
)

data class JournalBlock(
    val text: String,
    val fontStyle: AppFontStyle = AppFontStyle.DEFAULT,
    val fontScalePercent: Int = 100,
    val colorArgb: Long? = null,
    val spans: List<JournalSpan> = emptyList()
)

data class RoutineTimerState(
    val RoutineId: Int,
    val startedAtMillis: Long? = null,
    val elapsedMillis: Long = 0L,
    val isRunning: Boolean = false
)

data class HealthDailySnapshot(
    val epochDay: Long,
    val steps: Int = 0,
    val avgHeartRate: Int? = null,
    val distanceMeters: Float = 0f,
    val caloriesKcal: Float = 0f,
    val source: String = "manual"
)

// Complete Game Template for Export/Import
data class GameTemplate(
    val templateName: String = "My Custom RPG",
    val appTheme: AppTheme = AppTheme.DEFAULT,
    val dailyRoutines: List<RoutineTemplate> = emptyList(),
    val milestones: List<Milestone> = emptyList(),
    val catalogItems: List<CatalogItem> = emptyList(),
    val packageId: String = java.util.UUID.randomUUID().toString(), // NEW: The Brand Tag
    val templateSettings: TemplateSettings? = null,
    val accentArgb: Long? = null,
    val isPremium: Boolean = false
)

data class FullBackupPayload(
    val generatedAtMillis: Long = System.currentTimeMillis(),
    val version: Int = CURRENT_DATA_VERSION,
    val dataStoreDump: Map<String, String> = emptyMap()
)

data class AdvancedTemplateGuide(
    val summary: String = "Edit daily_routines, main_routines, and optional shop_items with AI, then import this file in Settings > Advanced Templates.",
    val ai_prompt_example: String = "",
    val notes: List<String> = listOf(
        "Use category: FITNESS, STUDY, HYDRATION, DISCIPLINE, MIND",
        "target should be >= 1",
        "main Routine steps should be 1..8 items"
    )
)

data class AdvancedDailyRoutineEntry(
    val title: String = "",
    val category: String = "DISCIPLINE",
    val target: Int = 1,
    val icon: String = "✅",
    val pinned: Boolean = false,
    val image_uri: String? = null,
    val objective_type: String = "COUNT",
    val target_seconds: Int? = null,
    val health_metric: String? = null,
    val health_aggregation: String? = null
)

data class AdvancedMilestoneEntry(
    val ref: String = "",
    val title: String = "",
    val description: String = "",
    val steps: List<String> = listOf("Preparation", "Execution", "Completion"),
    val prerequisite_ref: String? = null,
    val icon: String = "🏆",
    val image_uri: String? = null
)

data class AdvancedCatalogItemEntry(
    val id: String? = null,
    val name: String = "",
    val icon: String = "🧩",
    val description: String = "",
    val cost: Int = 100,
    val stock: Int = 5,
    val max_stock: Int = 5,
    val consumable: Boolean = true,
    val image_uri: String? = null
)

data class AdvancedTemplateFile(
    val schema_version: Int = 2,
    val template_name: String = "AI Generated Template",
    val app_theme: String = "DEFAULT",
    val accent_argb: Long? = null,
    val ai_instructions: List<String> = listOf(
        "This JSON file is from the questify app.",
        "Read the user's Request and update daily_routines/main_routines accordingly.",
        "Keep top-level keys and structure unchanged.",
        "Return ONLY the updated JSON file content (no markdown, no explanation)."
    ),
    val guide: AdvancedTemplateGuide = AdvancedTemplateGuide(),
    val template_settings: TemplateSettings? = null,
    val daily_routines: List<AdvancedDailyRoutineEntry> = emptyList(),
    val main_routines: List<AdvancedMilestoneEntry> = emptyList(),
    val shop_items: List<AdvancedCatalogItemEntry> = emptyList()
)

data class AdvancedTemplateImportResult(
    val success: Boolean,
    val templateName: String,
    val dailyAdded: Int,
    val mainAdded: Int,
    val packageId: String? = null,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList()
)

























