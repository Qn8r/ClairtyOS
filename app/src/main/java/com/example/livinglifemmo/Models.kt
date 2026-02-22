package com.example.livinglifemmo

import android.net.Uri
import com.google.gson.annotations.SerializedName

/* ===================== DATA MODELS ===================== */

enum class QuestCategory { FITNESS, STUDY, HYDRATION, DISCIPLINE, MIND }

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
enum class DifficultyPreference { CHILL, NORMAL, HARDCORE }

data class OnboardingSetup(
    val name: String,
    val avatar: String,
    val avatarImageUri: String? = null,
    val templateId: String,
    val theme: AppTheme = AppTheme.DEFAULT,
    val accentArgb: Long? = null,
    val goal: OnboardingGoal,
    val difficultyPreference: DifficultyPreference,
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
    val alwaysShowQuestProgress: Boolean = true,
    val hideCompletedQuests: Boolean = false,
    val confirmDestructiveActions: Boolean = true,
    val dailyResetHour: Int = 0,
    val dailyRemindersEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val soundEffectsEnabled: Boolean = true,
    val fontStyle: AppFontStyle = AppFontStyle.DEFAULT,
    val fontScalePercent: Int = 100,
    val backgroundImageUri: String? = null,
    val textColorArgb: Long? = null,
    val appBackgroundArgb: Long? = null,
    val chromeBackgroundArgb: Long? = null,
    val cardColorArgb: Long? = null,
    val buttonColorArgb: Long? = null,
    val journalPageColorArgb: Long? = null,
    val journalAccentColorArgb: Long? = null,
    val journalName: String = "Journal"
)

data class Quest(
    val id: Int,
    val title: String,
    val icon: String,
    val category: QuestCategory,
    val difficulty: Int,
    val target: Int = 1,
    val currentProgress: Int = 0,
    val completed: Boolean = false,
    val imageUri: String? = null,
    val packageId: String = "user_created",
    val xpReward: Int = 0
)

data class QuestTemplate(
    val category: QuestCategory,
    val difficulty: Int,
    val title: String,
    val icon: String,
    val target: Int = 1,
    val isPinned: Boolean = false,
    val imageUri: String? = null,
    val packageId: String = "user_created",
    val xp: Int = 0
)

sealed class Avatar {
    data class Preset(val emoji: String) : Avatar()
    data class Custom(val uri: Uri) : Avatar()
}

data class TutorialFlags(
    val seenMainQuestHelp: Boolean = false,
    val seenPoolHelp: Boolean = false
)

data class HistoryEntry(val done: Int, val total: Int, val allDone: Boolean)

data class CustomTemplate(
    val id: String,
    val category: QuestCategory,
    val difficulty: Int,
    val title: String,
    val icon: String,
    val target: Int = 1,
    val isPinned: Boolean = false,
    val imageUri: String? = null,
    val packageId: String = "user_created",
    val isActive: Boolean = true,
    val xp: Int = 0
) {
    constructor(
        id: String,
        category: QuestCategory,
        difficulty: Int,
        title: String,
        icon: String,
        xp: Int,
        target: Int,
        isPinned: Boolean,
        imageUri: String?,
        packageId: String
    ) : this(
        id = id,
        category = category,
        difficulty = difficulty,
        title = title,
        icon = icon,
        target = target,
        isPinned = isPinned,
        imageUri = imageUri,
        packageId = packageId,
        isActive = true,
        xp = xp
    )
}

data class CustomMainQuest(
    val id: String,
    val title: String,
    val description: String,
    val steps: List<String> = listOf("Preparation", "Execution", "Completion"),
    val currentStep: Int = 0,
    val isClaimed: Boolean = false,
    val hasStarted: Boolean = false,
    val prerequisiteId: String? = null,
    val packageId: String = "user_created",
    val isActive: Boolean = true,
    val xpReward: Int = 0
)


// Wrapper for JSON export
data class QuestPool(
    @SerializedName("quest_templates") val templates: List<QuestTemplate>
)

data class JournalPage(
    val dateEpochDay: Long,
    val text: String,
    val title: String = "",
    val editedAtMillis: Long = System.currentTimeMillis(),
    val voiceNoteUri: String? = null,
    val voiceNoteUris: List<String> = emptyList(),
    val voiceNoteSubmittedAt: Map<String, Long> = emptyMap(),
    val voiceNoteNames: Map<String, String> = emptyMap(),
    val voiceTranscript: String? = null,
    val voiceNoteTranscripts: Map<String, String> = emptyMap()
)

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val unlocked: Boolean = false
)

data class CommunityPost(
    val id: String = java.util.UUID.randomUUID().toString(),
    val authorId: String,
    val authorName: String,
    val title: String,
    val description: String,
    val tags: List<String> = emptyList(),
    val template: GameTemplate,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val playerLevel: Int = 1,
    val ratingAverage: Double = 0.0,
    val ratingCount: Int = 0,
    val remixCount: Int = 0,
    val sourcePostId: String? = null
)

enum class CommunitySyncTaskType { PUBLISH_POST, FOLLOW_AUTHOR, UNFOLLOW_AUTHOR, RATE_POST, INCREMENT_REMIX }

data class CommunitySyncTask(
    val type: CommunitySyncTaskType,
    val post: CommunityPost? = null,
    val authorId: String? = null,
    val postId: String? = null,
    val stars: Int? = null,
    val currentRemixCount: Int? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val attemptCount: Int = 0
)

data class GameTemplate(
    val templateName: String = "My Template",
    val appTheme: AppTheme = AppTheme.DEFAULT,
    val dailyQuests: List<QuestTemplate> = emptyList(),
    val mainQuests: List<CustomMainQuest> = emptyList(),
    val shopItems: List<ShopItem> = emptyList(),
    val packageId: String = java.util.UUID.randomUUID().toString(),
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
    val summary: String = "Edit daily_quests and main_quests with AI, then import this file in Settings > Advanced Templates.",
    val ai_prompt_example: String = "Add 100 daily routines focused on fitness and deep work. Keep difficulty 1-5 and realistic targets.",
    val notes: List<String> = listOf(
        "Use category: FITNESS, STUDY, HYDRATION, DISCIPLINE, MIND",
        "difficulty must be 1..5",
        "target should be >= 1",
        "main quest steps should be 1..8 items"
    )
)

data class AdvancedDailyQuestEntry(
    val title: String = "",
    val category: String = "DISCIPLINE",
    val difficulty: Int = 2,
    val xp: Int = 0,
    val target: Int = 1,
    val icon: String = "",
    val pinned: Boolean = false,
    val image_uri: String? = null
)

data class AdvancedMainQuestEntry(
    val ref: String = "",
    val title: String = "",
    val description: String = "",
    val xp_reward: Int = 0,
    val steps: List<String> = listOf("Preparation", "Execution", "Completion"),
    val prerequisite_ref: String? = null
)

data class AdvancedTemplateFile(
    val schema_version: Int = 1,
    val template_name: String = "AI Generated Template",
    val app_theme: String = "DEFAULT",
    val accent_argb: Long? = null,
    val ai_instructions: List<String> = listOf(
        "This JSON file is from the ClarityOS app.",
        "Read the user's request and update daily_quests/main_quests accordingly.",
        "Keep top-level keys and structure unchanged.",
        "Return ONLY the updated JSON file content (no markdown, no explanation)."
    ),
    val guide: AdvancedTemplateGuide = AdvancedTemplateGuide(),
    val daily_quests: List<AdvancedDailyQuestEntry> = emptyList(),
    val main_quests: List<AdvancedMainQuestEntry> = emptyList()
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

data class LevelInfo(
    val level: Int = 1,
    val currentXpInLevel: Int = 0,
    val xpForNextLevel: Int = 1,
    val totalXp: Int = 0
)

data class PlayerAttributes(
    val str: Int = 0,
    val int: Int = 0,
    val vit: Int = 0,
    val end: Int = 0,
    val fth: Int = 0
)

data class Boss(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val icon: String = "",
    val currentHp: Int = 0,
    val totalHp: Int = 0,
    val expiresAtMillis: Long = 0L,
    val rewardGold: Int = 0
)

data class InventoryItem(
    val id: String = "",
    val name: String = "",
    val icon: String = "",
    val ownedCount: Int = 0
)

data class ShopItem(
    val id: String = "",
    val name: String = "",
    val icon: String = "",
    val cost: Int = 0,
    val stock: Int = 0,
    val maxStock: Int = 0,
    val imageUri: String? = null,
    val description: String = "",
    val isConsumable: Boolean = false
)

data class CharacterData(
    val headColor: Long = 0xFFFACE8DL,
    val bodyColor: Long = 0xFF3F51B5L,
    val legsColor: Long = 0xFF212121L,
    val shoesColor: Long = 0xFF4E342EL
)

