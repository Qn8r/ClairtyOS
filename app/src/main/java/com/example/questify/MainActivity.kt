package com.example.questify

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.gson.Gson
import androidx.compose.animation.*
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.decode.ImageDecoderDecoder
import coil.decode.GifDecoder
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val lang = newBase.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("selected_language", "system") ?: "system"
        val ctx = if (lang == "system") {
            newBase
        } else {
            val locale = Locale(lang)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            newBase.createConfigurationContext(config)
        }
        super.attachBaseContext(ctx)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            AppLog.e("Uncaught crash on thread=${t.name}", e)
            previous?.uncaughtException(t, e)
        }
        super.onCreate(savedInstanceState)
        // Hard lock IME behavior so keyboard overlays without relayout/pan of app content.
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        SoundManager.init(this)
        enableEdgeToEdge()
        setContent {
            AppRoot(appContext = this)
        }
    }
}

enum class Screen { HOME, MILESTONE, CALENDAR, ROUTINES, GRIMOIRE, STATS, INVENTORY, SETTINGS, ABOUT }
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
@Composable
fun AppRoot(appContext: Context) {
    val scope = rememberCoroutineScope()
    val systemPrefersDark = androidx.compose.foundation.isSystemInDarkTheme()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    fun getString(resId: Int, vararg formatArgs: Any): String = appContext.getString(resId, *formatArgs)
    fun categoryLabel(cat: RoutineCategory): String = when (cat) {
        RoutineCategory.FITNESS -> getString(R.string.cat_fitness)
        RoutineCategory.STUDY -> getString(R.string.cat_study)
        RoutineCategory.HYDRATION -> getString(R.string.cat_hydration)
        RoutineCategory.DISCIPLINE -> getString(R.string.cat_discipline)
        RoutineCategory.MIND -> getString(R.string.cat_mind)
    }

    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var swipeVisualProgress by remember { mutableFloatStateOf(0f) }
    BackHandler {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            screen != Screen.HOME -> screen = Screen.HOME
        }
    }

    // --- State Variables ---
    var journalPages by remember { mutableStateOf(emptyList<JournalPage>()) }
    var grimoirePageIndex by rememberSaveable { mutableIntStateOf(0) }
    var journalBookOpen by rememberSaveable { mutableStateOf(false) }
    var balance by remember { mutableIntStateOf(0) }
    var salaryProfile by remember { mutableStateOf<SalaryProfile?>(null) }
    var purchaseTransactions by remember { mutableStateOf(emptyList<PurchaseTransaction>()) }
    var financeDefaultItemType by remember { mutableStateOf(CatalogItemType.WANT) }
    var financeWarnThresholdPercent by remember { mutableIntStateOf(20) }
    var financeShowHistoryHints by remember { mutableStateOf(true) }
    val remainingBudgetExact = remember(salaryProfile, purchaseTransactions) {
        salaryProfile?.let { calculateRemainingBalance(it, purchaseTransactions) }
    }
    val displayedBalance = remember(balance, remainingBudgetExact) {
        remainingBudgetExact?.toInt() ?: balance
    }

// This loads the history data safely from the database
    val historyFlow = remember(appContext) {
        appContext.dataStore.data.map { prefs -> parseHistory(prefs[Keys.HISTORY].orEmpty()) }
    }
    val historyMapState = historyFlow.collectAsState(initial = emptyMap())

    val historyMap = historyMapState.value

    // Core Data
    var routines by remember { mutableStateOf(emptyList<Routine>()) }
    var avatar by remember { mutableStateOf<Avatar>(Avatar.Preset("🧑‍🚀")) }
    var characterData by remember { mutableStateOf(CharacterData()) }
    var inventory by remember { mutableStateOf(emptyList<InventoryItem>()) }
    var catalogItems by remember { mutableStateOf(emptyList<CatalogItem>()) }
    var calendarPlans by remember { mutableStateOf<Map<Long, List<String>>>(emptyMap()) }
    var lastDayEpoch by remember { mutableLongStateOf(epochDayNowAtHour(0)) }
    var refreshCount by remember { mutableIntStateOf(0) }
    var homeRefreshInProgress by remember { mutableStateOf(false) }
    var earnedIds by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // Custom Main routines
    var milestones by remember { mutableStateOf(emptyList<Milestone>()) }

    var autoNewDay by remember { mutableStateOf(true) }
    var confirmComplete by remember { mutableStateOf(true) }
    var refreshIncompleteOnly by remember { mutableStateOf(true) }
    var customMode by remember { mutableStateOf(false) }
    var advancedOptions by remember { mutableStateOf(false) }
    var highContrastText by remember { mutableStateOf(false) }
    var compactMode by remember { mutableStateOf(false) }
    var largerTouchTargets by remember { mutableStateOf(false) }
    var reduceAnimations by remember { mutableStateOf(false) }
    var decorativeBorders by remember { mutableStateOf(false) }
    var neonLightBoost by remember { mutableStateOf(false) }
    var neonFlowEnabled by remember { mutableStateOf(false) }
    var neonFlowSpeed by remember { mutableIntStateOf(0) }
    var neonGlowPalette by remember { mutableStateOf("magenta") }
    var alwaysShowRoutineProgress by remember { mutableStateOf(true) }
    var hideCompletedRoutines by remember { mutableStateOf(false) }
    var confirmDestructiveActions by remember { mutableStateOf(true) }
    var dailyResetHour by remember { mutableIntStateOf(0) }
    var dailyRemindersEnabled by remember { mutableStateOf(true) }
    var hapticsEnabled by remember { mutableStateOf(true) }
    var soundEffectsEnabled by remember { mutableStateOf(true) }
    var fontStyle by remember { mutableStateOf(AppFontStyle.DEFAULT) }
    var fontScalePercent by remember { mutableIntStateOf(100) }
    var appLanguage by remember { mutableStateOf("system") }
    var backgroundImageUri by remember { mutableStateOf<String?>(null) }
    var backgroundVideoUri by remember { mutableStateOf<String?>(null) }
    var backgroundType by remember { mutableStateOf("color") }
    var backgroundVideoMuted by remember { mutableStateOf(true) }
    var backgroundImageTintEnabled by remember { mutableStateOf(true) }
    var backgroundImageTransparencyPercent by remember { mutableIntStateOf(78) }
    var accentTransparencyPercent by remember { mutableIntStateOf(0) }
    var textTransparencyPercent by remember { mutableIntStateOf(0) }
    var appBgTransparencyPercent by remember { mutableIntStateOf(0) }
    var chromeBgTransparencyPercent by remember { mutableIntStateOf(0) }
    var cardBgTransparencyPercent by remember { mutableIntStateOf(0) }
    var journalPageTransparencyPercent by remember { mutableIntStateOf(0) }
    var journalAccentTransparencyPercent by remember { mutableIntStateOf(0) }
    var buttonTransparencyPercent by remember { mutableIntStateOf(0) }
    var textColorOverride by remember { mutableStateOf<Color?>(null) }
    var appTheme by remember { mutableStateOf(if (systemPrefersDark) AppTheme.DEFAULT else AppTheme.LIGHT) }
    var accent by remember { mutableStateOf(AccentBurntOrange) }
    var appBackgroundColorOverride by remember { mutableStateOf<Color?>(null) }
    var chromeBackgroundColorOverride by remember { mutableStateOf<Color?>(null) }
    var cardColorOverride by remember { mutableStateOf<Color?>(null) }
    var buttonColorOverride by remember { mutableStateOf<Color?>(null) }
    var journalPageColorOverride by remember { mutableStateOf<Color?>(null) }
    var journalAccentColorOverride by remember { mutableStateOf<Color?>(null) }
    var journalName by remember { mutableStateOf("Journal") }
    var playerName by remember { mutableStateOf("Player") }
    var isLoggedIn by remember { mutableStateOf(false) }
    var authAccessToken by remember { mutableStateOf("") }
    var authRefreshToken by remember { mutableStateOf("") }
    var authUserEmail by remember { mutableStateOf("") }
    var authUserId by remember { mutableStateOf("") }
    var showLoginRequiredDialog by remember { mutableStateOf(false) }
    val runtimeTheme = remember(appTheme, appBackgroundColorOverride) {
        appBackgroundColorOverride?.let {
            if (it.luminance() >= 0.56f) {
                AppTheme.LIGHT
            } else if (appTheme == AppTheme.CYBERPUNK) {
                AppTheme.CYBERPUNK
            } else {
                AppTheme.DEFAULT
            }
        } ?: appTheme
    }
    val baseThemeBg = ThemeEngine.getColors(runtimeTheme).second
    val themeBg = appBackgroundColorOverride ?: baseThemeBg
    val appBgAlpha = (1f - (appBgTransparencyPercent.coerceIn(0, 100) / 100f)).coerceIn(0f, 1f)
    val chromeBgAlpha = (1f - (chromeBgTransparencyPercent.coerceIn(0, 100) / 100f)).coerceIn(0f, 1f)
    val isThemeBgLight = remember(themeBg) { themeBg.luminance() >= 0.56f }
    val accentStrong = buttonColorOverride ?: accent
    val accentSoft = remember(accentStrong, themeBg) { mixForBackground(accentStrong, themeBg) }
    val defaultChromeBg = remember(themeBg, isThemeBgLight) {
        if (isThemeBgLight) {
            mixForBackground(Color(0xFFD2DEEE), themeBg).copy(alpha = 0.98f)
        } else {
            mixForBackground(Color(0xFF060A11), themeBg).copy(alpha = 0.97f)
        }
    }
    val drawerBg = remember(defaultChromeBg, chromeBackgroundColorOverride, chromeBgAlpha) {
        (chromeBackgroundColorOverride ?: defaultChromeBg).copy(alpha = chromeBgAlpha)
    }
    val navBarBg = remember(drawerBg, chromeBackgroundColorOverride, isThemeBgLight, backgroundImageUri, backgroundImageTransparencyPercent, chromeBgAlpha) {
        val imageBlend = if (backgroundImageUri.isNullOrBlank()) 0f else (backgroundImageTransparencyPercent.coerceIn(0, 100) / 100f)
        val targetAlpha = (0.96f - (imageBlend * 0.18f)).coerceIn(0.74f, 0.96f)
        chromeBackgroundColorOverride ?: if (isThemeBgLight) {
            mixForBackground(Color(0xFFBDCCE3), drawerBg).copy(alpha = targetAlpha * chromeBgAlpha)
        } else {
            mixForBackground(Color(0xFF03060B), drawerBg).copy(alpha = targetAlpha * chromeBgAlpha)
        }
    }
    val drawerContentColor = remember(drawerBg) {
        if (drawerBg.luminance() >= 0.5f) Color(0xFF1B2430) else Color(0xFFE8EAF0)
    }
    val navContentColor = remember(navBarBg) {
        if (navBarBg.luminance() >= 0.5f) Color(0xFF1B2430) else Color(0xFFE8EAF0)
    }

    var showResetAll by remember { mutableStateOf(false) }
    var showRefreshDayConfirm by remember { mutableStateOf(false) }
    var customTemplates by remember { mutableStateOf<List<CustomTemplate>>(emptyList()) }
    var pendingUncheckRoutineId by remember { mutableStateOf<Int?>(null) }
    var showFocusTimer by remember { mutableStateOf(false) }
    var timerPersistJob by remember { mutableStateOf<Job?>(null) }
    var resetBackupBefore by remember { mutableStateOf(false) }
    var resetBackupName by remember { mutableStateOf("Pre-reset backup") }
    var pendingImportTemplate by remember { mutableStateOf<GameTemplate?>(null) }
    var savedTemplates by remember { mutableStateOf<List<GameTemplate>>(emptyList()) }
    var onboardingGoal by remember { mutableStateOf(OnboardingGoal.BALANCE) }
    var premiumUnlocked by remember { mutableStateOf(false) }
    var dailyRoutineTarget by remember { mutableIntStateOf(5) }
    var settingsExpandedSection by rememberSaveable { mutableStateOf("hub") }
    var financeFocusRequestNonce by rememberSaveable { mutableIntStateOf(0) }
    var RoutinesPreferredTab by rememberSaveable { mutableIntStateOf(0) }
    var pendingHomeEditDailyTemplateId by rememberSaveable { mutableStateOf<String?>(null) }
    var cloudSyncEnabled by remember { mutableStateOf(true) }
    var cloudAccountEmail by remember { mutableStateOf("") }
    var cloudConnectedAccount by remember { mutableStateOf<GoogleSignInAccount?>(null) }
    var cloudLastSyncAt by remember { mutableLongStateOf(0L) }
    var cloudLastAutoAttemptAt by remember { mutableLongStateOf(0L) }
    var promptApplyTemplate by remember { mutableStateOf<GameTemplate?>(null) }
    var importBackupBeforeApply by remember { mutableStateOf(true) }
    var importBackupName by remember { mutableStateOf("Backup") }
    var importClearExisting by remember { mutableStateOf(true) } // NEW: Option to wipe old routines
    var showIntroSplash by remember { mutableStateOf(true) }
    var showWelcomeSetup by remember { mutableStateOf(false) }
    var onboardingSkipIntroDefault by remember { mutableStateOf(false) }
    var shopTutorialSeen by remember { mutableStateOf(false) }
    var calendarTutorialSeen by remember { mutableStateOf(false) }
    var RoutinesTutorialSeen by remember { mutableStateOf(false) }
    var shopHoldHintSeen by remember { mutableStateOf(false) }
    var showBackupImport by remember { mutableStateOf(false) }
    var backupImportPayload by remember { mutableStateOf("") }
    var schemaDowngradeDetected by remember { mutableStateOf(false) }
    var healthDailySnapshot by remember { mutableStateOf<HealthDailySnapshot?>(null) }
    // --- Persistence Functions ---
    val defaultSystemPackageId = remember { getDefaultGameTemplate().packageId }
    val legacySystemPackageIds = remember {
        setOf(
            "default_pack",
            REAL_DAILY_LIFE_PACKAGE_ID,
            REAL_WORLD_MOMENTUM_PACKAGE_ID,
            "saitama_v1",
            Routine_FEATURE_TEST_PACKAGE_ID
        )
    }
    var activePackageIds by remember { mutableStateOf(setOf(defaultSystemPackageId)) } // NEW
    val activePacksKey = stringPreferencesKey("active_packs") // NEW
    fun currentEpochDay(): Long = epochDayNowAtHour(dailyResetHour)
    fun isRealDailyLifeTemplateActive(): Boolean = activePackageIds.contains(defaultSystemPackageId)
    fun daySeedForGeneration(day: Long): Long = if (isRealDailyLifeTemplateActive()) (day / 7L) else day
    fun secondSeedForGeneration(seconds: Long): Long = if (isRealDailyLifeTemplateActive()) (currentEpochDay() / 7L) else seconds
    fun normalizeTheme(theme: AppTheme): AppTheme = when (theme) {
        AppTheme.LIGHT -> AppTheme.LIGHT
        AppTheme.CYBERPUNK -> AppTheme.CYBERPUNK
        else -> AppTheme.DEFAULT
    }
    fun parseStoredTheme(themeName: String): AppTheme {
        return when (themeName.uppercase()) {
            "LIGHT", "LIGHT_SOFT", "LIGHT_WARM", "LIGHT_SKY", "LIGHT_MINT" -> AppTheme.LIGHT
            "CYBERPUNK", "CYPERPUNK", "CYBERPUNK_NEON", "NEON_CYBERPUNK" -> AppTheme.CYBERPUNK
            else -> AppTheme.DEFAULT
        }
    }
    fun fallbackAccentForTheme(theme: AppTheme): Color {
        return ThemeEngine.getColors(theme).first
    }

    fun currentTemplateSettings(): TemplateSettings {
        return TemplateSettings(
            autoNewDay = autoNewDay,
            confirmComplete = confirmComplete,
            refreshIncompleteOnly = refreshIncompleteOnly,
            customMode = customMode,
            advancedOptions = advancedOptions,
            highContrastText = highContrastText,
            compactMode = compactMode,
            largerTouchTargets = largerTouchTargets,
            reduceAnimations = reduceAnimations,
            decorativeBorders = neonFlowEnabled,
            neonLightBoost = neonLightBoost,
            neonFlowEnabled = neonFlowEnabled,
            neonFlowSpeed = neonFlowSpeed.coerceIn(0, 2),
            neonGlowPalette = neonGlowPalette,
            alwaysShowRoutineProgress = alwaysShowRoutineProgress,
            hideCompletedRoutines = hideCompletedRoutines,
            confirmDestructiveActions = confirmDestructiveActions,
            dailyResetHour = dailyResetHour,
            dailyRemindersEnabled = dailyRemindersEnabled,
            hapticsEnabled = hapticsEnabled,
            soundEffectsEnabled = soundEffectsEnabled,
            fontStyle = fontStyle,
            fontScalePercent = fontScalePercent,
            backgroundImageUri = backgroundImageUri,
            backgroundVideoUri = backgroundVideoUri,
            backgroundType = backgroundType,
            backgroundImageTintEnabled = backgroundImageTintEnabled,
            backgroundImageTransparencyPercent = backgroundImageTransparencyPercent.coerceIn(0, 100),
            accentTransparencyPercent = accentTransparencyPercent.coerceIn(0, 100),
            textTransparencyPercent = textTransparencyPercent.coerceIn(0, 100),
            appBgTransparencyPercent = appBgTransparencyPercent.coerceIn(0, 100),
            chromeBgTransparencyPercent = chromeBgTransparencyPercent.coerceIn(0, 100),
            cardBgTransparencyPercent = cardBgTransparencyPercent.coerceIn(0, 100),
            journalPageTransparencyPercent = journalPageTransparencyPercent.coerceIn(0, 100),
            journalAccentTransparencyPercent = journalAccentTransparencyPercent.coerceIn(0, 100),
            buttonTransparencyPercent = buttonTransparencyPercent.coerceIn(0, 100),
            textColorArgb = textColorOverride?.toArgbCompat()?.toLong(),
            appBackgroundArgb = appBackgroundColorOverride?.toArgbCompat()?.toLong(),
            chromeBackgroundArgb = chromeBackgroundColorOverride?.toArgbCompat()?.toLong(),
            cardColorArgb = cardColorOverride?.toArgbCompat()?.toLong(),
            buttonColorArgb = buttonColorOverride?.toArgbCompat()?.toLong(),
            journalPageColorArgb = journalPageColorOverride?.toArgbCompat()?.toLong(),
            journalAccentColorArgb = journalAccentColorOverride?.toArgbCompat()?.toLong(),
            journalName = journalName
        )
    }
    fun applyTemplateDailyRoutineDefaults(packageId: String, clearExisting: Boolean = true) {
        if (!clearExisting) return
        if (packageId == defaultSystemPackageId) {
            dailyRoutineTarget = 5
        } else if (packageId == Routine_FEATURE_TEST_PACKAGE_ID) {
            dailyRoutineTarget = 5
        }
    }

    fun applyTemplateSettings(settings: TemplateSettings?) {
        if (settings == null) return
        autoNewDay = settings.autoNewDay
        confirmComplete = settings.confirmComplete
        refreshIncompleteOnly = settings.refreshIncompleteOnly
        customMode = settings.customMode
        advancedOptions = settings.advancedOptions
        highContrastText = settings.highContrastText
        compactMode = settings.compactMode
        largerTouchTargets = settings.largerTouchTargets
        reduceAnimations = settings.reduceAnimations
        val neonEnabledFromTemplate = settings.neonFlowEnabled || settings.decorativeBorders
        decorativeBorders = neonEnabledFromTemplate
        neonLightBoost = settings.neonLightBoost
        neonFlowEnabled = neonEnabledFromTemplate
        neonFlowSpeed = settings.neonFlowSpeed.coerceIn(0, 2)
        neonGlowPalette = runCatching { settings.neonGlowPalette }.getOrDefault("magenta").ifBlank { "magenta" }
        alwaysShowRoutineProgress = settings.alwaysShowRoutineProgress
        hideCompletedRoutines = settings.hideCompletedRoutines
        confirmDestructiveActions = true
        dailyResetHour = settings.dailyResetHour.coerceIn(0, 23)
        dailyRemindersEnabled = settings.dailyRemindersEnabled
        hapticsEnabled = settings.hapticsEnabled
        soundEffectsEnabled = settings.soundEffectsEnabled
        fontStyle = runCatching { settings.fontStyle }.getOrDefault(AppFontStyle.DEFAULT)
        fontScalePercent = settings.fontScalePercent.coerceIn(80, 125)
        backgroundImageUri = runCatching { settings.backgroundImageUri }.getOrNull()
        backgroundVideoUri = runCatching { settings.backgroundVideoUri }.getOrNull()
        backgroundType = runCatching { settings.backgroundType }.getOrDefault("color")
        backgroundVideoMuted = runCatching { settings.backgroundVideoMuted }.getOrDefault(true)
        backgroundImageTintEnabled = runCatching { settings.backgroundImageTintEnabled }.getOrDefault(true)
        backgroundImageTransparencyPercent = (settings.backgroundImageTransparencyPercent ?: backgroundImageTransparencyPercent).coerceIn(0, 100)
        accentTransparencyPercent = (settings.accentTransparencyPercent ?: accentTransparencyPercent).coerceIn(0, 100)
        textTransparencyPercent = (settings.textTransparencyPercent ?: textTransparencyPercent).coerceIn(0, 100)
        appBgTransparencyPercent = (settings.appBgTransparencyPercent ?: appBgTransparencyPercent).coerceIn(0, 100)
        chromeBgTransparencyPercent = (settings.chromeBgTransparencyPercent ?: chromeBgTransparencyPercent).coerceIn(0, 100)
        cardBgTransparencyPercent = (settings.cardBgTransparencyPercent ?: cardBgTransparencyPercent).coerceIn(0, 100)
        journalPageTransparencyPercent = (settings.journalPageTransparencyPercent ?: journalPageTransparencyPercent).coerceIn(0, 100)
        journalAccentTransparencyPercent = (settings.journalAccentTransparencyPercent ?: journalAccentTransparencyPercent).coerceIn(0, 100)
        buttonTransparencyPercent = (settings.buttonTransparencyPercent ?: buttonTransparencyPercent).coerceIn(0, 100)
        textColorOverride = settings.textColorArgb?.let { Color(it.toInt()) }
        appBackgroundColorOverride = settings.appBackgroundArgb?.let { Color(it.toInt()) }
        chromeBackgroundColorOverride = settings.chromeBackgroundArgb?.let { Color(it.toInt()) }
        cardColorOverride = settings.cardColorArgb?.let { Color(it.toInt()) }
        buttonColorOverride = settings.buttonColorArgb?.let { Color(it.toInt()) }
        journalPageColorOverride = settings.journalPageColorArgb?.let { Color(it.toInt()) }
        journalAccentColorOverride = settings.journalAccentColorArgb?.let { Color(it.toInt()) }
        journalName = runCatching { settings.journalName }.getOrDefault("Journal").ifBlank { "Journal" }
    }

    fun customTemplateIdentityKey(template: CustomTemplate): String {
        return buildString {
            append(template.packageId.trim())
            append("|")
            append(template.category.name)
            append("|")
            append(template.title.trim().lowercase(Locale.ROOT))
            append("|")
            append(template.icon.trim())
            append("|")
            append(template.objectiveType.name)
            append("|")
            append(template.target)
            append("|")
            append(template.targetSeconds ?: 0)
            append("|")
            append(template.healthMetric.orEmpty().trim().lowercase(Locale.ROOT))
            append("|")
            append(template.healthAggregation.orEmpty().trim().lowercase(Locale.ROOT))
            append("|")
            append(template.imageUri.orEmpty().trim())
        }
    }

    fun mergeCustomTemplatesDistinct(
        existing: List<CustomTemplate>,
        incoming: List<CustomTemplate>
    ): List<CustomTemplate> {
        return (existing + incoming).distinctBy(::customTemplateIdentityKey)
    }

    fun remapMilestonesForPackage(
        incoming: List<Milestone>,
        packageId: String,
        existing: List<Milestone>
    ): List<Milestone> {
        if (incoming.isEmpty()) return emptyList()
        val existingIds = existing.map { it.id }.toMutableSet()
        val idMap = linkedMapOf<String, String>()
        incoming.forEachIndexed { idx, milestone ->
            val rawId = milestone.id.trim().ifBlank { "milestone_${idx + 1}" }
            val prefixedBase = if (rawId.startsWith("${packageId}_")) rawId else "${packageId}_$rawId"
            val normalizedBase = prefixedBase
                .replace(Regex("[^A-Za-z0-9_\\-]"), "_")
                .take(64)
                .ifBlank { "${packageId}_milestone_${idx + 1}" }
            var candidate = normalizedBase
            var suffix = 1
            while (existingIds.contains(candidate) || idMap.values.contains(candidate)) {
                candidate = "${normalizedBase.take(58)}_${suffix++}"
            }
            idMap[milestone.id] = candidate
        }
        return incoming.mapIndexed { idx, milestone ->
            val fallbackId = "${packageId}_milestone_${idx + 1}"
            val mappedId = idMap[milestone.id] ?: fallbackId
            val mappedPrereq = milestone.prerequisiteId?.let { idMap[it] }
            milestone.copy(
                id = mappedId,
                prerequisiteId = mappedPrereq,
                packageId = packageId
            )
        }
    }

    fun persistInventory(items: List<InventoryItem>) {
        inventory = items
        scope.launch { appContext.dataStore.edit { p -> p[Keys.INVENTORY] = serializeInventory(items) } }
    }

    fun persistCatalogItems(items: List<CatalogItem>) {
        catalogItems = items
        scope.launch { appContext.dataStore.edit { p -> p[Keys.CATALOG_ITEMS] = serializeCatalogItems(items) } }
    }

    fun persistCalendarPlans(plans: Map<Long, List<String>>) {
        calendarPlans = plans
        scope.launch { appContext.dataStore.edit { p -> p[Keys.CALENDAR_PLANS] = serializeCalendarPlans(plans) } }
    }
    fun pushPlanStateToSupabase(plans: Map<Long, List<String>>) {
        if (!SupabaseApi.isConfigured || authUserId.isBlank()) return
        scope.launch {
            SupabaseApi.upsertPlanState(
                userId = authUserId,
                userName = authUserEmail.substringBefore("@").ifBlank { "Player" },
                plans = plans
            )
        }
    }
    fun pushHistoryStateToSupabase(day: Long, done: Int, total: Int, allDone: Boolean) {
        if (!SupabaseApi.isConfigured || authUserId.isBlank()) return
        scope.launch {
            SupabaseApi.upsertHistoryState(
                userId = authUserId,
                userName = authUserEmail.substringBefore("@").ifBlank { "Player" },
                day = day,
                done = done,
                total = total,
                allDone = allDone
            )
        }
    }

    fun persistCharacter(data: CharacterData) {
        characterData = data
        scope.launch {
            appContext.dataStore.edit { p ->
                p[Keys.CHAR_HEAD] = data.headColor
                p[Keys.CHAR_BODY] = data.bodyColor
                p[Keys.CHAR_LEGS] = data.legsColor
                p[Keys.CHAR_SHOES] = data.shoesColor
            }
        }
    }

    fun persistMilestones(list: List<Milestone>) {
        milestones = list
        scope.launch { appContext.dataStore.edit { p -> p[Keys.MILESTONES] = serializeMilestones(list) } }
    }

    fun persistSavedTemplates(list: List<GameTemplate>) {
        savedTemplates = list
        scope.launch { appContext.dataStore.edit { p -> p[Keys.SAVED_TEMPLATES] = serializeSavedTemplates(list) } }
    }

    fun sanitizeHealthSnapshot(snapshot: HealthDailySnapshot): HealthDailySnapshot {
        val safeDistance = if (snapshot.distanceMeters.isFinite()) snapshot.distanceMeters else 0f
        val safeCalories = if (snapshot.caloriesKcal.isFinite()) snapshot.caloriesKcal else 0f
        return snapshot.copy(
            steps = snapshot.steps.coerceAtLeast(0),
            avgHeartRate = snapshot.avgHeartRate?.coerceIn(0, 260),
            distanceMeters = safeDistance.coerceIn(0f, 1_000_000f),
            caloriesKcal = safeCalories.coerceIn(0f, 100_000f)
        )
    }

    fun persistHealthDailySnapshot(snapshot: HealthDailySnapshot?) {
        val safeSnapshot = snapshot?.let(::sanitizeHealthSnapshot)
        healthDailySnapshot = safeSnapshot
        scope.launch {
            runCatching {
                appContext.dataStore.edit { p ->
                    if (safeSnapshot == null) {
                        p.remove(Keys.HEALTH_DAILY_SNAPSHOT)
                    } else {
                        p[Keys.HEALTH_DAILY_SNAPSHOT] = Gson().toJson(safeSnapshot)
                    }
                }
            }.onFailure { err ->
                AppLog.w("persist_health_snapshot_failed", err)
            }
        }
    }

    fun metricValueForRoutine(snapshot: HealthDailySnapshot, metric: String?): Int {
        return when (metric?.trim()?.lowercase(Locale.getDefault())) {
            "steps" -> snapshot.steps
            "heart_rate" -> snapshot.avgHeartRate ?: 0
            "distance_m" -> snapshot.distanceMeters.toInt()
            "calories_kcal" -> snapshot.caloriesKcal.toInt()
            else -> 0
        }
    }

    fun syncHealthObjectiveRoutineProgress(
        snapshot: HealthDailySnapshot?,
        startedRoutineId: Int? = null
    ) {
        if (snapshot == null) return
        val updated = routines.map { q ->
            if (q.completed || q.objectiveType != RoutineObjectiveType.HEALTH) return@map q
            val metric = q.healthMetric?.trim()?.lowercase(Locale.getDefault())
            val value = metricValueForRoutine(snapshot, q.healthMetric).coerceAtLeast(0)
            val target = q.target.coerceAtLeast(1)
            val mapped = when (metric) {
                // Heart-rate goals are threshold goals: lower/equal target is success.
                "heart_rate" -> if (value > 0 && value <= target) target + 1 else 0
                else -> if (value >= target) target + 1 else value
            }
            val effective = when {
                mapped > 0 -> mapped
                q.id == startedRoutineId -> 1
                q.currentProgress > 0 -> 1
                else -> 0
            }
            if (effective == q.currentProgress) q else q.copy(currentProgress = effective)
        }
        if (updated != routines) {
            routines = updated
            val completedIds = updated.filter { it.completed }.map { it.id }.toSet()
            val base = updated.map { it.copy(completed = false) }
            scope.launch {
                runCatching {
                    appContext.dataStore.edit { p ->
                        p[Keys.LAST_DAY] = lastDayEpoch
                        p[Keys.ROUTINES] = serializeRoutines(base)
                        p[Keys.COMPLETED] = completedIds.joinToString(",")
                        p[Keys.EARNED] = earnedIds.joinToString(",")
                        p[Keys.REFRESH_COUNT] = refreshCount
                    }
                }.onFailure { err ->
                    AppLog.w("persist_health_progress_failed", err)
                }
            }
        }
    }

    fun clampHealthTarget(metric: String?, value: Int): Int {
        return when (metric?.trim()?.lowercase(Locale.getDefault())) {
            "steps" -> value.coerceIn(100, 50000)
            "heart_rate" -> value.coerceIn(40, 220)
            "distance_m" -> value.coerceIn(100, 50000)
            "calories_kcal" -> value.coerceIn(50, 5000)
            else -> value.coerceAtLeast(1)
        }
    }

    fun sanitizeHealthTemplateOrNull(template: CustomTemplate): CustomTemplate? {
        if (template.objectiveType != RoutineObjectiveType.HEALTH) return template
        val metric = template.healthMetric?.trim()?.lowercase(Locale.getDefault())
        if (metric !in setOf("steps", "heart_rate", "distance_m", "calories_kcal")) {
            AppLog.w("Dropped invalid HEALTH template id=${template.id} title=${template.title} metric=${template.healthMetric}")
            return null
        }
        val safeTarget = clampHealthTarget(metric, template.target)
        return template.copy(
            target = safeTarget,
            healthMetric = metric,
            healthAggregation = template.healthAggregation ?: if (metric == "heart_rate") "daily_avg" else "daily_total"
        )
    }

    fun sanitizeHealthRoutineOrNull(routine: Routine): Routine? {
        if (routine.objectiveType != RoutineObjectiveType.HEALTH) return routine
        val metric = routine.healthMetric?.trim()?.lowercase(Locale.getDefault())
        if (metric !in setOf("steps", "heart_rate", "distance_m", "calories_kcal")) {
            AppLog.w("Dropped invalid HEALTH Routine id=${routine.id} title=${routine.title} metric=${routine.healthMetric}")
            return null
        }
        val safeTarget = clampHealthTarget(metric, routine.target)
        return routine.copy(
            target = safeTarget,
            currentProgress = routine.currentProgress.coerceIn(0, safeTarget + 1),
            healthMetric = metric,
            healthAggregation = routine.healthAggregation ?: if (metric == "heart_rate") "daily_avg" else "daily_total"
        )
    }

    fun exportBackupPayload(): String {
        val dump = mutableMapOf<String, String>()
        dump["balance"] = balance.toString()
        dump["routines"] = serializeRoutines(routines)
        dump["completed"] = routines.filter { it.completed }.joinToString(",") { it.id.toString() }
        dump["earned"] = earnedIds.joinToString(",")
        dump["lastDay"] = lastDayEpoch.toString()
        dump["refreshCount"] = refreshCount.toString()
        dump["milestones"] = serializeMilestones(milestones)
        dump["customTemplates"] = serializeCustomTemplates(customTemplates)
        dump["inventory"] = serializeInventory(inventory)
        dump["catalogItems"] = serializeCatalogItems(catalogItems)
        dump["calendarPlans"] = serializeCalendarPlans(calendarPlans)
        dump["history"] = serializeHistory(historyMap)
        dump["settings_theme"] = appTheme.name
        dump["settings_accent"] = accent.toArgbCompat().toString()
        dump["settings_bg"] = backgroundImageUri.orEmpty()
        dump["settings_bgVideo"] = backgroundVideoUri.orEmpty()
        dump["settings_bgType"] = backgroundType
        dump["settings_bgVideoMuted"] = backgroundVideoMuted.toString()
        dump["settings_bgTransparency"] = backgroundImageTransparencyPercent.toString()
        dump["settings_bgColor"] = appBackgroundColorOverride?.toArgbCompat()?.toString().orEmpty()
        dump["settings_chromeColor"] = chromeBackgroundColorOverride?.toArgbCompat()?.toString().orEmpty()
        dump["settings_cardColor"] = cardColorOverride?.toArgbCompat()?.toString().orEmpty()
        dump["settings_buttonColor"] = buttonColorOverride?.toArgbCompat()?.toString().orEmpty()
        dump["settings_journalPageColor"] = journalPageColorOverride?.toArgbCompat()?.toString().orEmpty()
        dump["settings_journalAccentColor"] = journalAccentColorOverride?.toArgbCompat()?.toString().orEmpty()
        dump["settings_journalName"] = journalName
        dump["settings_fontStyle"] = fontStyle.name
        dump["settings_fontScale"] = fontScalePercent.toString()
        dump["settings_decorativeBorders"] = neonFlowEnabled.toString()
        dump["settings_neonLightBoost"] = neonLightBoost.toString()
        dump["settings_neonFlowEnabled"] = neonFlowEnabled.toString()
        dump["settings_neonFlowSpeed"] = neonFlowSpeed.toString()
        dump["settings_neonGlowPalette"] = neonGlowPalette
        dump["settings_goal"] = onboardingGoal.name
        dump["settings_premium"] = premiumUnlocked.toString()
        dump["settings_dailyRoutineTarget"] = dailyRoutineTarget.toString()
        dump["settings_cloudEnabled"] = cloudSyncEnabled.toString()
        dump["settings_cloudEmail"] = cloudAccountEmail
        dump["settings_cloudLastSync"] = cloudLastSyncAt.toString()
        dump["dailyResetHour"] = dailyResetHour.toString()
        dump["dailyRemindersEnabled"] = dailyRemindersEnabled.toString()
        val payload = FullBackupPayload(dataStoreDump = dump)
        return exportFullBackupEncrypted(payload, appContext.packageName)
    }

    fun importBackupPayload(payload: String): Boolean {
        val parsed = importFullBackupEncrypted(payload.trim(), appContext.packageName) ?: return false
        val dump = parsed.dataStoreDump
        fun getInt(key: String, default: Int) = dump[key]?.toIntOrNull() ?: default
        fun getBool(key: String, default: Boolean) = dump[key]?.toBooleanStrictOrNull() ?: default
        balance = getInt("balance", balance)
        val importedCompleted = parseIds(dump["completed"])
        val importedDecoded = deserializeRoutines(dump["routines"].orEmpty())
            .mapNotNull(::sanitizeHealthRoutineOrNull)
        routines = ensureUniqueRoutineIds(importedDecoded).map { q ->
            if (importedCompleted.contains(q.id)) q.copy(completed = true) else q
        }
        val importedEarned = parseIds(dump["earned"])
        earnedIds = if (importedEarned.isNotEmpty()) importedEarned else importedCompleted
        lastDayEpoch = dump["lastDay"]?.toLongOrNull() ?: currentEpochDay()
        refreshCount = getInt("refreshCount", refreshCount).coerceIn(0, 3)
        milestones = deserializeMilestones(dump["milestones"].orEmpty())
        customTemplates = deserializeCustomTemplates(dump["customTemplates"].orEmpty())
        inventory = deserializeInventory(dump["inventory"].orEmpty())
        catalogItems = deserializeCatalogItems(dump["catalogItems"])
        calendarPlans = deserializeCalendarPlans(dump["calendarPlans"])
        appTheme = runCatching { AppTheme.valueOf(dump["settings_theme"].orEmpty()) }.getOrDefault(appTheme)
        dump["settings_accent"]?.toIntOrNull()?.let { accent = Color(it) }
        backgroundImageUri = dump["settings_bg"].orEmpty().ifBlank { null }
        backgroundVideoUri = dump["settings_bgVideo"].orEmpty().ifBlank { null }
        backgroundType = dump["settings_bgType"].orEmpty().ifBlank { backgroundType }
        backgroundVideoMuted = getBool("settings_bgVideoMuted", backgroundVideoMuted)
        backgroundImageTransparencyPercent = getInt("settings_bgTransparency", backgroundImageTransparencyPercent).coerceIn(0, 100)
        appBackgroundColorOverride = dump["settings_bgColor"]?.toIntOrNull()?.let { Color(it) }
        chromeBackgroundColorOverride = dump["settings_chromeColor"]?.toIntOrNull()?.let { Color(it) }
        cardColorOverride = dump["settings_cardColor"]?.toIntOrNull()?.let { Color(it) }
        buttonColorOverride = dump["settings_buttonColor"]?.toIntOrNull()?.let { Color(it) }
        journalPageColorOverride = dump["settings_journalPageColor"]?.toIntOrNull()?.let { Color(it) }
        journalAccentColorOverride = dump["settings_journalAccentColor"]?.toIntOrNull()?.let { Color(it) }
        journalName = dump["settings_journalName"].orEmpty().ifBlank { journalName }
        fontStyle = runCatching { AppFontStyle.valueOf(dump["settings_fontStyle"].orEmpty()) }.getOrDefault(fontStyle)
        fontScalePercent = getInt("settings_fontScale", fontScalePercent).coerceIn(80, 125)
        val importedDecorative = getBool("settings_decorativeBorders", decorativeBorders)
        neonLightBoost = getBool("settings_neonLightBoost", neonLightBoost)
        neonFlowEnabled = getBool("settings_neonFlowEnabled", importedDecorative || neonFlowEnabled) || importedDecorative
        decorativeBorders = neonFlowEnabled
        neonFlowSpeed = getInt("settings_neonFlowSpeed", neonFlowSpeed).coerceIn(0, 2)
        neonGlowPalette = dump["settings_neonGlowPalette"].orEmpty().ifBlank { neonGlowPalette }
        onboardingGoal = runCatching { OnboardingGoal.valueOf(dump["settings_goal"].orEmpty()) }.getOrDefault(onboardingGoal)
        premiumUnlocked = getBool("settings_premium", premiumUnlocked)
        dailyRoutineTarget = getInt("settings_dailyRoutineTarget", dailyRoutineTarget).coerceIn(3, 10)
        cloudSyncEnabled = getBool("settings_cloudEnabled", cloudSyncEnabled)
        cloudAccountEmail = dump["settings_cloudEmail"].orEmpty()
        cloudLastSyncAt = dump["settings_cloudLastSync"]?.toLongOrNull() ?: cloudLastSyncAt
        dailyResetHour = getInt("dailyResetHour", dailyResetHour).coerceIn(0, 23)
        dailyRemindersEnabled = getBool("dailyRemindersEnabled", dailyRemindersEnabled)
        val importedHistory = dump["history"]?.let { serializeHistory(parseHistory(it)) }
        scope.launch {
            appContext.dataStore.edit { p ->
                p[Keys.BALANCE] = balance
                p[Keys.LAST_DAY] = lastDayEpoch
                p[Keys.REFRESH_COUNT] = refreshCount
                p[Keys.ROUTINES] = serializeRoutines(routines.map { it.copy(completed = false) })
                p[Keys.COMPLETED] = routines.filter { it.completed }.joinToString(",") { it.id.toString() }
                p[Keys.EARNED] = earnedIds.joinToString(",")
                p[Keys.MILESTONES] = serializeMilestones(milestones)
                p[Keys.CUSTOM_TEMPLATES] = serializeCustomTemplates(customTemplates)
                p[Keys.INVENTORY] = serializeInventory(inventory)
                p[Keys.CATALOG_ITEMS] = serializeCatalogItems(catalogItems)
                p[Keys.CALENDAR_PLANS] = serializeCalendarPlans(calendarPlans)
                p[Keys.APP_THEME] = appTheme.name
                p[Keys.ACCENT_ARGB] = accent.toArgbCompat()
                p[Keys.FONT_STYLE] = fontStyle.name
                p[Keys.FONT_SCALE_PERCENT] = fontScalePercent
                p[Keys.DECORATIVE_BORDERS] = neonFlowEnabled
                p[Keys.NEON_LIGHT_BOOST] = neonLightBoost
                p[Keys.NEON_FLOW_ENABLED] = neonFlowEnabled
                p[Keys.NEON_FLOW_SPEED] = neonFlowSpeed.coerceIn(0, 2)
                p[Keys.NEON_GLOW_PALETTE] = neonGlowPalette
                p[Keys.BACKGROUND_IMAGE_URI] = backgroundImageUri.orEmpty()
                p[Keys.BACKGROUND_VIDEO_URI] = backgroundVideoUri.orEmpty()
                p[Keys.BACKGROUND_TYPE] = backgroundType
                p[Keys.BACKGROUND_VIDEO_MUTED] = backgroundVideoMuted
                p[Keys.BACKGROUND_IMAGE_TINT_ENABLED] = backgroundImageTintEnabled
                p[Keys.BACKGROUND_IMAGE_TRANSPARENCY_PERCENT] = backgroundImageTransparencyPercent.coerceIn(0, 100)
                p[Keys.TRANSPARENCY_ACCENT] = accentTransparencyPercent.coerceIn(0, 100)
                p[Keys.TRANSPARENCY_TEXT] = textTransparencyPercent.coerceIn(0, 100)
                p[Keys.TRANSPARENCY_APP_BG] = appBgTransparencyPercent.coerceIn(0, 100)
                p[Keys.TRANSPARENCY_CHROME_BG] = chromeBgTransparencyPercent.coerceIn(0, 100)
                p[Keys.TRANSPARENCY_CARD_BG] = cardBgTransparencyPercent.coerceIn(0, 100)
                p[Keys.TRANSPARENCY_JOURNAL_PAGE] = journalPageTransparencyPercent.coerceIn(0, 100)
                p[Keys.TRANSPARENCY_JOURNAL_ACCENT] = journalAccentTransparencyPercent.coerceIn(0, 100)
                p[Keys.TRANSPARENCY_BUTTON] = buttonTransparencyPercent.coerceIn(0, 100)
                appBackgroundColorOverride?.let { p[Keys.APP_BACKGROUND_ARGB] = it.toArgbCompat() } ?: p.remove(Keys.APP_BACKGROUND_ARGB)
                chromeBackgroundColorOverride?.let { p[Keys.CHROME_BACKGROUND_ARGB] = it.toArgbCompat() } ?: p.remove(Keys.CHROME_BACKGROUND_ARGB)
                cardColorOverride?.let { p[Keys.CARD_COLOR_ARGB] = it.toArgbCompat() } ?: p.remove(Keys.CARD_COLOR_ARGB)
                buttonColorOverride?.let { p[Keys.BUTTON_COLOR_ARGB] = it.toArgbCompat() } ?: p.remove(Keys.BUTTON_COLOR_ARGB)
                journalPageColorOverride?.let { p[Keys.JOURNAL_PAGE_COLOR_ARGB] = it.toArgbCompat() } ?: p.remove(Keys.JOURNAL_PAGE_COLOR_ARGB)
                journalAccentColorOverride?.let { p[Keys.JOURNAL_ACCENT_COLOR_ARGB] = it.toArgbCompat() } ?: p.remove(Keys.JOURNAL_ACCENT_COLOR_ARGB)
                p[Keys.JOURNAL_NAME] = journalName
                p[Keys.AUTH_ACCESS_TOKEN] = encryptAuthState(authAccessToken)
                p[Keys.AUTH_REFRESH_TOKEN] = encryptAuthState(authRefreshToken)
                p[Keys.AUTH_USER_EMAIL] = authUserEmail
                p[Keys.AUTH_USER_ID] = authUserId
                p[Keys.AUTH_PROVIDER] = if (isLoggedIn) "google" else ""
                p[Keys.ONBOARDING_GOAL] = onboardingGoal.name
                p[Keys.PREMIUM_UNLOCKED] = premiumUnlocked
                p[Keys.DAILY_ROUTINE_TARGET] = dailyRoutineTarget
                p[Keys.CLOUD_SYNC_ENABLED] = cloudSyncEnabled
                p[Keys.CLOUD_ACCOUNT_EMAIL] = cloudAccountEmail
                p[Keys.CLOUD_LAST_SYNC_AT] = cloudLastSyncAt
                p[Keys.DAILY_RESET_HOUR] = dailyResetHour
                p[Keys.DAILY_REMINDERS_ENABLED] = dailyRemindersEnabled
                importedHistory?.let { p[Keys.HISTORY] = it }
            }
        }
        return true
    }

    fun triggerCloudSnapshotSync(force: Boolean = false) {
        if (!cloudSyncEnabled) return
        if (cloudConnectedAccount == null) {
            if (force) {
                scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_google_drive_first)) }
            }
            return
        }
        val now = System.currentTimeMillis()
        if (!force && now - cloudLastAutoAttemptAt < 120_000L) return
        cloudLastAutoAttemptAt = now
        val snapshot = exportBackupPayload()
        if (snapshot.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_cloud_sync_empty)) }
            return
        }
        scope.launch {
            val ok = GoogleDriveSync.uploadBackup(appContext, snapshot)
            if (ok) {
                cloudLastSyncAt = System.currentTimeMillis()
                cloudAccountEmail = cloudConnectedAccount?.email.orEmpty()
                appContext.dataStore.edit { p ->
                    p[Keys.CLOUD_LAST_SNAPSHOT] = snapshot
                    p[Keys.CLOUD_ACCOUNT_EMAIL] = cloudAccountEmail
                    p[Keys.CLOUD_LAST_SYNC_AT] = cloudLastSyncAt
                }
                snackbarHostState.showSnackbar(getString(R.string.snackbar_cloud_backup_synced))
            } else {
                snackbarHostState.showSnackbar(getString(R.string.snackbar_cloud_sync_failed))
            }
        }
    }

    fun restoreFromCloud() {
        if (!cloudSyncEnabled || cloudConnectedAccount == null) {
            scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_google_drive_first)) }
            return
        }
        scope.launch {
            val payload = GoogleDriveSync.downloadBackup(appContext)
            if (payload.isNullOrBlank()) {
                snackbarHostState.showSnackbar(getString(R.string.snackbar_no_cloud_backup))
                return@launch
            }
            val ok = importBackupPayload(payload)
            if (ok) {
                cloudLastSyncAt = System.currentTimeMillis()
                appContext.dataStore.edit { p -> p[Keys.CLOUD_LAST_SYNC_AT] = cloudLastSyncAt }
                snackbarHostState.showSnackbar(getString(R.string.snackbar_cloud_restored))
            } else {
                snackbarHostState.showSnackbar(getString(R.string.snackbar_cloud_restore_failed))
            }
        }
    }

    fun disconnectCloudAccount() {
        cloudSyncEnabled = false
        cloudConnectedAccount = null
        cloudAccountEmail = ""
        GoogleDriveSync.signOut(appContext)
        scope.launch {
            appContext.dataStore.edit { p ->
                p[Keys.CLOUD_SYNC_ENABLED] = false
                p[Keys.CLOUD_ACCOUNT_EMAIL] = ""
            }
        }
    }

    fun onGoogleAccountConnected(account: GoogleSignInAccount) {
        cloudConnectedAccount = account
        cloudSyncEnabled = true
        cloudAccountEmail = account.email.orEmpty()
        scope.launch {
            appContext.dataStore.edit { p ->
                p[Keys.CLOUD_SYNC_ENABLED] = true
                p[Keys.CLOUD_ACCOUNT_EMAIL] = cloudAccountEmail
            }
            snackbarHostState.showSnackbar(getString(R.string.snackbar_google_cloud_connected))
        }
    }

    fun shareFeedbackReport(category: String = "", message: String = "") {
        val resolvedCategory = category.ifBlank { getString(R.string.feedback_general) }
        val premiumLabel = if (premiumUnlocked) getString(R.string.on_label) else getString(R.string.off_label)
        val cloudSyncLabel = if (cloudSyncEnabled) getString(R.string.on_label) else getString(R.string.off_label)
        val report = buildString {
            appendLine(getString(R.string.feedback_report_title))
            appendLine(getString(R.string.feedback_report_user, authUserEmail.ifBlank { "anonymous" }, authUserId.ifBlank { "n/a" }))
            appendLine(getString(R.string.feedback_report_category, resolvedCategory))
            appendLine(getString(R.string.feedback_report_theme, appTheme.name))
            appendLine(getString(R.string.feedback_report_premium, premiumLabel))
            appendLine(getString(R.string.feedback_report_cloud_sync, cloudSyncLabel))
            if (message.isNotBlank()) {
                appendLine()
                appendLine(getString(R.string.feedback_report_message_label))
                appendLine(message)
            }
            appendLine()
            appendLine(getString(R.string.feedback_report_logs_label))
            appendLine(AppLog.exportRecentLogs().ifBlank { getString(R.string.feedback_report_no_logs) })
        }
        scope.launch {
            val pushed = SupabaseApi.submitFeedbackInbox(
                userId = authUserId.ifBlank { UUID.randomUUID().toString() },
                userName = authUserEmail.substringBefore("@").ifBlank { "Player" },
                category = resolvedCategory,
                message = message.ifBlank { report },
                appTheme = appTheme.name
            )
            if (pushed) {
                snackbarHostState.showSnackbar(getString(R.string.snackbar_feedback_sent))
            } else {
                val intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, report)
                    type = "text/plain"
                }
                appContext.startActivity(Intent.createChooser(intent, getString(R.string.settings_send_feedback)))
            }
        }
    }


    fun persistCore() {
        scope.launch {
            appContext.dataStore.edit { p ->
                p[Keys.BALANCE] = balance
            }
        }
        triggerCloudSnapshotSync(force = false)
    }

    fun persistSettings() {
        scope.launch {
            appContext.dataStore.edit { p ->
                p[Keys.AUTO_NEW_DAY] = autoNewDay
                p[Keys.CONFIRM_COMPLETE] = confirmComplete
                p[Keys.REFRESH_INCOMPLETE_ONLY] = refreshIncompleteOnly
                p[Keys.ADMIN_MODE] = customMode
                p[Keys.APP_THEME] = appTheme.name
                p[Keys.ACCENT_ARGB] = accent.toArgbCompat()
                p[Keys.ADVANCED_OPTIONS] = advancedOptions
                p[Keys.HIGH_CONTRAST_TEXT] = highContrastText
                p[Keys.COMPACT_MODE] = compactMode
                p[Keys.LARGE_TOUCH_TARGETS] = largerTouchTargets
                p[Keys.REDUCE_ANIMATIONS] = reduceAnimations
                p[Keys.DECORATIVE_BORDERS] = neonFlowEnabled
                p[Keys.NEON_LIGHT_BOOST] = neonLightBoost
                p[Keys.NEON_FLOW_ENABLED] = neonFlowEnabled
                p[Keys.NEON_FLOW_SPEED] = neonFlowSpeed.coerceIn(0, 2)
                p[Keys.NEON_GLOW_PALETTE] = neonGlowPalette
                p[Keys.ALWAYS_SHOW_ROUTINE_PROGRESS] = alwaysShowRoutineProgress
                p[Keys.HIDE_COMPLETED_routines] = hideCompletedRoutines
                p[Keys.CONFIRM_DESTRUCTIVE] = confirmDestructiveActions
                p[Keys.DAILY_RESET_HOUR] = dailyResetHour.coerceIn(0, 23)
                p[Keys.DAILY_REMINDERS_ENABLED] = dailyRemindersEnabled
                p[Keys.HAPTICS] = hapticsEnabled
                p[Keys.SOUND_EFFECTS] = soundEffectsEnabled
                p[Keys.ONBOARDING_GOAL] = onboardingGoal.name
                p[Keys.PREMIUM_UNLOCKED] = premiumUnlocked
                p[Keys.DAILY_ROUTINE_TARGET] = dailyRoutineTarget.coerceIn(3, 10)
                p[Keys.CLOUD_SYNC_ENABLED] = cloudSyncEnabled
                p[Keys.CLOUD_ACCOUNT_EMAIL] = cloudAccountEmail
                p[Keys.CLOUD_LAST_SYNC_AT] = cloudLastSyncAt
                p[Keys.FONT_STYLE] = fontStyle.name
                p[Keys.FONT_SCALE_PERCENT] = fontScalePercent.coerceIn(80, 125)
                p[Keys.BACKGROUND_IMAGE_URI] = backgroundImageUri.orEmpty()
                p[Keys.BACKGROUND_VIDEO_URI] = backgroundVideoUri.orEmpty()
                p[Keys.BACKGROUND_TYPE] = backgroundType
                p[Keys.BACKGROUND_VIDEO_MUTED] = backgroundVideoMuted
                p[Keys.BACKGROUND_IMAGE_TINT_ENABLED] = backgroundImageTintEnabled
                p[Keys.BACKGROUND_IMAGE_TRANSPARENCY_PERCENT] = backgroundImageTransparencyPercent.coerceIn(0, 100)
                p[Keys.TRANSPARENCY_ACCENT] = accentTransparencyPercent.coerceIn(0, 100)
                p[Keys.TRANSPARENCY_TEXT] = textTransparencyPercent.coerceIn(0, 100)
                p[Keys.TRANSPARENCY_APP_BG] = appBgTransparencyPercent.coerceIn(0, 100)
                p[Keys.TRANSPARENCY_CHROME_BG] = chromeBgTransparencyPercent.coerceIn(0, 100)
                p[Keys.TRANSPARENCY_CARD_BG] = cardBgTransparencyPercent.coerceIn(0, 100)
                p[Keys.TRANSPARENCY_JOURNAL_PAGE] = journalPageTransparencyPercent.coerceIn(0, 100)
                p[Keys.TRANSPARENCY_JOURNAL_ACCENT] = journalAccentTransparencyPercent.coerceIn(0, 100)
                p[Keys.TRANSPARENCY_BUTTON] = buttonTransparencyPercent.coerceIn(0, 100)
                appBackgroundColorOverride?.let { p[Keys.APP_BACKGROUND_ARGB] = it.toArgbCompat() } ?: p.remove(Keys.APP_BACKGROUND_ARGB)
                chromeBackgroundColorOverride?.let { p[Keys.CHROME_BACKGROUND_ARGB] = it.toArgbCompat() } ?: p.remove(Keys.CHROME_BACKGROUND_ARGB)
                cardColorOverride?.let { p[Keys.CARD_COLOR_ARGB] = it.toArgbCompat() } ?: p.remove(Keys.CARD_COLOR_ARGB)
                buttonColorOverride?.let { p[Keys.BUTTON_COLOR_ARGB] = it.toArgbCompat() } ?: p.remove(Keys.BUTTON_COLOR_ARGB)
                journalPageColorOverride?.let { p[Keys.JOURNAL_PAGE_COLOR_ARGB] = it.toArgbCompat() } ?: p.remove(Keys.JOURNAL_PAGE_COLOR_ARGB)
                journalAccentColorOverride?.let { p[Keys.JOURNAL_ACCENT_COLOR_ARGB] = it.toArgbCompat() } ?: p.remove(Keys.JOURNAL_ACCENT_COLOR_ARGB)
                p[Keys.JOURNAL_NAME] = journalName
                p[Keys.APP_LANGUAGE] = appLanguage
                if (textColorOverride == null) {
                    p.remove(Keys.TEXT_COLOR_ARGB)
                } else {
                    p[Keys.TEXT_COLOR_ARGB] = textColorOverride!!.toArgbCompat()
                }
            }
        }
    }

    fun persistAvatar(newAvatar: Avatar) {
        avatar = newAvatar
        scope.launch {
            appContext.dataStore.edit { p ->
                when (newAvatar) {
                    is Avatar.Preset -> { p[Keys.AVATAR_PRESET] = newAvatar.emoji; p[Keys.AVATAR_URI] = "" }; is Avatar.Custom -> { p[Keys.AVATAR_PRESET] = ""; p[Keys.AVATAR_URI] = newAvatar.uri.toString() }
                }
            }
        }
    }

    fun persistCustomTemplates(list: List<CustomTemplate>) {
        customTemplates = list
        scope.launch { appContext.dataStore.edit { p -> p[Keys.CUSTOM_TEMPLATES] = serializeCustomTemplates(list) } }
    }
    fun markShopTutorialSeen() {
        if (shopTutorialSeen) return
        shopTutorialSeen = true
        scope.launch { appContext.dataStore.edit { p -> p[Keys.TUTORIAL_CATALOG] = true } }
    }
    fun markCalendarTutorialSeen() {
        if (calendarTutorialSeen) return
        calendarTutorialSeen = true
        scope.launch { appContext.dataStore.edit { p -> p[Keys.TUTORIAL_CALENDAR] = true } }
    }
    fun markRoutinesTutorialSeen() {
        if (RoutinesTutorialSeen) return
        RoutinesTutorialSeen = true
        scope.launch { appContext.dataStore.edit { p -> p[Keys.TUTORIAL_ROUTINES] = true } }
    }
    fun markShopHoldHintSeen() {
        if (shopHoldHintSeen) return
        shopHoldHintSeen = true
        scope.launch { appContext.dataStore.edit { p -> p[Keys.CATALOG_HOLD_HINT_SEEN] = true } }
    }

    fun persistToday(day: Long, baseRoutines: List<Routine>, completedIds: Set<Int>, earnedIdsNow: Set<Int>, refreshCountNow: Int) {
        lastDayEpoch = day; refreshCount = refreshCountNow; earnedIds = earnedIdsNow
        scope.launch { appContext.dataStore.edit { p -> p[Keys.LAST_DAY] = day; p[Keys.ROUTINES] = serializeRoutines(baseRoutines); p[Keys.COMPLETED] = completedIds.joinToString(","); p[Keys.EARNED] = earnedIdsNow.joinToString(","); p[Keys.REFRESH_COUNT] = refreshCountNow } }
        QuestifyWidgetProvider.updateAll(appContext)
    }

    fun updateHistory(day: Long, baseRoutines: List<Routine>, completedIds: Set<Int>) {
        val total = baseRoutines.size; val done = completedIds.size.coerceAtMost(total); val allDone = (total > 0 && done == total)
        pushHistoryStateToSupabase(day = day, done = done, total = total, allDone = allDone)
        scope.launch {
            val prefs = appContext.dataStore.data.first()
            val current = prefs[Keys.HISTORY].orEmpty()
            val map = parseHistory(current).toMutableMap()
            map[day] = HistoryEntry(done = done, total = total, allDone = allDone)
            val trimmed = map.toList().sortedByDescending { it.first }.take(60).associate { it.first to it.second }
            appContext.dataStore.edit { p -> p[Keys.HISTORY] = serializeHistory(trimmed) }
        }
    }

    fun todayBaseAndCompleted(): Pair<List<Routine>, Set<Int>> {
        val base = routines.map { it.copy(completed = false) }
        val doneIds = routines.filter { it.completed }.map { it.id }.toSet()
        return base to doneIds
    }

    fun regenerateForDay(day: Long) {
        val recentFailed = routines.filter { !it.completed }.map { it.title }.toSet()
        val base = generateDailyRoutinesAdaptive(
            seed = daySeedForGeneration(day),
            pool = customTemplatesToRoutineTemplates(customTemplates.filter { it.isActive }),
            history = historyMap,
            recentFailedTitles = recentFailed,
            completedRoutines = routines.filter { it.completed },
            desiredCount = dailyRoutineTarget
        )
        routines = base
        persistToday(day, base, emptySet(), emptySet(), refreshCountNow = 0)
        updateHistory(day, base, emptySet())
    }

    fun finalizePreviousDayIfNeeded(previousDay: Long, currentDay: Long, previousRoutines: List<Routine>) {
        val base = previousRoutines.map { it.copy(completed = false) }
        val doneIds = previousRoutines.filter { it.completed }.map { it.id }.toSet()
        persistCore()
        updateHistory(previousDay, base, doneIds)
    }

    suspend fun load() {
        val before = appContext.dataStore.data.first()
        val beforeVersion = before[Keys.DATA_VERSION] ?: 0
        schemaDowngradeDetected = beforeVersion > CURRENT_DATA_VERSION
        runDataMigrations(appContext)
        val prefs = appContext.dataStore.data.first()
        journalPages = loadJournal(appContext)
        balance = prefs[Keys.BALANCE] ?: 0
// ... inside load() ...
        val savedActive = prefs[activePacksKey]
        if (!savedActive.isNullOrBlank()) {
            val normalizedActive = savedActive
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map {
                    when (it) {
                        "default_pack", REAL_DAILY_LIFE_PACKAGE_ID -> defaultSystemPackageId
                        REAL_WORLD_MOMENTUM_PACKAGE_ID, "saitama_v1", Routine_FEATURE_TEST_PACKAGE_ID -> ""
                        else -> it
                    }
                }
                .filter { it.isNotBlank() }
                .toSet()
            activePackageIds = if (normalizedActive.isNotEmpty()) normalizedActive else setOf(defaultSystemPackageId)
            if (activePackageIds.joinToString(",") != savedActive) {
                appContext.dataStore.edit { p -> p[activePacksKey] = activePackageIds.joinToString(",") }
            }
        }
        autoNewDay = prefs[Keys.AUTO_NEW_DAY] ?: true
        confirmComplete = prefs[Keys.CONFIRM_COMPLETE] ?: true
        refreshIncompleteOnly = prefs[Keys.REFRESH_INCOMPLETE_ONLY] ?: true
        customMode = prefs[Keys.ADMIN_MODE] ?: false
        advancedOptions = prefs[Keys.ADVANCED_OPTIONS] ?: false
        highContrastText = prefs[Keys.HIGH_CONTRAST_TEXT] ?: false
        compactMode = prefs[Keys.COMPACT_MODE] ?: false
        largerTouchTargets = prefs[Keys.LARGE_TOUCH_TARGETS] ?: false
        reduceAnimations = prefs[Keys.REDUCE_ANIMATIONS] ?: false
        val savedDecorative = prefs[Keys.DECORATIVE_BORDERS] ?: false
        neonLightBoost = prefs[Keys.NEON_LIGHT_BOOST] ?: false
        neonFlowEnabled = (prefs[Keys.NEON_FLOW_ENABLED] ?: savedDecorative)
        decorativeBorders = neonFlowEnabled
        neonFlowSpeed = (prefs[Keys.NEON_FLOW_SPEED] ?: 0).coerceIn(0, 2)
        neonGlowPalette = prefs[Keys.NEON_GLOW_PALETTE].orEmpty().ifBlank { "magenta" }
        alwaysShowRoutineProgress = prefs[Keys.ALWAYS_SHOW_ROUTINE_PROGRESS] ?: true
        hideCompletedRoutines = prefs[Keys.HIDE_COMPLETED_routines] ?: false
        confirmDestructiveActions = prefs[Keys.CONFIRM_DESTRUCTIVE] ?: true
        dailyResetHour = (prefs[Keys.DAILY_RESET_HOUR] ?: 0).coerceIn(0, 23)
        dailyRemindersEnabled = prefs[Keys.DAILY_REMINDERS_ENABLED] ?: true
        hapticsEnabled = prefs[Keys.HAPTICS] ?: true
        soundEffectsEnabled = prefs[Keys.SOUND_EFFECTS] ?: true
        onboardingGoal = runCatching { OnboardingGoal.valueOf(prefs[Keys.ONBOARDING_GOAL] ?: OnboardingGoal.BALANCE.name) }
            .getOrDefault(OnboardingGoal.BALANCE)
        premiumUnlocked = prefs[Keys.PREMIUM_UNLOCKED] ?: false
        dailyRoutineTarget = (prefs[Keys.DAILY_ROUTINE_TARGET] ?: 5).coerceIn(3, 10)
        cloudSyncEnabled = prefs[Keys.CLOUD_SYNC_ENABLED] ?: true
        cloudAccountEmail = prefs[Keys.CLOUD_ACCOUNT_EMAIL].orEmpty()
        cloudLastSyncAt = prefs[Keys.CLOUD_LAST_SYNC_AT] ?: 0L
        cloudConnectedAccount = GoogleDriveSync.getLastSignedInAccount(appContext)
        if (cloudConnectedAccount != null && cloudAccountEmail.isBlank()) {
            cloudAccountEmail = cloudConnectedAccount?.email.orEmpty()
        }
        fontStyle = runCatching { AppFontStyle.valueOf(prefs[Keys.FONT_STYLE] ?: AppFontStyle.DEFAULT.name) }.getOrDefault(AppFontStyle.DEFAULT)
        fontScalePercent = (prefs[Keys.FONT_SCALE_PERCENT] ?: 100).coerceIn(80, 125)
        backgroundImageUri = prefs[Keys.BACKGROUND_IMAGE_URI].orEmpty().ifBlank { null }
        backgroundVideoUri = prefs[Keys.BACKGROUND_VIDEO_URI].orEmpty().ifBlank { null }
        backgroundType = prefs[Keys.BACKGROUND_TYPE].orEmpty().ifBlank { "color" }
        backgroundVideoMuted = prefs[Keys.BACKGROUND_VIDEO_MUTED] ?: true
        backgroundImageTintEnabled = prefs[Keys.BACKGROUND_IMAGE_TINT_ENABLED] ?: true
        backgroundImageTransparencyPercent = (prefs[Keys.BACKGROUND_IMAGE_TRANSPARENCY_PERCENT] ?: 78).coerceIn(0, 100)
        accentTransparencyPercent = (prefs[Keys.TRANSPARENCY_ACCENT] ?: 0).coerceIn(0, 100)
        textTransparencyPercent = (prefs[Keys.TRANSPARENCY_TEXT] ?: 0).coerceIn(0, 100)
        appBgTransparencyPercent = (prefs[Keys.TRANSPARENCY_APP_BG] ?: 0).coerceIn(0, 100)
        chromeBgTransparencyPercent = (prefs[Keys.TRANSPARENCY_CHROME_BG] ?: 0).coerceIn(0, 100)
        cardBgTransparencyPercent = (prefs[Keys.TRANSPARENCY_CARD_BG] ?: 0).coerceIn(0, 100)
        journalPageTransparencyPercent = (prefs[Keys.TRANSPARENCY_JOURNAL_PAGE] ?: 0).coerceIn(0, 100)
        journalAccentTransparencyPercent = (prefs[Keys.TRANSPARENCY_JOURNAL_ACCENT] ?: 0).coerceIn(0, 100)
        buttonTransparencyPercent = (prefs[Keys.TRANSPARENCY_BUTTON] ?: 0).coerceIn(0, 100)
        textColorOverride = prefs[Keys.TEXT_COLOR_ARGB]?.let { Color(it) }
        appBackgroundColorOverride = prefs[Keys.APP_BACKGROUND_ARGB]?.let { Color(it) }
        chromeBackgroundColorOverride = prefs[Keys.CHROME_BACKGROUND_ARGB]?.let { Color(it) }
        cardColorOverride = prefs[Keys.CARD_COLOR_ARGB]?.let { Color(it) }
        buttonColorOverride = prefs[Keys.BUTTON_COLOR_ARGB]?.let { Color(it) }
        journalPageColorOverride = prefs[Keys.JOURNAL_PAGE_COLOR_ARGB]?.let { Color(it) }
        journalAccentColorOverride = prefs[Keys.JOURNAL_ACCENT_COLOR_ARGB]?.let { Color(it) }
        journalName = prefs[Keys.JOURNAL_NAME].orEmpty().ifBlank { "Journal" }
        playerName = prefs[Keys.PLAYER_NAME].orEmpty().ifBlank { prefs[stringPreferencesKey("community_user_name")].orEmpty().ifBlank { "Player" } }
        val rawAccessToken = prefs[Keys.AUTH_ACCESS_TOKEN].orEmpty()
        val rawRefreshToken = prefs[Keys.AUTH_REFRESH_TOKEN].orEmpty()
        authAccessToken = decryptAuthState(rawAccessToken)
        authRefreshToken = decryptAuthState(rawRefreshToken)
        authUserEmail = prefs[Keys.AUTH_USER_EMAIL].orEmpty()
        authUserId = prefs[Keys.AUTH_USER_ID].orEmpty()
        isLoggedIn = prefs[Keys.AUTH_PROVIDER].orEmpty().isNotBlank() && authAccessToken.isNotBlank()
        val needsAuthTokenMigration =
            (rawAccessToken.isNotBlank() && !isEncryptedAuthState(rawAccessToken)) ||
                (rawRefreshToken.isNotBlank() && !isEncryptedAuthState(rawRefreshToken))
        if (needsAuthTokenMigration) {
            appContext.dataStore.edit { p ->
                p[Keys.AUTH_ACCESS_TOKEN] = encryptAuthState(authAccessToken)
                p[Keys.AUTH_REFRESH_TOKEN] = encryptAuthState(authRefreshToken)
            }
        }
        healthDailySnapshot = runCatching { Gson().fromJson(prefs[Keys.HEALTH_DAILY_SNAPSHOT].orEmpty(), HealthDailySnapshot::class.java) }
            .getOrNull()
            ?.let(::sanitizeHealthSnapshot)
        val savedLang = prefs[Keys.APP_LANGUAGE].orEmpty().ifBlank { "system" }
        appLanguage = savedLang
        appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit().putString("selected_language", savedLang).apply()

        val themeName = prefs[Keys.APP_THEME]
        appTheme = if (themeName.isNullOrBlank()) {
            if (systemPrefersDark) AppTheme.DEFAULT else AppTheme.LIGHT
        } else {
            parseStoredTheme(themeName)
        }
        val argb = prefs[Keys.ACCENT_ARGB]
        accent = if (argb != null) Color(argb) else fallbackAccentForTheme(appTheme)

        val savedPreset = prefs[Keys.AVATAR_PRESET]; val savedUri = prefs[Keys.AVATAR_URI]
        avatar = when {
            !savedUri.isNullOrBlank() -> Avatar.Custom(savedUri.toUri()); !savedPreset.isNullOrBlank() -> Avatar.Preset(savedPreset); else -> Avatar.Preset("🧑‍🚀")
        }

        characterData = CharacterData(prefs[Keys.CHAR_HEAD] ?: 0xFFFACE8D, prefs[Keys.CHAR_BODY] ?: 0xFF3F51B5, prefs[Keys.CHAR_LEGS] ?: 0xFF212121, prefs[Keys.CHAR_SHOES] ?: 0xFF4E342E)

        inventory = deserializeInventory(prefs[Keys.INVENTORY].orEmpty())
        val savedShop = deserializeCatalogItems(prefs[Keys.CATALOG_ITEMS])
        catalogItems = savedShop
        calendarPlans = deserializeCalendarPlans(prefs[Keys.CALENDAR_PLANS])
        salaryProfile = deserializeSalaryProfile(prefs[Keys.SALARY_PROFILE])
        purchaseTransactions = deserializePurchaseTransactions(prefs[Keys.PURCHASE_TRANSACTIONS])
        financeDefaultItemType = runCatching {
            CatalogItemType.valueOf(prefs[Keys.FINANCE_DEFAULT_ITEM_TYPE] ?: CatalogItemType.WANT.name)
        }.getOrDefault(CatalogItemType.WANT)
        financeWarnThresholdPercent = (prefs[Keys.FINANCE_WARN_THRESHOLD_PERCENT] ?: 20).coerceIn(5, 80)
        financeShowHistoryHints = prefs[Keys.FINANCE_SHOW_HISTORY_HINTS] ?: true

        val storedCustom = prefs[Keys.CUSTOM_TEMPLATES].orEmpty()
        if (storedCustom.isBlank()) {
            val defaults = getInitialDefaultPool()
            customTemplates = defaults
            persistCustomTemplates(defaults)
        } else {
            val decoded = deserializeCustomTemplates(storedCustom)
            val safe = decoded.mapNotNull(::sanitizeHealthTemplateOrNull)
            val droppedCount = decoded.size - safe.size
            customTemplates = safe
            if (droppedCount > 0) {
                AppLog.w("Sanitized $droppedCount invalid health template(s) on load")
                persistCustomTemplates(safe)
            }
        }

        // Load main milestones
        val rawMQ = prefs[Keys.MILESTONES].orEmpty()
        milestones = if (rawMQ.isBlank()) {
            val defaults = getInitialMilestones(); persistMilestones(defaults); defaults
        } else {
            deserializeMilestones(rawMQ)
        }

        // Remove legacy built-in packs and guarantee the new Journey & Balance system pack exists.
        val removableLegacyPackages = legacySystemPackageIds - defaultSystemPackageId
        var customChanged = false
        val filteredCustom = customTemplates.filterNot { removableLegacyPackages.contains(it.packageId) }
        if (filteredCustom.size != customTemplates.size) customChanged = true
        val hasSystemCustom = filteredCustom.any { it.packageId == defaultSystemPackageId }
        customTemplates = if (hasSystemCustom) {
            filteredCustom
        } else {
            customChanged = true
            filteredCustom + getInitialDefaultPool()
        }
        if (customChanged) {
            persistCustomTemplates(customTemplates)
        }

        var milestonesChanged = false
        val filteredMilestones = milestones.filterNot { removableLegacyPackages.contains(it.packageId) }
        if (filteredMilestones.size != milestones.size) milestonesChanged = true
        val hasSystemMilestones = filteredMilestones.any { it.packageId == defaultSystemPackageId }
        milestones = if (hasSystemMilestones) {
            filteredMilestones
        } else {
            milestonesChanged = true
            filteredMilestones + getInitialMilestones()
        }
        if (milestonesChanged) {
            persistMilestones(milestones)
        }

        if (savedActive.isNullOrBlank()) {
            val derivedActivePackages = (
                customTemplates.filter { it.isActive }.map { it.packageId } +
                    milestones.filter { it.isActive }.map { it.packageId }
                )
                .filter { it.isNotBlank() }
                .toSet()
            if (derivedActivePackages.isNotEmpty()) {
                activePackageIds = derivedActivePackages
                appContext.dataStore.edit { p -> p[activePacksKey] = activePackageIds.joinToString(",") }
            }
        }
        val legacyCatalogPrefixes = (legacySystemPackageIds - defaultSystemPackageId).map { "${it}_" }
        var cleanedCatalog = catalogItems.filterNot { item -> legacyCatalogPrefixes.any { prefix -> item.id.startsWith(prefix) } }
        if (activePackageIds.contains(defaultSystemPackageId)) {
            val defaults = getDefaultShopItems()
            val catalogById = cleanedCatalog.associateBy { it.id }.toMutableMap()
            defaults.forEach { defaultItem ->
                if (!catalogById.containsKey(defaultItem.id)) {
                    catalogById[defaultItem.id] = defaultItem
                }
            }
            cleanedCatalog = catalogById.values.toList()
        } else {
            cleanedCatalog = cleanedCatalog.filterNot { it.id == "shop_apple" || it.id == "shop_coffee" }
        }
        if (cleanedCatalog.size != catalogItems.size || cleanedCatalog.toSet() != catalogItems.toSet()) {
            catalogItems = cleanedCatalog
            appContext.dataStore.edit { p -> p[Keys.CATALOG_ITEMS] = serializeCatalogItems(cleanedCatalog) }
        }

        // NEW: Load Template Library
        val decodedSavedTemplates = deserializeSavedTemplates(prefs[Keys.SAVED_TEMPLATES])
        savedTemplates = decodedSavedTemplates
            .filterNot { (legacySystemPackageIds - defaultSystemPackageId).contains(it.packageId) }
        if (savedTemplates.size != decodedSavedTemplates.size) {
            persistSavedTemplates(savedTemplates)
        }
        showWelcomeSetup = !(prefs[Keys.ONBOARDING_DONE] ?: false)
        onboardingSkipIntroDefault = false
        shopTutorialSeen = prefs[Keys.TUTORIAL_CATALOG] ?: false
        calendarTutorialSeen = prefs[Keys.TUTORIAL_CALENDAR] ?: false
        RoutinesTutorialSeen = prefs[Keys.TUTORIAL_ROUTINES] ?: false
        shopHoldHintSeen = prefs[Keys.CATALOG_HOLD_HINT_SEEN] ?: false


        val storedDay = prefs[Keys.LAST_DAY] ?: currentEpochDay(); val storedRoutinesSer = prefs[Keys.ROUTINES]; val storedCompletedSer = prefs[Keys.COMPLETED]; val storedEarnedSer = prefs[Keys.EARNED]; val storedRefresh = prefs[Keys.REFRESH_COUNT] ?: 0; val nowDay = currentEpochDay()

        val storedBase = if (!storedRoutinesSer.isNullOrBlank()) {
            val decoded = deserializeRoutines(storedRoutinesSer)
            val safe = decoded.mapNotNull(::sanitizeHealthRoutineOrNull)
            val unique = ensureUniqueRoutineIds(safe)
            val droppedCount = decoded.size - safe.size
            if (droppedCount > 0) {
                AppLog.w("Sanitized $droppedCount invalid health Routine(s) on load")
            }
            unique
        } else emptyList()
        val storedCompleted = parseIds(storedCompletedSer); val storedEarned = parseIds(storedEarnedSer)
        val storedFull = storedBase.map { q -> if (storedCompleted.contains(q.id)) q.copy(completed = true) else q }

        if (autoNewDay && nowDay > storedDay) {
            if (storedFull.isNotEmpty()) finalizePreviousDayIfNeeded(storedDay, nowDay, storedFull)
            regenerateForDay(nowDay)
        } else {
            lastDayEpoch = storedDay; refreshCount = storedRefresh; earnedIds = storedEarned.intersect(storedBase.map { it.id }.toSet())
            routines = if (storedBase.isNotEmpty()) {
                storedFull
            } else {
                generateDailyRoutinesAdaptive(
                    seed = daySeedForGeneration(storedDay),
                    pool = customTemplatesToRoutineTemplates(customTemplates.filter { it.isActive }),
                    history = historyMap,
                    recentFailedTitles = emptySet(),
                    completedRoutines = emptyList(),
                    desiredCount = dailyRoutineTarget
                ).also { base ->
                    persistToday(storedDay, base, emptySet(), emptySet(), refreshCountNow = 0); updateHistory(storedDay, base, emptySet())
                }
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && dailyRemindersEnabled) {
            DailyReminderWorker.schedule(appContext, dailyResetHour)
        }
    }
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            scope.launch { snackbarHostState.showSnackbar(appContext.getString(R.string.google_sign_in_canceled)) }
            return@rememberLauncherForActivityResult
        }
        val data = result.data
        GoogleDriveSync.resolveSignInResult(data)
            .onSuccess { account ->
                if (account != null) onGoogleAccountConnected(account)
            }
            .onFailure { e ->
                AppLog.w("Google sign-in failed.", e)
                val code = GoogleDriveSync.resolveSignInStatusCode(e)
                val hint = if (code != null) " (code: $code)" else ""
                scope.launch { snackbarHostState.showSnackbar(appContext.getString(R.string.google_sign_in_failed_hint, hint)) }
            }
    }

    val authGoogleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_google_signin_canceled)) }
            return@rememberLauncherForActivityResult
        }
        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            val idToken = account?.idToken
            if (idToken.isNullOrBlank()) {
                scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_google_id_token_failed)) }
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                val session = SupabaseApi.signInWithGoogleIdToken(idToken)
                if (session != null && !session.accessToken.isNullOrBlank()) {
                    authAccessToken = session.accessToken.orEmpty()
                    authRefreshToken = session.refreshToken.orEmpty()
                    authUserEmail = session.user?.email ?: account.email.orEmpty()
                    authUserId = session.user?.id.orEmpty()
                    isLoggedIn = true
                    persistSettings()
                    snackbarHostState.showSnackbar(appContext.getString(R.string.account_signed_in_as_dot, authUserEmail))
                } else {
                    snackbarHostState.showSnackbar(appContext.getString(R.string.google_sign_in_failed_check_config))
                }
            }
        } catch (e: Exception) {
            AppLog.w("Auth Google sign-in failed.", e)
            scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_google_signin_failed)) }
        }
    }

    fun performGoogleLogin() {
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (webClientId.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_google_web_client_missing)) }
            return
        }
        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
        )
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(appContext, gso)
        authGoogleSignInLauncher.launch(client.signInIntent)
    }

    fun performLogout() {
        scope.launch {
            if (authAccessToken.isNotBlank()) {
                SupabaseApi.signOut(authAccessToken)
            }
            authAccessToken = ""
            authRefreshToken = ""
            authUserEmail = ""
            authUserId = ""
            isLoggedIn = false
            persistSettings()
            snackbarHostState.showSnackbar(getString(R.string.snackbar_signed_out))
        }
    }

    LaunchedEffect(Unit) {
        load()
        if (schemaDowngradeDetected) {
            scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_data_schema_newer)) }
        }
        delay(700)
        showIntroSplash = false
        if (cloudSyncEnabled && cloudConnectedAccount != null) {
            triggerCloudSnapshotSync(force = false)
        }

        // Catch incoming shared templates only from expected schemes/hosts.
        val intent = (appContext as? ComponentActivity)?.intent
        if (intent != null && intent.action == Intent.ACTION_VIEW) {
            val dataUri = intent.data
            val trustedSource = when (dataUri?.scheme?.lowercase()) {
                "https" -> dataUri.host.equals("qn8r.github.io", ignoreCase = true) &&
                    (
                        (dataUri.path ?: "").startsWith("/questify", ignoreCase = true) ||
                            (dataUri.path ?: "").startsWith("/ClarityOS", ignoreCase = true)
                        )
                "livinglife" -> dataUri.host.equals("import", ignoreCase = true)
                else -> false
            }
            if (trustedSource) {
                val templateData = dataUri?.getQueryParameter("data")
                if (!templateData.isNullOrBlank()) {
                    if (templateData.length > 250_000) {
                        snackbarHostState.showSnackbar(getString(R.string.snackbar_template_link_too_large))
                    } else {
                        try {
                            val decoded = runCatching {
                                java.net.URLDecoder.decode(templateData, StandardCharsets.UTF_8.name())
                            }.getOrDefault(templateData)
                            val template = importGameTemplate(decoded)
                            if (template != null) {
                                pendingImportTemplate = template
                            } else {
                                snackbarHostState.showSnackbar(getString(R.string.snackbar_template_link_invalid))
                            }
                        } catch (e: Exception) {
                            AppLog.e("Failed to parse incoming template link.", e)
                            snackbarHostState.showSnackbar(getString(R.string.snackbar_read_template_failed))
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(dailyRemindersEnabled, dailyResetHour) {
        if (!dailyRemindersEnabled) {
            DailyReminderWorker.cancel(appContext)
            return@LaunchedEffect
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                DailyReminderWorker.schedule(appContext, dailyResetHour)
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            DailyReminderWorker.schedule(appContext, dailyResetHour)
        }
    }

    fun onRefreshDay() {
        val today = currentEpochDay()
        if (today != lastDayEpoch && routines.isNotEmpty()) { finalizePreviousDayIfNeeded(lastDayEpoch, today, routines) }
        regenerateForDay(today)
        scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_new_day_started)) }
    }

    fun cloneTemplateForLibrary(template: GameTemplate, newName: String): GameTemplate {
        val safeTemplate = normalizeGameTemplateSafe(template)
        val newPkg = "imported_${UUID.randomUUID()}"
        val remappedDaily = safeTemplate.dailyRoutines.map { it.copy(packageId = newPkg) }

        val idMap = safeTemplate.milestones.associate { it.id to UUID.randomUUID().toString() }
        val remappedMain = safeTemplate.milestones.map { q ->
            q.copy(
                id = idMap[q.id] ?: UUID.randomUUID().toString(),
                prerequisiteId = q.prerequisiteId?.let { idMap[it] },
                packageId = newPkg
            )
        }

        return GameTemplate(
            templateName = newName,
            appTheme = safeTemplate.appTheme,
            dailyRoutines = remappedDaily,
            milestones = remappedMain,
            catalogItems = safeTemplate.catalogItems,
            packageId = newPkg,
            templateSettings = safeTemplate.templateSettings,
            accentArgb = safeTemplate.accentArgb
        )
    }
    val advancedTemplateGson = Gson()
    val advancedDailyImportLimit = 500
    val advancedMainImportLimit = 200
    val advancedShopImportLimit = 120
    val advancedTemplatePromptText = """
You are editing a questify template JSON file.

USER Request (highest priority):
==========
{{USER_Request}}
==========

TASK:
- Generate/update daily_routines, main_routines, and optional shop_items to satisfy USER Request.
- {{THEME_POLICY_GOAL}}
- Keep the file import-compatible for questify.

STRICT OUTPUT CONTRACT:
1) Output valid JSON only (no markdown, no commentary).
2) Return ONE full JSON object (not partial patches).
3) Keep required top-level keys: schema_version, template_name, app_theme, accent_argb, daily_routines, main_routines.

SCHEMA RULES:
4) Categories allowed: FITNESS, STUDY, HYDRATION, DISCIPLINE, MIND.
5) daily_routines[] fields: title, category, target, icon, objective_type, optional pinned, image_uri.
6) objective_type must be COUNT|TIMER|HEALTH.
   - COUNT: target >= 1.
   - TIMER: target_seconds required (30..86400). Set target = target_seconds for compatibility.
   - HEALTH: health_metric required in steps|heart_rate|distance_m|calories_kcal.
     health_aggregation optional (prefer daily_total, use daily_avg for heart_rate when Requested).
7) main_routines[] fields: ref, title, description, steps[], prerequisite_ref, optional icon/image_uri.
   - ref must be unique.
   - prerequisite_ref must be null or an existing ref.
   - steps count: 2..8 concise items.
8) shop_items[] is optional: id, name, icon, description, cost, stock, max_stock, consumable, optional image_uri.
   - stock/max_stock range 0..99 and max_stock >= stock.
9) If distribution is not specified, keep daily_routines near-balanced across all 5 categories.
10) Hard caps (TOTAL counts): daily_routines <= $advancedDailyImportLimit, main_routines <= $advancedMainImportLimit, shop_items <= $advancedShopImportLimit.
11) {{THEME_POLICY_RULE}}
12) ai_instructions and guide are optional in final output.

REFERENCE SHAPE (keep names exactly):
{
  "schema_version": 2,
  "template_name": "Name",
  "app_theme": "DEFAULT",
  "accent_argb": 4283215696,
  "daily_routines": [
    {
      "title": "Run 20 minutes",
      "category": "FITNESS",
      "target": 1200,
      "icon": "🏃",
      "objective_type": "TIMER",
      "target_seconds": 1200
    },
    {
      "title": "Hit 7000 steps",
      "category": "FITNESS",
      "target": 7000,
      "icon": "👟",
      "objective_type": "HEALTH",
      "health_metric": "steps",
      "health_aggregation": "daily_total"
    }
  ],
  "main_routines": [],
  "shop_items": []
}

FINAL OUTPUT:
- Return the full updated JSON file only.
- If attachments are supported, use filename questify_advanced_template.json.
""".trimIndent()

    fun buildAdvancedTemplateStarterJson(): String {
        val starterSettings = currentTemplateSettings().copy(backgroundImageUri = null, backgroundVideoUri = null, backgroundType = "color")
        val starter = AdvancedTemplateFile(
            template_name = "AI Generated Template",
            app_theme = appTheme.name,
            accent_argb = accent.toArgbCompat().toLong(),
            ai_instructions = listOf(
                "This JSON file is from questify.",
                "Read USER Request first, then update daily_routines/main_routines/shop_items.",
                "Return ONE valid JSON object only.",
                "Keep required keys: schema_version, template_name, app_theme, accent_argb, daily_routines, main_routines.",
                "For TIMER routines set target_seconds and set target to the same value.",
                "For HEALTH routines set health_metric and optional health_aggregation.",
                "Follow prompt limits/rules; return JSON only."
            ),
            guide = AdvancedTemplateGuide(
                summary = "Workflow: user asks AI for routines, AI edits this file directly, user uploads the returned JSON in Settings > Advanced Templates.",
                ai_prompt_example = "",
                notes = listOf(
                    "daily_routines[]: title, category, target, icon, objective_type.",
                    "objective_type TIMER can include target_seconds (30..86400).",
                    "objective_type HEALTH can include health_metric and health_aggregation.",
                    "main_routines[]: ref, title, description, steps[], prerequisite_ref, optional icon/image_uri.",
                    "shop_items[] is optional: id, name, icon, description, cost, stock, max_stock, consumable, optional image_uri.",
                    "template_settings is optional. Transparency values must be 0..100.",
                    "Counts are totals and capped by the app.",
                    "guide and ai_instructions may be removed from final output."
                )
            ),
            template_settings = starterSettings,
            daily_routines = listOf(
                // FITNESS
                AdvancedDailyRoutineEntry(title = "Run 20 minutes", category = RoutineCategory.FITNESS.name, target = 1200, icon = "🏃", objective_type = "TIMER", target_seconds = 1200),
                AdvancedDailyRoutineEntry(title = "Hit 7000 steps", category = RoutineCategory.FITNESS.name, target = 7000, icon = "👟", objective_type = "HEALTH", health_metric = "steps", health_aggregation = "daily_total"),
                // STUDY
                AdvancedDailyRoutineEntry(title = "Deep work 45 min", category = RoutineCategory.STUDY.name, target = 2700, icon = "🧠", objective_type = "TIMER", target_seconds = 2700),
                AdvancedDailyRoutineEntry(title = "Review 30 flashcards", category = RoutineCategory.STUDY.name, target = 30, icon = "🃏", objective_type = "COUNT"),
                // HYDRATION
                AdvancedDailyRoutineEntry(title = "Drink 8 cups water", category = RoutineCategory.HYDRATION.name, target = 8, icon = "💧", objective_type = "COUNT"),
                AdvancedDailyRoutineEntry(title = "Walk 2500 meters", category = RoutineCategory.HYDRATION.name, target = 2500, icon = "🛣️", objective_type = "HEALTH", health_metric = "distance_m", health_aggregation = "daily_total"),
                // DISCIPLINE
                AdvancedDailyRoutineEntry(title = "Focused cleanup 15 min", category = RoutineCategory.DISCIPLINE.name, target = 900, icon = "🧹", objective_type = "TIMER", target_seconds = 900),
                AdvancedDailyRoutineEntry(title = "Declutter one zone", category = RoutineCategory.DISCIPLINE.name, target = 1, icon = "📦", objective_type = "COUNT"),
                // MIND
                AdvancedDailyRoutineEntry(title = "Meditation 12 min", category = RoutineCategory.MIND.name, target = 720, icon = "🧘", objective_type = "TIMER", target_seconds = 720),
                AdvancedDailyRoutineEntry(title = "Calm average heart rate", category = RoutineCategory.MIND.name, target = 92, icon = "❤️", objective_type = "HEALTH", health_metric = "heart_rate", health_aggregation = "daily_avg")
            ),
            main_routines = listOf(
                AdvancedMilestoneEntry(ref = "mq_1", title = "Build Consistency", description = "Finish 30 focused days in a row.", steps = listOf("Week 1", "Week 2", "Week 3", "Week 4"), icon = "🏆"),
                AdvancedMilestoneEntry(ref = "mq_2", title = "Master Focus", description = "Upgrade your focus routine.", steps = listOf("Baseline", "System", "Mastery"), prerequisite_ref = "mq_1", icon = "🧠")
            ),
            shop_items = listOf(
                AdvancedCatalogItemEntry(name = "Focus Timer Boost", icon = "⏱️", description = "Adds a quick focus session shortcut.", cost = 180, stock = 5, max_stock = 5, consumable = true),
                AdvancedCatalogItemEntry(name = "Calm Theme Pack", icon = "🎨", description = "Unlocks a calm visual preset.", cost = 420, stock = 1, max_stock = 1, consumable = false)
            )
        )
        return advancedTemplateGson.toJson(starter)
    }

    fun buildAdvancedTemplatePromptFromRequest(userRequest: String, allowThemeChanges: Boolean): String {
        val Request = userRequest.trim().ifBlank { "Generate 100 daily routines and 30 main routines in Saitama-style progression." }
        val lower = Request.lowercase(Locale.getDefault())
        val dailyCount = Regex("""(\d{1,4})\s*(daily|day|dailies)""").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val mainCount = Regex("""(\d{1,4})\s*(main|story|milestone)""").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val shopCount = Regex("""(\d{1,4})\s*(shop|item|items|store)""").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val finalDailyCount = dailyCount?.coerceAtMost(advancedDailyImportLimit)
        val finalMainCount = mainCount?.coerceAtMost(advancedMainImportLimit)
        val finalShopCount = shopCount?.coerceAtMost(advancedShopImportLimit)
        val countHint = buildString {
            append("- Hard caps: daily <= $advancedDailyImportLimit, main <= $advancedMainImportLimit, shop_items <= $advancedShopImportLimit.")
            if (allowThemeChanges) {
                append("\n- Theme generation is enabled for this run.")
            } else {
                append("\n- Theme generation is disabled for this run.")
            }
            if (finalDailyCount != null) append("\n- Daily routines target: $finalDailyCount.")
            if (finalMainCount != null) append("\n- Main routines target: $finalMainCount.")
            if (finalShopCount != null) append("\n- Shop items target: $finalShopCount.")
            if (dailyCount != null && finalDailyCount != dailyCount) append("\n- Daily Request was capped from $dailyCount to $finalDailyCount.")
            if (mainCount != null && finalMainCount != mainCount) append("\n- Main Request was capped from $mainCount to $finalMainCount.")
            if (shopCount != null && finalShopCount != shopCount) append("\n- Shop Request was capped from $shopCount to $finalShopCount.")
        }
        val enrichedRequest = if (countHint.isBlank()) Request else Request + "\n\nCOUNT TARGETS:\n" + countHint
        val themePolicyGoal = if (allowThemeChanges) {
            "You may edit daily_routines, main_routines, shop_items, app_theme, accent_argb, and template_settings."
        } else {
            "You may edit daily_routines, main_routines, and shop_items only. Keep app_theme, accent_argb, and template_settings unchanged."
        }
        val themePolicyRule = if (allowThemeChanges) {
            "Theme changes are allowed when they help satisfy USER Request."
        } else {
            "Theme changes are NOT allowed for this run. Do not modify app_theme, accent_argb, or template_settings."
        }
        return advancedTemplatePromptText
            .replace("{{USER_Request}}", enrichedRequest)
            .replace("{{THEME_POLICY_GOAL}}", themePolicyGoal)
            .replace("{{THEME_POLICY_RULE}}", themePolicyRule)
    }

    fun importAdvancedTemplateJson(raw: String): AdvancedTemplateImportResult {
        val payload = raw.trim()
        if (payload.isBlank()) return AdvancedTemplateImportResult(false, "Unnamed", 0, 0, errors = listOf("File is empty."))
        val parsed = runCatching { advancedTemplateGson.fromJson(payload, AdvancedTemplateFile::class.java) }.getOrNull()
            ?: return AdvancedTemplateImportResult(false, "Unnamed", 0, 0, errors = listOf("Invalid JSON format."))
        val supportedSchema = 2
        if (parsed.schema_version > supportedSchema) {
            return AdvancedTemplateImportResult(
                success = false,
                templateName = parsed.template_name.ifBlank { "Unnamed" },
                dailyAdded = 0,
                mainAdded = 0,
                errors = listOf("Unsupported schema_version=${parsed.schema_version}. Supported up to $supportedSchema.")
            )
        }

        val warnings = mutableListOf<String>()
        if (parsed.schema_version < supportedSchema) {
            warnings += "Legacy schema_version=${parsed.schema_version} detected. Applied compatibility migration."
        }
        val templateName = parsed.template_name.trim().ifBlank { "AI Template ${System.currentTimeMillis()}" }.take(60)
        val packageId = "ai_${UUID.randomUUID()}"

        val dailyRaw = parsed.daily_routines.take(advancedDailyImportLimit).mapIndexedNotNull { index, q ->
            val title = q.title.trim().take(64)
            if (title.isBlank()) { warnings += "Daily[$index] skipped: missing title."; return@mapIndexedNotNull null }
            val category = runCatching { RoutineCategory.valueOf(q.category.trim().uppercase(Locale.getDefault())) }.getOrNull()
            if (category == null) { warnings += "Daily[$index] skipped: invalid category '${q.category}'."; return@mapIndexedNotNull null }
            val objectiveType = runCatching { RoutineObjectiveType.valueOf(q.objective_type.trim().uppercase(Locale.getDefault())) }.getOrDefault(RoutineObjectiveType.COUNT)
            val safeHealthMetric = q.health_metric?.trim()?.lowercase(Locale.getDefault())?.takeIf { it in setOf("steps", "heart_rate", "distance_m", "calories_kcal") }
            val safeTargetSeconds = q.target_seconds?.coerceIn(30, 24 * 60 * 60)
            CustomTemplate(
                id = UUID.randomUUID().toString(),
                category = category,
                title = title,
                icon = q.icon.trim().ifBlank { "✅" }.take(3),
                target = if (objectiveType == RoutineObjectiveType.TIMER) (safeTargetSeconds ?: q.target).coerceIn(30, 24 * 60 * 60) else q.target.coerceIn(1, 500),
                isPinned = q.pinned,
                imageUri = q.image_uri?.takeIf { it.isNotBlank() },
                packageId = packageId,
                objectiveType = objectiveType,
                targetSeconds = if (objectiveType == RoutineObjectiveType.TIMER) safeTargetSeconds else null,
                healthMetric = if (objectiveType == RoutineObjectiveType.HEALTH) safeHealthMetric else null,
                healthAggregation = if (objectiveType == RoutineObjectiveType.HEALTH) q.health_aggregation?.trim()?.take(32) else null
            )
        }.distinctBy { it.title.lowercase(Locale.getDefault()) }
        val daily = dailyRaw
        if (parsed.daily_routines.size > advancedDailyImportLimit) warnings += "Daily routines capped to $advancedDailyImportLimit."

        val tempMain = parsed.main_routines.take(advancedMainImportLimit).mapIndexedNotNull { index, q ->
            val title = q.title.trim().take(64)
            if (title.isBlank()) { warnings += "Main[$index] skipped: missing title."; return@mapIndexedNotNull null }
            val steps = q.steps.map { it.trim().take(48) }.filter { it.isNotBlank() }.take(8)
            if (steps.isEmpty()) { warnings += "Main[$index] skipped: at least one step required."; return@mapIndexedNotNull null }
            Triple(q, title, steps)
        }
        if (parsed.main_routines.size > advancedMainImportLimit) warnings += "Main routines capped to $advancedMainImportLimit."
        val refToId = mutableMapOf<String, String>()
        val mainRawToRoutine = tempMain.map { (q, title, steps) ->
            val id = UUID.randomUUID().toString()
            q.ref.trim().takeIf { it.isNotBlank() }?.let { refToId[it] = id }
            q to Milestone(
                id = id,
                title = title,
                description = q.description.trim().take(220),
                steps = steps,
                packageId = packageId,
                icon = q.icon.trim().ifBlank { "🏆" }.take(3),
                imageUri = q.image_uri?.takeIf { it.isNotBlank() }
            )
        }
        val mainBase = mainRawToRoutine.map { (rawMain, routine) ->
            val pre = rawMain.prerequisite_ref?.trim()?.let { refToId[it] }
            if (rawMain.prerequisite_ref != null && pre == null) warnings += "Main '${routine.title}': prerequisite_ref '${rawMain.prerequisite_ref}' not found."
            routine.copy(prerequisiteId = pre)
        }.distinctBy { it.title.lowercase(Locale.getDefault()) }
        val main = mainBase
        val shop = parsed.shop_items.take(advancedShopImportLimit).mapIndexedNotNull { index, s ->
            val name = s.name.trim().take(48)
            if (name.isBlank()) {
                warnings += "Shop[$index] skipped: missing name."
                return@mapIndexedNotNull null
            }
            val safeIdBase = s.id?.trim().orEmpty().ifBlank { name.lowercase(Locale.getDefault()).replace(Regex("[^a-z0-9]+"), "_").trim('_') }
            val safeId = safeIdBase.ifBlank { "shop_${index + 1}" }.take(40)
            CatalogItem(
                id = "${packageId}_$safeId",
                name = name,
                icon = s.icon.trim().ifBlank { "🧩" }.take(3),
                description = s.description.trim().take(140),
                cost = s.cost.coerceIn(20, 20000),
                stock = s.stock.coerceIn(0, 99),
                maxStock = s.max_stock.coerceIn(0, 99).coerceAtLeast(s.stock.coerceIn(0, 99)),
                isConsumable = true,
                imageUri = s.image_uri?.takeIf { it.isNotBlank() }
            )
        }.distinctBy { it.id.lowercase(Locale.getDefault()) }
        if (parsed.shop_items.size > advancedShopImportLimit) warnings += "Shop items capped to $advancedShopImportLimit."

        if (daily.isEmpty() && main.isEmpty() && shop.isEmpty()) {
            return AdvancedTemplateImportResult(false, templateName, 0, 0, warnings = warnings, errors = listOf("No valid routines found in JSON."))
        }

        val theme = runCatching { parseStoredTheme(parsed.app_theme) }.getOrDefault(appTheme)
        val safeTemplateSettings = parsed.template_settings?.copy(
            dailyResetHour = parsed.template_settings.dailyResetHour.coerceIn(0, 23),
            fontScalePercent = parsed.template_settings.fontScalePercent.coerceIn(80, 125),
            neonFlowSpeed = parsed.template_settings.neonFlowSpeed.coerceIn(0, 2),
            backgroundImageTransparencyPercent = parsed.template_settings.backgroundImageTransparencyPercent?.coerceIn(0, 100),
            accentTransparencyPercent = parsed.template_settings.accentTransparencyPercent?.coerceIn(0, 100),
            textTransparencyPercent = parsed.template_settings.textTransparencyPercent?.coerceIn(0, 100),
            appBgTransparencyPercent = parsed.template_settings.appBgTransparencyPercent?.coerceIn(0, 100),
            chromeBgTransparencyPercent = parsed.template_settings.chromeBgTransparencyPercent?.coerceIn(0, 100),
            cardBgTransparencyPercent = parsed.template_settings.cardBgTransparencyPercent?.coerceIn(0, 100),
            journalPageTransparencyPercent = parsed.template_settings.journalPageTransparencyPercent?.coerceIn(0, 100),
            journalAccentTransparencyPercent = parsed.template_settings.journalAccentTransparencyPercent?.coerceIn(0, 100),
            buttonTransparencyPercent = parsed.template_settings.buttonTransparencyPercent?.coerceIn(0, 100)
        )
        val importedTemplate = GameTemplate(
            templateName = templateName,
            appTheme = theme,
            dailyRoutines = customTemplatesToRoutineTemplates(daily),
            milestones = main,
            catalogItems = shop,
            packageId = packageId,
            templateSettings = safeTemplateSettings,
            accentArgb = parsed.accent_argb
        )
        persistSavedTemplates((savedTemplates + importedTemplate).distinctBy { "${it.packageId}|${it.templateName}" })
        return AdvancedTemplateImportResult(true, templateName, daily.size, main.size, packageId = packageId, warnings = warnings)
    }

    fun applyAdvancedImportedTemplate(packageId: String): Boolean {
        val t = savedTemplates.firstOrNull { it.packageId == packageId } ?: return false
        appTheme = normalizeTheme(t.appTheme)
        accent = t.accentArgb?.let { Color(it.toInt()) } ?: fallbackAccentForTheme(appTheme)
        applyTemplateSettings(t.templateSettings)
        persistSettings()
        val mappedDailies = t.dailyRoutines.map { qt ->
            CustomTemplate(
                id = UUID.randomUUID().toString(),
                category = qt.category,
                title = qt.title,
                icon = qt.icon,
                target = qt.target,
                isPinned = qt.isPinned,
                imageUri = qt.imageUri,
                packageId = t.packageId,
                objectiveType = qt.objectiveType,
                targetSeconds = qt.targetSeconds,
                healthMetric = qt.healthMetric,
                healthAggregation = qt.healthAggregation
            )
        }
        persistCustomTemplates(mappedDailies)
        persistMilestones(t.milestones)
        if (t.catalogItems.isNotEmpty()) persistCatalogItems(t.catalogItems)
        activePackageIds = setOf(t.packageId)
        scope.launch { appContext.dataStore.edit { p -> p[activePacksKey] = activePackageIds.joinToString(",") } }
        regenerateForDay(currentEpochDay())
        return true
    }

    fun applyStarterTemplate(template: GameTemplate) {
        appTheme = normalizeTheme(template.appTheme)
        accent = template.accentArgb?.let { Color(it.toInt()) } ?: fallbackAccentForTheme(appTheme)
        applyTemplateSettings(template.templateSettings)
        applyTemplateDailyRoutineDefaults(template.packageId, clearExisting = true)
        persistSettings()
        val mappedDailies = template.dailyRoutines.map { qt ->
            CustomTemplate(
                id = UUID.randomUUID().toString(),
                category = qt.category,
                title = qt.title,
                icon = qt.icon,
                target = qt.target,
                isPinned = qt.isPinned,
                imageUri = qt.imageUri,
                packageId = template.packageId,
                objectiveType = qt.objectiveType,
                targetSeconds = qt.targetSeconds,
                healthMetric = qt.healthMetric,
                healthAggregation = qt.healthAggregation
            )
        }
        persistCustomTemplates(mappedDailies)
        persistMilestones(template.milestones)
        persistCatalogItems(template.catalogItems)
        activePackageIds = setOf(template.packageId)
        scope.launch { appContext.dataStore.edit { p -> p[activePacksKey] = activePackageIds.joinToString(",") } }
        regenerateForDay(currentEpochDay())
    }

    fun awardBonusReward(uniqueId: Int) {
        if (earnedIds.contains(uniqueId)) return
        earnedIds = earnedIds + uniqueId; SoundManager.playSuccess(); persistCore()
        val (base, completedIds) = todayBaseAndCompleted(); persistToday(lastDayEpoch, base, completedIds, earnedIds, refreshCount); updateHistory(lastDayEpoch, base, completedIds)
    }

    fun awardFocusReward(@Suppress("UNUSED_PARAMETER") minutes: Int) {
        SoundManager.playSuccess(); persistCore()
    }

    fun onUpdateRoutineProgress(id: Int, newProgress: Int) {
        timerPersistJob?.cancel()
        timerPersistJob = null
        val updated = routines.map { q ->
            if (q.id == id) q.copy(currentProgress = newProgress.coerceIn(0, q.target + 1)) else q
        }
        routines = updated

        // Save immediately so it survives Theme Changes
        val (base, completedIds) = todayBaseAndCompleted()
        persistToday(lastDayEpoch, base, completedIds, earnedIds, refreshCount)
    }

    fun onTimerTickProgress(id: Int, newProgress: Int) {
        val before = routines.firstOrNull { it.id == id } ?: return
        val clamped = newProgress.coerceIn(0, before.target + 1)
        if (before.currentProgress == clamped) return

        routines = routines.map { q ->
            if (q.id == id) q.copy(currentProgress = clamped) else q
        }

        timerPersistJob?.cancel()
        timerPersistJob = scope.launch {
            delay(5000)
            val (base, completedIds) = todayBaseAndCompleted()
            persistToday(lastDayEpoch, base, completedIds, earnedIds, refreshCount)
            timerPersistJob = null
        }
    }

    fun onTimerComplete(id: Int, newProgress: Int) {
        timerPersistJob?.cancel()
        timerPersistJob = null

        val before = routines.firstOrNull { it.id == id } ?: return
        val clamped = newProgress.coerceIn(0, before.target + 1)
        if (before.currentProgress != clamped) {
            routines = routines.map { q ->
                if (q.id == id) q.copy(currentProgress = clamped) else q
            }
        }

        val (base, completedIds) = todayBaseAndCompleted()
        persistToday(lastDayEpoch, base, completedIds, earnedIds, refreshCount)
    }

    fun flushTimerPersist() {
        val pending = timerPersistJob
        timerPersistJob = null
        pending?.cancel()
        val (base, completedIds) = todayBaseAndCompleted()
        persistToday(lastDayEpoch, base, completedIds, earnedIds, refreshCount)
    }

    fun onUpdateRoutineProgressWithUndo(id: Int, newProgress: Int) {
        val before = routines.firstOrNull { it.id == id } ?: return
        val clamped = newProgress.coerceIn(0, before.target + 1)
        if (before.currentProgress == clamped) return

        onUpdateRoutineProgress(id, clamped)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val message = when {
                clamped > before.target -> appContext.getString(R.string.progress_marked_done)
                clamped == before.target -> appContext.getString(R.string.progress_marked_done)
                clamped <= 0 -> appContext.getString(R.string.progress_reset)
                clamped == 1 -> appContext.getString(R.string.routine_started_msg)
                else -> appContext.getString(R.string.progress_value_of_target, clamped, before.target)
            }
            val undoResult = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "UNDO",
                duration = SnackbarDuration.Short
            )
            if (undoResult == SnackbarResult.ActionPerformed) {
                val latest = routines.firstOrNull { it.id == id } ?: return@launch
                if (!latest.completed) {
                    onUpdateRoutineProgress(id, before.currentProgress)
                }
            }
        }
    }

    fun onResetRoutineProgressWithUndo(id: Int) {
        val before = routines.firstOrNull { it.id == id } ?: return
        if (!before.completed && before.currentProgress <= 0) return

        val hadReward = earnedIds.contains(id)
        var newEarned = earnedIds
        if (hadReward) { newEarned = newEarned - id }

        routines = routines.map { q ->
            if (q.id == id) q.copy(currentProgress = 0, completed = false) else q
        }
        earnedIds = newEarned
        persistCore()

        val (base, completedIds) = todayBaseAndCompleted()
        persistToday(lastDayEpoch, base, completedIds, earnedIds, refreshCount)
        updateHistory(lastDayEpoch, base, completedIds)

        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val undoResult = snackbarHostState.showSnackbar(
                message = "Routine progress reset",
                actionLabel = "UNDO",
                duration = SnackbarDuration.Short
            )
            if (undoResult == SnackbarResult.ActionPerformed) {
                val latest = routines.firstOrNull { it.id == id } ?: return@launch
                if (latest.completed || latest.currentProgress != 0) return@launch

                var undoEarned = earnedIds
                if (hadReward && !undoEarned.contains(id)) {
                    undoEarned = undoEarned + id
                }

                routines = routines.map { q -> if (q.id == id) before else q }
                earnedIds = undoEarned
                persistCore()

                val (undoBase, undoCompletedIds) = todayBaseAndCompleted()
                persistToday(lastDayEpoch, undoBase, undoCompletedIds, earnedIds, refreshCount)
                updateHistory(lastDayEpoch, undoBase, undoCompletedIds)
            }
        }
    }

    fun onRemoveRoutineFromToday(id: Int) {
        var newEarned = earnedIds
        if (newEarned.contains(id)) { newEarned = newEarned - id }

        routines = routines.filterNot { it.id == id }
        earnedIds = newEarned
        persistCore()

        val (base, completedIds) = todayBaseAndCompleted()
        persistToday(lastDayEpoch, base, completedIds, earnedIds, refreshCount)
        updateHistory(lastDayEpoch, base, completedIds)
        scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_routine_removed)) }
    }

    fun onOpenRoutineEditorFromHome(id: Int) {
        val targetRoutine = routines.firstOrNull { it.id == id } ?: return
        fun stableMatches(template: CustomTemplate): Boolean {
            val q = RoutineTemplate(
                category = template.category,
                title = template.title,
                icon = template.icon,
                target = template.target,
                isPinned = template.isPinned,
                imageUri = template.imageUri,
                packageId = template.packageId,
                objectiveType = template.objectiveType,
                targetSeconds = template.targetSeconds,
                healthMetric = template.healthMetric,
                healthAggregation = template.healthAggregation
            )
            return stableRoutineId(template.category, q) == targetRoutine.id
        }
        var matchedTemplate = customTemplates.firstOrNull(::stableMatches)
            ?: customTemplates.firstOrNull {
                it.title.equals(targetRoutine.title, ignoreCase = true) &&
                    it.category == targetRoutine.category &&
                    it.packageId == targetRoutine.packageId
            }
            ?: customTemplates.firstOrNull {
                it.title.equals(targetRoutine.title, ignoreCase = true) &&
                    it.category == targetRoutine.category
            }
            ?: customTemplates.firstOrNull { it.title.equals(targetRoutine.title, ignoreCase = true) }

        if (matchedTemplate == null) {
            val created = CustomTemplate(
                id = UUID.randomUUID().toString(),
                category = targetRoutine.category,
                title = targetRoutine.title,
                icon = targetRoutine.icon.ifBlank { "⭐" },
                target = when (targetRoutine.objectiveType) {
                    RoutineObjectiveType.HEALTH -> targetRoutine.target.coerceAtLeast(100)
                    RoutineObjectiveType.TIMER -> (targetRoutine.targetSeconds ?: targetRoutine.target).coerceAtLeast(60)
                    else -> targetRoutine.target.coerceAtLeast(1)
                },
                isPinned = false,
                imageUri = targetRoutine.imageUri,
                packageId = targetRoutine.packageId,
                objectiveType = targetRoutine.objectiveType,
                targetSeconds = if (targetRoutine.objectiveType == RoutineObjectiveType.TIMER) {
                    (targetRoutine.targetSeconds ?: targetRoutine.target).coerceAtLeast(60)
                } else null,
                healthMetric = if (targetRoutine.objectiveType == RoutineObjectiveType.HEALTH) targetRoutine.healthMetric else null,
                healthAggregation = if (targetRoutine.objectiveType == RoutineObjectiveType.HEALTH) {
                    targetRoutine.healthAggregation ?: "daily_total"
                } else null
            )
            persistCustomTemplates(customTemplates + created)
            matchedTemplate = created
        }
        RoutinesPreferredTab = 0
        pendingHomeEditDailyTemplateId = matchedTemplate?.id
        screen = Screen.ROUTINES
    }

    fun onToggleRoutine(id: Int, force: Boolean = false) {
        val target = routines.firstOrNull { it.id == id } ?: return
        if (!force && target.completed && confirmComplete) { pendingUncheckRoutineId = id; return }

        var newEarned = earnedIds

        val updated = routines.map { q ->
            if (q.id != id) return@map q
            val newCompleted = !q.completed

            if (newCompleted) {
                if (!newEarned.contains(q.id)) {
                    newEarned = newEarned + q.id
                    SoundManager.playSuccess()
                }
            } else {
                if (newEarned.contains(q.id)) {
                    newEarned = newEarned - q.id
                }
            }
            q.copy(completed = newCompleted)
        }

        routines = updated; earnedIds = newEarned
        persistCore()

        val (base, completedIds) = todayBaseAndCompleted()
        persistToday(lastDayEpoch, base, completedIds, earnedIds, refreshCount)
        updateHistory(lastDayEpoch, base, completedIds)
        val completedNow = updated.firstOrNull { it.id == id }?.completed == true
        if (completedNow) {
            AppLog.event("Routine_done", "Routine=$id")
        }
    }

    fun onCompleteRoutineWithUndo(id: Int) {
        val RoutineBefore = routines.firstOrNull { it.id == id } ?: return
        if (RoutineBefore.completed || RoutineBefore.currentProgress < RoutineBefore.target) return

        onToggleRoutine(id)
        val completed = routines.firstOrNull { it.id == id }?.completed == true
        if (!completed) return

        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val undoResult = snackbarHostState.showSnackbar(
                message = appContext.getString(R.string.snackbar_routine_completed),
                actionLabel = "UNDO",
                duration = SnackbarDuration.Short
            )
            if (undoResult == SnackbarResult.ActionPerformed) {
                val latestRoutine = routines.firstOrNull { it.id == id } ?: return@launch
                if (latestRoutine.completed) {
                    onToggleRoutine(id, force = true)
                }
            }
        }
    }

    fun onBuyShopItem(item: CatalogItem) {
        scope.launch {
            val result = performCatalogPurchase(
                context = appContext,
                itemId = item.id,
                requireSalaryProfile = true
            )
            if (!result.success) {
                when (result.failure) {
                    CatalogPurchaseFailure.INVALID_PRICE -> snackbarHostState.showSnackbar(appContext.getString(R.string.invalid_item_price))
                    CatalogPurchaseFailure.OUT_OF_STOCK -> snackbarHostState.showSnackbar(appContext.getString(R.string.item_out_of_stock, item.name))
                    CatalogPurchaseFailure.SALARY_NOT_SET -> snackbarHostState.showSnackbar(appContext.getString(R.string.catalog_purchase_no_salary_hint))
                    CatalogPurchaseFailure.INSUFFICIENT_BALANCE -> snackbarHostState.showSnackbar(getString(R.string.snackbar_not_enough_balance))
                    CatalogPurchaseFailure.ITEM_NOT_FOUND -> snackbarHostState.showSnackbar(getString(R.string.snackbar_item_not_found))
                    null -> snackbarHostState.showSnackbar(getString(R.string.snackbar_purchase_failed))
                }
                return@launch
            }

            catalogItems = result.updatedCatalogItems
            balance = result.updatedBalance
            if (salaryProfile != null) {
                purchaseTransactions = result.updatedTransactions
            }
            SoundManager.playSuccess()
            val purchasedName = result.purchasedItem?.name ?: item.name
            AppLog.event("shop_buy", "item=${item.id},cost=${item.cost}")
            snackbarHostState.showSnackbar(appContext.getString(R.string.used_item_name, purchasedName))
        }
    }

    fun onUpsertShopItem(item: CatalogItem) {
        if (!customMode) {
            scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_custom_mode_required)) }
            return
        }
        val existing = catalogItems.firstOrNull { it.id == item.id }
        val cappedMax = item.maxStock.coerceAtLeast(1)
        val cappedStock = item.stock.coerceIn(0, cappedMax)
        val safeCost = item.cost.coerceAtLeast(1)
        val baseHistory = existing?.priceHistory.orEmpty()
        val shouldAppendPrice = existing == null || existing.cost != safeCost || baseHistory.isEmpty()
        val nextHistory = if (shouldAppendPrice) {
            (baseHistory + PricePoint(price = safeCost, epoch = System.currentTimeMillis())).takeLast(50)
        } else {
            baseHistory
        }
        val sanitized = item.copy(
            maxStock = cappedMax,
            stock = cappedStock,
            cost = safeCost,
            isConsumable = true,
            type = item.type ?: CatalogItemType.WANT,
            priceHistory = nextHistory
        )
        val list = catalogItems.toMutableList()
        val idx = list.indexOfFirst { it.id == sanitized.id }
        if (idx >= 0) list[idx] = sanitized else list.add(sanitized)
        persistCatalogItems(list)
        scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_catalog_item_saved)) }
    }

    fun onDeleteShopItem(id: String) {
        if (!customMode) {
            scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_custom_mode_required)) }
            return
        }
        val removed = catalogItems.firstOrNull { it.id == id } ?: return
        persistCatalogItems(catalogItems.filterNot { it.id == id })
        scope.launch {
            val res = snackbarHostState.showSnackbar(appContext.getString(R.string.catalog_item_removed), actionLabel = "UNDO", duration = SnackbarDuration.Short)
            if (res == SnackbarResult.ActionPerformed) {
                persistCatalogItems((catalogItems + removed).distinctBy { it.id })
            }
        }
    }

    fun onConsumeItem(item: InventoryItem) {
        if (item.ownedCount > 0) {
            val newInv = if (item.ownedCount > 1) {
                inventory.map { if (it.id == item.id) it.copy(ownedCount = it.ownedCount - 1) else it }
            } else {
                inventory.filter { it.id != item.id }
            }
            persistInventory(newInv); scope.launch { snackbarHostState.showSnackbar(appContext.getString(R.string.used_item_name, item.name)) }
        }
    }

    fun onAddPlan(day: Long, text: String) {
        val clean = text.trim()
        if (clean.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_plan_title_required)) }
            return
        }
        val next = calendarPlans.toMutableMap()
        val current = next[day].orEmpty()
        next[day] = current + clean
        persistCalendarPlans(next)
        pushPlanStateToSupabase(next)
        AppLog.event("plan_add", "day=$day")
        scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_plan_added)) }
    }

    fun onDeletePlan(day: Long, index: Int) {
        val next = calendarPlans.toMutableMap()
        val current = next[day].orEmpty().toMutableList()
        if (index !in current.indices) return
        val removed = current.removeAt(index)
        if (current.isEmpty()) next.remove(day) else next[day] = current
        persistCalendarPlans(next)
        pushPlanStateToSupabase(next)
        scope.launch {
            val res = snackbarHostState.showSnackbar(getString(R.string.snackbar_plan_removed), actionLabel = getString(R.string.undo), duration = SnackbarDuration.Short)
            if (res == SnackbarResult.ActionPerformed) {
                val restored = calendarPlans.toMutableMap()
                val list = restored[day].orEmpty().toMutableList()
                val insertAt = index.coerceIn(0, list.size)
                list.add(insertAt, removed)
                restored[day] = list
                persistCalendarPlans(restored)
                pushPlanStateToSupabase(restored)
            }
        }
    }

    fun restoreDefaultRoutines() {
        val defaults = getInitialDefaultPool()

        // Merge logic: If we have a default Routine, update its target/definition to the new one
        val currentMap = customTemplates.associateBy { it.title }.toMutableMap()
        var added = 0

        defaults.forEach { def ->
            if (!currentMap.containsKey(def.title)) {
                currentMap[def.title] = def
                added++
            } else {
                // Update existing default to new target (e.g. Water 1 -> Water 2)
                val existing = currentMap[def.title]!!
                if (existing.target != def.target) {
                    currentMap[def.title] = existing.copy(target = def.target)
                }
            }
        }

        persistCustomTemplates(currentMap.values.toList())
        scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_routine_pool_updated)) }
    }
    fun onRefreshTodayRoutines() {
        if (homeRefreshInProgress) return
        homeRefreshInProgress = true
        try {
            if (refreshCount >= 3) {
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        message = "Refresh limit reached",
                        duration = SnackbarDuration.Short,
                        withDismissAction = true // Adds "X" button
                    )
                }
                return
            }

            val seed = System.currentTimeMillis()
            val refreshed = if (refreshIncompleteOnly) {
                refreshKeepingCompleted(
                    current = routines,
                    seed = seed,
                    pool = customTemplatesToRoutineTemplates(customTemplates.filter { it.isActive }),
                    desiredCount = dailyRoutineTarget
                )
            } else {
                generateDailyRoutinesAdaptive(
                    seed = secondSeedForGeneration(seed / 1000L),
                    pool = customTemplatesToRoutineTemplates(customTemplates.filter { it.isActive }),
                    history = historyMap,
                    recentFailedTitles = routines.filter { !it.completed }.map { it.title }.toSet(),
                    completedRoutines = routines.filter { it.completed },
                    desiredCount = dailyRoutineTarget
                )
            }

            routines = refreshed
            refreshCount += 1

            val base = refreshed.map { it.copy(completed = false) }
            val completedIds = refreshed.filter { it.completed }.map { it.id }.toSet()

            persistToday(lastDayEpoch, base, completedIds, earnedIds, refreshCount)
            updateHistory(lastDayEpoch, base, completedIds)

            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(
                    message = "routines rerolled",
                    duration = SnackbarDuration.Short,
                    withDismissAction = true // Adds "X" button
                )
            }
        } finally {
            scope.launch {
                delay(450)
                homeRefreshInProgress = false
            }
        }
    }

    fun onDeleteMilestone(id: String) {
        persistMilestones(milestones.filterNot { it.id == id }); scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_deleted)) }
    }

    fun onUpdateMilestone(updated: Milestone) {
        // FIXED: Capture the previous state BEFORE we update the list
        // Save the new state
        persistMilestones(milestones.map { if (it.id == updated.id) updated else it })
        SoundManager.playSuccess()
    }

    fun onResetMilestoneWithUndo(id: String) {
        val before = milestones.find { it.id == id } ?: return
        val reset = before.copy(currentStep = 0)
        if (before == reset) return

        persistMilestones(milestones.map { if (it.id == id) reset else it })
        persistCore()

        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val undoResult = snackbarHostState.showSnackbar(
                message = "Main Routine reset",
                actionLabel = "UNDO",
                duration = SnackbarDuration.Short
            )
            if (undoResult == SnackbarResult.ActionPerformed) {
                val latest = milestones.find { it.id == id } ?: return@launch
                if (latest != reset) return@launch

                persistMilestones(milestones.map { if (it.id == id) before else it })
                persistCore()
            }
        }
    }

    fun resetAll(saveBackup: Boolean) {
        scope.launch {
            val backupTemplate = if (saveBackup) {
                GameTemplate(
                    templateName = resetBackupName.ifBlank { "Pre-reset backup" },
                    appTheme = appTheme,
                    dailyRoutines = customTemplatesToRoutineTemplates(customTemplates),
                    milestones = milestones,
                    catalogItems = catalogItems,
                    templateSettings = currentTemplateSettings(),
                    accentArgb = accent.toArgbCompat().toLong()
                )
            } else null
            appContext.dataStore.edit { it.clear() }
            screen = Screen.HOME
            balance = 0; refreshCount = 0
            lastDayEpoch = currentEpochDay()
            earnedIds = emptySet()
            routines = emptyList()
            journalPages = emptyList()
            grimoirePageIndex = 0
            avatar = Avatar.Preset("🧑‍🚀")
            characterData = CharacterData()

            autoNewDay = true; confirmComplete = true; refreshIncompleteOnly = true
            customMode = false
            advancedOptions = false
            highContrastText = false
            compactMode = false
            largerTouchTargets = false
            reduceAnimations = false
            decorativeBorders = false
            neonLightBoost = false
            neonFlowEnabled = false
            neonFlowSpeed = 0
            neonGlowPalette = "magenta"
            alwaysShowRoutineProgress = true
            hideCompletedRoutines = false
            confirmDestructiveActions = true
            dailyResetHour = 0
            dailyRemindersEnabled = true
            hapticsEnabled = true
            soundEffectsEnabled = true
            onboardingGoal = OnboardingGoal.BALANCE
            premiumUnlocked = false
            dailyRoutineTarget = 5
            settingsExpandedSection = "hub"
            RoutinesPreferredTab = 0
            cloudSyncEnabled = true
            cloudAccountEmail = ""
            cloudConnectedAccount = null
            cloudLastSyncAt = 0L
            fontStyle = AppFontStyle.DEFAULT
            fontScalePercent = 100
            backgroundImageUri = null
            backgroundImageTransparencyPercent = 78
            textColorOverride = null
            appBackgroundColorOverride = null
            chromeBackgroundColorOverride = null
            cardColorOverride = null
            buttonColorOverride = null
            journalPageColorOverride = null
            journalAccentColorOverride = null
            journalName = "Journal"
            val defaultTemplate = getDefaultGameTemplate()
            appTheme = if (systemPrefersDark) AppTheme.DEFAULT else AppTheme.LIGHT
            accent = defaultTemplate.accentArgb?.let { Color(it.toInt()) } ?: fallbackAccentForTheme(appTheme)

            inventory = emptyList()
            catalogItems = defaultTemplate.catalogItems
            calendarPlans = emptyMap()
            activePackageIds = setOf(defaultTemplate.packageId)
            shopTutorialSeen = false
            calendarTutorialSeen = false
            RoutinesTutorialSeen = false
            shopHoldHintSeen = false

            val defaults = defaultTemplate.dailyRoutines.map { qt ->
                CustomTemplate(
                    id = UUID.randomUUID().toString(),
                    category = qt.category,
                    title = qt.title,
                    icon = qt.icon,
                    target = qt.target,
                    isPinned = qt.isPinned,
                    imageUri = qt.imageUri,
                    packageId = defaultTemplate.packageId
                )
            }
            customTemplates = defaults
            val mqDefaults = defaultTemplate.milestones
            val templatesAfterReset = listOfNotNull(backupTemplate)
            savedTemplates = templatesAfterReset

            persistCore(); persistSettings(); persistAvatar(avatar); persistCustomTemplates(defaults)
            persistCharacter(characterData); persistMilestones(mqDefaults)
            persistInventory(emptyList())
            persistCatalogItems(catalogItems)
            persistCalendarPlans(emptyMap())
            persistSavedTemplates(templatesAfterReset)
            appContext.dataStore.edit { p -> p[Keys.ONBOARDING_DONE] = false }
            appContext.dataStore.edit { p -> p[activePacksKey] = activePackageIds.joinToString(",") }
            customTemplatesToRoutineTemplates(customTemplates.filter { it.isActive })
            showWelcomeSetup = true
            onboardingSkipIntroDefault = false
            snackbarHostState.showSnackbar(getString(R.string.snackbar_reset_complete))
        }
    }

    LaunchedEffect(soundEffectsEnabled) {
        SoundManager.setEnabled(soundEffectsEnabled)
    }
    LaunchedEffect(hapticsEnabled) {
        SoundManager.setHapticsEnabled(hapticsEnabled)
    }

    LivingLifeMMOTheme(
        theme = runtimeTheme,
        accentOverride = accentStrong,
        backgroundOverride = themeBg,
        cardColorOverride = cardColorOverride,
        highContrastTextEnabled = highContrastText,
        reduceAnimationsEnabled = reduceAnimations,
        compactModeEnabled = compactMode,
        largerTouchTargetsEnabled = largerTouchTargets,
        decorativeBordersEnabled = neonFlowEnabled,
        neonLightBoostEnabled = neonLightBoost,
        neonFlowEnabled = neonFlowEnabled,
        neonFlowSpeed = neonFlowSpeed,
        neonGlowPalette = neonGlowPalette,
        fontStyle = fontStyle,
        fontScalePercent = fontScalePercent,
        textColorOverride = textColorOverride
    ) {
        ThemeRuntime.accentTransparencyPercent = accentTransparencyPercent
        ThemeRuntime.textTransparencyPercent = textTransparencyPercent
        ThemeRuntime.appBgTransparencyPercent = appBgTransparencyPercent
        ThemeRuntime.chromeBgTransparencyPercent = chromeBgTransparencyPercent
        ThemeRuntime.cardBgTransparencyPercent = cardBgTransparencyPercent
        ThemeRuntime.journalPageTransparencyPercent = journalPageTransparencyPercent
        ThemeRuntime.journalAccentTransparencyPercent = journalAccentTransparencyPercent
        ThemeRuntime.buttonTransparencyPercent = buttonTransparencyPercent
        if (showLoginRequiredDialog) {
            AlertDialog(
                onDismissRequest = { showLoginRequiredDialog = false },
                title = { Text(stringResource(R.string.l10n_sign_in_required), color = OnCardText) },
                text = { Text(stringResource(R.string.l10n_you_need_to_sign_in_with_google_to_use_thi), color = OnCardText.copy(alpha = 0.8f)) },
                confirmButton = {
                    TextButton(onClick = {
                        showLoginRequiredDialog = false
                        settingsExpandedSection = "hub"
                        screen = Screen.SETTINGS
                    }) { Text(stringResource(R.string.l10n_go_to_settings)) }
                },
                dismissButton = {
                    TextButton(onClick = { showLoginRequiredDialog = false }) {
                        Text(stringResource(R.string.cancel), color = OnCardText.copy(alpha = 0.6f))
                    }
                },
                containerColor = CardDarkBlue
            )
        }
        if (showRefreshDayConfirm) { AlertDialog(onDismissRequest = { showRefreshDayConfirm = false }, title = { Text(stringResource(R.string.l10n_start_new_day)) }, text = { Text(stringResource(R.string.l10n_this_forces_a_new_day_calculation)) }, confirmButton = { TextButton(onClick = { onRefreshDay(); showRefreshDayConfirm = false }) { Text(stringResource(R.string.l10n_start_day)) } }, dismissButton = { TextButton(onClick = { showRefreshDayConfirm = false }) { Text(stringResource(R.string.cancel)) } }) }
        if (showBackupImport) {
            AlertDialog(
                onDismissRequest = { showBackupImport = false },
                title = { Text(stringResource(R.string.l10n_import_encrypted_backup)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.l10n_paste_your_encrypted_backup_import_applies), color = OnCardText.copy(alpha = 0.75f), fontSize = 12.sp)
                        OutlinedTextField(
                            value = backupImportPayload,
                            onValueChange = { backupImportPayload = it },
                            label = { Text(stringResource(R.string.l10n_backup_payload)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val ok = importBackupPayload(backupImportPayload)
                        showBackupImport = false
                        backupImportPayload = ""
                        scope.launch { snackbarHostState.showSnackbar(if (ok) "Backup imported." else "Backup import failed.") }
                    }) { Text(stringResource(R.string.l10n_import)) }
                },
                dismissButton = {
                    TextButton(onClick = { showBackupImport = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
        if (showResetAll) {
            AlertDialog(
                onDismissRequest = { showResetAll = false },
                title = { Text(stringResource(R.string.l10n_reset_everything)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.l10n_this_will_erase_progress_and_active_data))
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { resetBackupBefore = !resetBackupBefore },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = resetBackupBefore, onCheckedChange = { resetBackupBefore = it })
                        Text(stringResource(R.string.l10n_save_current_setup_to_template_before_rese))
                        }
                        if (resetBackupBefore) {
                            OutlinedTextField(
                                value = resetBackupName,
                                onValueChange = { resetBackupName = it },
                                label = { Text(stringResource(R.string.l10n_backup_name)) },
                                singleLine = true
                            )
                        }
                        Text(stringResource(R.string.l10n_default_package_will_be_enabled_automatica))
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        resetAll(resetBackupBefore)
                        showResetAll = false
                    }) { Text(stringResource(R.string.l10n_reset)) }
                },
                dismissButton = { TextButton(onClick = { showResetAll = false }) { Text(stringResource(R.string.cancel)) } }
            )
        }
        if (pendingUncheckRoutineId != null) { AlertDialog(onDismissRequest = { pendingUncheckRoutineId = null }, title = { Text(stringResource(R.string.l10n_uncheck_routine)) }, text = { Text("This will uncheck the routine.") }, confirmButton = { TextButton(onClick = { val id = pendingUncheckRoutineId; pendingUncheckRoutineId = null; if (id != null) onToggleRoutine(id, force = true) }) { Text(stringResource(R.string.l10n_uncheck)) } }, dismissButton = { TextButton(onClick = { pendingUncheckRoutineId = null }) { Text(stringResource(R.string.cancel)) } }) }
        if (showFocusTimer) { FocusTimerDialog(accentStrong = accentStrong, accentSoft = accentSoft, onDismiss = { showFocusTimer = false }, onComplete = { minutes -> awardFocusReward(minutes); showFocusTimer = false }) }

// 1. Initial Import Dialog
        if (pendingImportTemplate != null) {
            AlertDialog(
                onDismissRequest = { pendingImportTemplate = null },
                containerColor = CardDarkBlue,
                title = { Text(stringResource(R.string.l10n_save_template), color = accentStrong, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(stringResource(R.string.l10n_you_are_about_to_save), color = OnCardText)
                        Text(stringResource(R.string.template_bullet_name, pendingImportTemplate!!.templateName), fontWeight = FontWeight.Bold, color = accentStrong)
                        Text(stringResource(R.string.template_bullet_routine_count, pendingImportTemplate!!.dailyRoutines.size), color = OnCardText)
                        Text(stringResource(R.string.template_bullet_milestone_count, pendingImportTemplate!!.milestones.size), color = OnCardText)
                        Text(stringResource(R.string.template_bullet_catalog_count, pendingImportTemplate!!.catalogItems.size), color = OnCardText)
                        Text(stringResource(R.string.template_bullet_theme_name, pendingImportTemplate!!.appTheme.name), color = accentStrong)
                        Text(stringResource(R.string.l10n_includes_advanced_options_background_if_pr), color = OnCardText.copy(alpha = 0.85f))
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val t = pendingImportTemplate!!
                        persistSavedTemplates(savedTemplates + t)
                        promptApplyTemplate = t // NEW: Trigger the follow-up dialog
                        pendingImportTemplate = null
                    }) { Text(stringResource(R.string.save_template_lib), color = accentStrong) }
                },
                dismissButton = { TextButton(onClick = { pendingImportTemplate = null }) { Text(stringResource(R.string.cancel), color = OnCardText) } }
            )
        }

// 2. Follow-Up Apply Prompt (With Backup & Clear Checkboxes)
        if (promptApplyTemplate != null) {
            AlertDialog(
                onDismissRequest = { promptApplyTemplate = null },
                containerColor = CardDarkBlue,
                title = { Text(stringResource(R.string.equip_template_title), color = accentStrong, fontWeight = FontWeight.Bold) },
                text = {
                    val t = promptApplyTemplate!!
                    val dailyDelta = t.dailyRoutines.size - customTemplates.size
                    val mainDelta = t.milestones.size - milestones.size
                    val shopDelta = t.catalogItems.size - catalogItems.size
                    val minCost = t.catalogItems.minOfOrNull { it.cost.coerceAtLeast(1) } ?: 0
                    val affordableCount = t.catalogItems.count { balance >= it.cost.coerceAtLeast(1) }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.l10n_template_saved_are_you_sure_you_want_to_ap), color = OnCardText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.l10n_this_will_change_theme_background_advanced), color = OnCardText.copy(alpha = 0.85f), fontSize = 13.sp)
                        Text(
                            stringResource(R.string.template_preview_delta_line, if (dailyDelta >= 0) "+$dailyDelta" else "$dailyDelta", if (mainDelta >= 0) "+$mainDelta" else "$mainDelta", if (shopDelta >= 0) "+$shopDelta" else "$shopDelta"),
                            color = OnCardText.copy(alpha = 0.84f),
                            fontSize = 12.sp
                        )
                        Text(
                            stringResource(R.string.template_economy_line, if (minCost > 0) appContext.getString(R.string.template_economy_min_item_balance, minCost) else appContext.getString(R.string.template_economy_no_catalog_items), affordableCount, t.catalogItems.size),
                            color = OnCardText.copy(alpha = 0.72f),
                            fontSize = 12.sp
                        )
                        HorizontalDivider(color = OnCardText.copy(alpha=0.1f))

                        // NEW: Clear Existing Checkbox
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { importClearExisting = !importClearExisting }) {
                            Checkbox(checked = importClearExisting, onCheckedChange = { importClearExisting = it }, colors = CheckboxDefaults.colors(checkedColor = accentStrong))
                            Text(stringResource(R.string.clear_existing_routines), color = OnCardText, fontSize = 14.sp)
                        }

                        // Backup Checkbox
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { importBackupBeforeApply = !importBackupBeforeApply }) {
                            Checkbox(checked = importBackupBeforeApply, onCheckedChange = { importBackupBeforeApply = it }, colors = CheckboxDefaults.colors(checkedColor = accentStrong))
                            Text(stringResource(R.string.backup_current_setup), color = OnCardText, fontSize = 14.sp)
                        }
                        if (importBackupBeforeApply) {
                            OutlinedTextField(value = importBackupName, onValueChange = { importBackupName = it }, label = { Text(stringResource(R.string.backup_name_label), color = OnCardText.copy(alpha=0.5f)) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = OnCardText, unfocusedTextColor = OnCardText, cursorColor = accentStrong))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        // 1. Handle Backup First!
                        if (importBackupBeforeApply) {
                            val backupNameFinal = importBackupName.ifBlank { "My Backup" }
                            val backup = GameTemplate(
                                backupNameFinal,
                                appTheme,
                                customTemplatesToRoutineTemplates(customTemplates),
                                milestones,
                                catalogItems,
                                templateSettings = currentTemplateSettings(),
                                accentArgb = accent.toArgbCompat().toLong()
                            )
                            persistSavedTemplates(savedTemplates + backup)
                        }

                        // 2. Apply the Imported Template
                        val t = promptApplyTemplate!!
                        appTheme = normalizeTheme(t.appTheme)
                        accent = t.accentArgb?.let { Color(it.toInt()) } ?: fallbackAccentForTheme(appTheme)
                        applyTemplateSettings(t.templateSettings)
                        persistSettings()

                        val mappedDailies = t.dailyRoutines.map { qt ->
                            CustomTemplate(
                                id = UUID.randomUUID().toString(),
                                category = qt.category,
                                title = qt.title,
                                icon = qt.icon,
                                target = qt.target,
                                isPinned = qt.isPinned,
                                imageUri = qt.imageUri,
                                packageId = t.packageId
                            )
                        }

                        if (importClearExisting) {
                            persistCustomTemplates(mappedDailies)
                            persistMilestones(t.milestones)
                            persistCatalogItems(t.catalogItems)
                            activePackageIds = setOf(t.packageId)
                            applyTemplateDailyRoutineDefaults(t.packageId, clearExisting = true)
                            persistSettings()
                        } else {
                            val customBase = customTemplates.filterNot { it.packageId == t.packageId }
                            persistCustomTemplates(mergeCustomTemplatesDistinct(customBase, mappedDailies))
                            val milestoneBase = milestones.filterNot { it.packageId == t.packageId }
                            val mergedMilestones = milestoneBase + remapMilestonesForPackage(
                                incoming = t.milestones,
                                packageId = t.packageId,
                                existing = milestoneBase
                            )
                            persistMilestones(mergedMilestones.distinctBy { it.id })
                            if (t.catalogItems.isNotEmpty()) {
                                val shopBase = catalogItems.filterNot { it.id.startsWith("${t.packageId}_") }
                                val mergedShop = (shopBase + t.catalogItems).distinctBy { it.id }
                                persistCatalogItems(mergedShop)
                            }
                            activePackageIds = activePackageIds + t.packageId
                        }
                        scope.launch { appContext.dataStore.edit { p -> p[activePacksKey] = activePackageIds.joinToString(",") } }
                        regenerateForDay(currentEpochDay())

                        scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_theme_applied)) }
                        promptApplyTemplate = null
                    }) { Text(stringResource(R.string.l10n_yes_equip_now), color = accentStrong) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_template_saved)) }
                        promptApplyTemplate = null
                    }) { Text(stringResource(R.string.l10n_no_later), color = OnCardText) }
                }
            )
        }

        fun onTogglePackage(template: GameTemplate, isActive: Boolean) {
            val pid = template.packageId

            if (isActive) {
                // ENABLE: Add ID and routines
                activePackageIds = activePackageIds + pid

                // 1. Add Dailies (Avoid duplicates)
                val newDailies = template.dailyRoutines.map { qt ->
                    CustomTemplate(
                        id = UUID.randomUUID().toString(),
                        category = qt.category,
                        title = qt.title,
                        icon = qt.icon,
                        target = qt.target,
                        isPinned = qt.isPinned,
                        imageUri = qt.imageUri,
                        packageId = pid,
                        isActive = true,
                        objectiveType = qt.objectiveType,
                        targetSeconds = qt.targetSeconds,
                        healthMetric = qt.healthMetric,
                        healthAggregation = qt.healthAggregation
                    )
                }
                persistCustomTemplates(mergeCustomTemplatesDistinct(customTemplates, newDailies))

                // 2. Add Main routines
                val newMains = remapMilestonesForPackage(
                    incoming = template.milestones,
                    packageId = pid,
                    existing = milestones
                )
                persistMilestones((milestones + newMains).distinctBy { it.id })

                // 3. Add Shop Items
                val prefixedShop = template.catalogItems.map { it.copy(id = "${pid}_${it.id}".take(64)) }
                val currentShopIds = catalogItems.map { it.id }.toSet()
                val shopToAdd = prefixedShop.filterNot { currentShopIds.contains(it.id) }
                persistCatalogItems(catalogItems + shopToAdd)

                SoundManager.playAccept() // Nice feedback

            } else {
                // DISABLE: Remove ID and routines
                activePackageIds = activePackageIds - pid

                // 1. Remove Dailies belonging to this pack
                persistCustomTemplates(customTemplates.filter { it.packageId != pid })

                // 2. Remove Main routines belonging to this pack
                persistMilestones(milestones.filter { it.packageId != pid })

                // 3. Remove Shop Items belonging to this pack
                val afterPackageRemoval = catalogItems.filterNot { it.id.startsWith("${pid}_") }
                val finalShop = if (pid == defaultSystemPackageId) {
                    afterPackageRemoval.filterNot { it.id == "shop_apple" || it.id == "shop_coffee" }
                } else {
                    afterPackageRemoval
                }
                persistCatalogItems(finalShop)

                SoundManager.playClick()
            }

            // Save the active list
            scope.launch { appContext.dataStore.edit { p -> p[activePacksKey] = activePackageIds.joinToString(",") } }
            regenerateForDay(currentEpochDay())
        }
        fun swipeNavIndex(target: Screen): Float = when (target) {
            Screen.HOME -> 0f
            Screen.MILESTONE -> 1f
            Screen.GRIMOIRE -> 2f
            else -> 0f
        }
        fun swipeNavEmphasis(target: Screen): Float {
            val usingSwipeScreens = screen == Screen.HOME || screen == Screen.MILESTONE || screen == Screen.GRIMOIRE
            val visual = if (usingSwipeScreens) swipeVisualProgress.coerceIn(0f, 2f) else swipeNavIndex(screen)
            return (1f - abs(visual - swipeNavIndex(target))).coerceIn(0f, 1f)
        }
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = true,
            drawerContent = {
                val drawerShape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp)
                ModalDrawerSheet(
                    modifier = Modifier
                        .clip(drawerShape)
                        .background(drawerBg),
                    drawerContainerColor = drawerBg
                ) {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.app_name), modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = drawerContentColor)
                    DrawerItem(stringResource(R.string.nav_home), Icons.Default.Home, screen == Screen.HOME, accentStrong, drawerBg, drawerContentColor) { SoundManager.playClick(); screen = Screen.HOME; scope.launch { drawerState.close() } }
                    DrawerItem(stringResource(R.string.nav_dashboard), Icons.Default.QueryStats, screen == Screen.STATS, accentStrong, drawerBg, drawerContentColor) { SoundManager.playClick(); screen = Screen.STATS; scope.launch { drawerState.close() } }
                    DrawerItem(stringResource(R.string.title_catalog), Icons.Default.Backpack, screen == Screen.INVENTORY, accentStrong, drawerBg, drawerContentColor) { SoundManager.playClick(); screen = Screen.INVENTORY; scope.launch { drawerState.close() } }
                    DrawerItem(stringResource(R.string.title_calendar), Icons.Default.Today, screen == Screen.CALENDAR, accentStrong, drawerBg, drawerContentColor) { SoundManager.playClick(); screen = Screen.CALENDAR; scope.launch { drawerState.close() } }
                    DrawerItem(stringResource(R.string.title_routines_templates), Icons.Default.Checklist, screen == Screen.ROUTINES, accentStrong, drawerBg, drawerContentColor) { SoundManager.playClick(); RoutinesPreferredTab = 0; screen = Screen.ROUTINES; scope.launch { drawerState.close() } }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = drawerContentColor.copy(alpha = 0.12f), modifier = Modifier.padding(horizontal = 16.dp))
                    DrawerItem(stringResource(R.string.title_settings), Icons.Default.Settings, screen == Screen.SETTINGS, accentStrong, drawerBg, drawerContentColor) { SoundManager.playClick(); screen = Screen.SETTINGS; scope.launch { drawerState.close() } }
                    DrawerItem(stringResource(R.string.title_about), Icons.Default.Info, screen == Screen.ABOUT, accentStrong, drawerBg, drawerContentColor) { SoundManager.playClick(); screen = Screen.ABOUT; scope.launch { drawerState.close() } }
                }
            }
        ) {
            Scaffold(
                containerColor = themeBg,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = {
                    SnackbarHost(hostState = snackbarHostState) { data ->
                        // FIXED: Detect swipe and dismiss IMMEDIATELY to free up the FAB
                        @Suppress("DEPRECATION")
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value != SwipeToDismissBoxValue.Settled) {
                                    data.dismiss() // Kill the timer and the box instantly
                                    true
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {}, // No background needed
                            content = {
                                Snackbar(
                                    snackbarData = data,
                                    containerColor = Color(0xFF333333), // Dark Grey
                                    contentColor = Color.White,
                                    actionColor = accentStrong,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        )
                    }
                },
                bottomBar = {
                    if (screen == Screen.HOME || screen == Screen.MILESTONE || screen == Screen.GRIMOIRE) {
                        Surface(color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                            val navDockShape = RoundedCornerShape(18.dp)
                            val navDockBg = Color.Transparent
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                    .clip(navDockShape)
                                    .background(navDockBg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TikTokNavButton(stringResource(R.string.title_daily_routines), Icons.Default.Checklist, screen == Screen.HOME, accentStrong, navContentColor, navBarBg, emphasis = swipeNavEmphasis(Screen.HOME)) { SoundManager.playClick(); screen = Screen.HOME }
                                    TikTokNavButton(stringResource(R.string.title_main_routines), Icons.Default.Star, screen == Screen.MILESTONE, accentStrong, navContentColor, navBarBg, emphasis = swipeNavEmphasis(Screen.MILESTONE)) { SoundManager.playClick(); screen = Screen.MILESTONE }
                                    TikTokNavButton(stringResource(R.string.title_journal), Icons.AutoMirrored.Filled.MenuBook, screen == Screen.GRIMOIRE, accentStrong, navContentColor, navBarBg, emphasis = swipeNavEmphasis(Screen.GRIMOIRE)) { SoundManager.playClick(); screen = Screen.GRIMOIRE }
                                }
                            }
                        }
                    }
                }) { padding ->
                val drawerClosed = drawerState.currentValue == DrawerValue.Closed && !drawerState.isAnimationRunning
                val RoutinesScreenContent: @Composable () -> Unit = {
                    RoutinesScreen(
                                                   modifier = Modifier.fillMaxSize().padding(padding),
                                                   accentStrong = accentStrong,
                                                   accentSoft = accentSoft,
                                                   customMode = customMode,
                                                   dailyTemplates = customTemplates,
                                                   milestones = milestones,
                                                   savedTemplates = savedTemplates,
                                                   activePackageIds = activePackageIds, // NEW
                                                   onTogglePackage = { t, b -> onTogglePackage(t, b) }, // NEW
                                                    onUpsertDaily = { t ->
                                                        if (!customMode) {
                                                            scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_custom_mode_required)) }
                                                            return@RoutinesScreen
                                                        }
                                                        val safeTemplate = sanitizeHealthTemplateOrNull(t)
                                                        if (safeTemplate == null) {
                                                            scope.launch { snackbarHostState.showSnackbar("Invalid health Routine metric. Use steps, heart_rate, distance_m, or calories_kcal.") }
                                                            return@RoutinesScreen
                                                        }
                                                        val list = customTemplates.toMutableList()
                                                        val idx = list.indexOfFirst { it.id == safeTemplate.id }
                                                        val oldTemplate = if (idx >= 0) list[idx] else null
                                                        val isNewTemplate = idx < 0
                                                        if (idx >= 0) list[idx] = safeTemplate else list.add(safeTemplate)
                                                        persistCustomTemplates(list)
                                                        if (isNewTemplate) {
                                                            regenerateForDay(currentEpochDay())
                                                        } else if (oldTemplate != null) {
                                                           fun stableId(template: CustomTemplate): Int {
                                                               val qt = RoutineTemplate(
                                                                   category = template.category,
                                                                   title = template.title,
                                                                   icon = template.icon,
                                                                   target = template.target,
                                                                   isPinned = template.isPinned,
                                                                   imageUri = template.imageUri,
                                                                   packageId = template.packageId,
                                                                   objectiveType = template.objectiveType,
                                                                   targetSeconds = template.targetSeconds,
                                                                   healthMetric = template.healthMetric,
                                                                   healthAggregation = template.healthAggregation
                                                               )
                                                               return stableRoutineId(template.category, qt)
                                                           }
                                                           val oldRoutineId = stableId(oldTemplate)
                                                           val matchIndex = routines.indexOfFirst { q ->
                                                               q.id == oldRoutineId ||
                                                                   (q.title.equals(oldTemplate.title, ignoreCase = true) &&
                                                                       q.category == oldTemplate.category &&
                                                                       q.packageId == oldTemplate.packageId)
                                                           }
                                                           if (matchIndex >= 0) {
                                                                val newRoutineId = stableId(safeTemplate)
                                                                val existing = routines[matchIndex]
                                                                val nextTarget = when (safeTemplate.objectiveType) {
                                                                    RoutineObjectiveType.TIMER -> (safeTemplate.targetSeconds ?: safeTemplate.target).coerceAtLeast(60)
                                                                    RoutineObjectiveType.HEALTH -> safeTemplate.target.coerceAtLeast(100)
                                                                    RoutineObjectiveType.COUNT -> safeTemplate.target.coerceAtLeast(1)
                                                                }
                                                                val updatedRoutine = existing.copy(
                                                                    id = newRoutineId,
                                                                    title = safeTemplate.title,
                                                                    icon = safeTemplate.icon,
                                                                    category = safeTemplate.category,
                                                                    target = nextTarget,
                                                                    currentProgress = if (existing.completed) nextTarget else existing.currentProgress.coerceAtMost(nextTarget),
                                                                    imageUri = safeTemplate.imageUri,
                                                                    packageId = safeTemplate.packageId,
                                                                    objectiveType = safeTemplate.objectiveType,
                                                                    targetSeconds = if (safeTemplate.objectiveType == RoutineObjectiveType.TIMER) nextTarget else null,
                                                                    healthMetric = if (safeTemplate.objectiveType == RoutineObjectiveType.HEALTH) safeTemplate.healthMetric else null,
                                                                    healthAggregation = if (safeTemplate.objectiveType == RoutineObjectiveType.HEALTH) (safeTemplate.healthAggregation ?: "daily_total") else null
                                                                )
                                                               val nextRoutines = routines.toMutableList()
                                                               nextRoutines[matchIndex] = updatedRoutine
                                                               routines = nextRoutines
                                                               if (oldRoutineId != newRoutineId && earnedIds.contains(oldRoutineId)) {
                                                                   earnedIds = (earnedIds - oldRoutineId) + newRoutineId
                                                               }
                                                               val (base, completedIds) = todayBaseAndCompleted()
                                                               persistToday(lastDayEpoch, base, completedIds, earnedIds, refreshCount)
                                                               updateHistory(lastDayEpoch, base, completedIds)
                                                           }
                                                       }
                                                        scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_daily_routine_saved)) }
                                                   },
                                                   onDeleteDaily = { id ->
                                                       if (!customMode) {
                                                           scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_custom_mode_required)) }
                                                           return@RoutinesScreen
                                                       }
                                                       val list = customTemplates.filterNot { it.id == id }
                                                       persistCustomTemplates(list)
                                                            scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_daily_routine_deleted)) }
                                                   },
                                                   onUpsertMain = { mq ->
                                                       if (!customMode) {
                                                           scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_custom_mode_required)) }
                                                           return@RoutinesScreen
                                                       }
                                                       val list = milestones.toMutableList()
                                                       val idx = list.indexOfFirst { it.id == mq.id }
                                                       if (idx >= 0) list[idx] = mq else list.add(mq)
                                                       persistMilestones(list)
                                                        scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_main_routine_saved)) }
                                                   },
                                                   onDeleteMain = { id ->
                                                       if (!customMode) {
                                                           scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_custom_mode_required)) }
                                                           return@RoutinesScreen
                                                       }
                                                       val list = milestones.filterNot { it.id == id }
                                                       persistMilestones(list)
                                                            scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_main_routine_deleted)) }
                                                   },
                                                   onRestoreDefaults = { restoreDefaultRoutines() },
                                                   onExportTemplate = { templateName ->
                                                       val template = GameTemplate(
                                                           templateName,
                                                           appTheme,
                                                           customTemplatesToRoutineTemplates(customTemplates),
                                                           milestones,
                                                           catalogItems,
                                                           templateSettings = currentTemplateSettings(),
                                                           accentArgb = accent.toArgbCompat().toLong()
                                                       )
                                                       val compressedPayload = exportGameTemplate(template)
                                                       val link = "https://qn8r.github.io/questify/?data=$compressedPayload"
                                                       val sendIntent = Intent().apply { action = Intent.ACTION_SEND; putExtra(Intent.EXTRA_TEXT, "Check out my questify Template: $templateName!\n\n$link"); type = "text/plain" }
                                                       appContext.startActivity(Intent.createChooser(sendIntent, getString(R.string.share_template)))
                                                   },
                                                   onSaveCurrentToLibrary = { templateName ->
                                                       val template = GameTemplate(
                                                           templateName,
                                                           appTheme,
                                                           customTemplatesToRoutineTemplates(customTemplates),
                                                           milestones,
                                                           catalogItems,
                                                           templateSettings = currentTemplateSettings(),
                                                           accentArgb = accent.toArgbCompat().toLong()
                                                       )
                                                       persistSavedTemplates(savedTemplates + template)
                                                       scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_template_saved)) }
                                                   },
                                                    onApplySavedTemplate = { t, backupName, clearExisting ->
                                                        val safeTemplate = runCatching { normalizeGameTemplateSafe(t) }.getOrElse {
                                                             scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_incompatible_template)) }
                                                            return@RoutinesScreen
                                                       }
                                                       if (!backupName.isNullOrBlank()) {
                                                           val backup = GameTemplate(
                                                               backupName,
                                                               appTheme,
                                                               customTemplatesToRoutineTemplates(customTemplates),
                                                               milestones,
                                                               catalogItems,
                                                               templateSettings = currentTemplateSettings(),
                                                               accentArgb = accent.toArgbCompat().toLong()
                                                           )
                                                           persistSavedTemplates(savedTemplates + backup)
                                                       }
                                                       appTheme = normalizeTheme(safeTemplate.appTheme)
                                                       accent = safeTemplate.accentArgb?.let { Color(it.toInt()) } ?: fallbackAccentForTheme(appTheme)
                                                       applyTemplateSettings(safeTemplate.templateSettings)
                                                       persistSettings()

                                                       val mappedDailies = safeTemplate.dailyRoutines.map { qt ->
                                                           CustomTemplate(
                                                               id = UUID.randomUUID().toString(),
                                                               category = qt.category,
                                                               title = qt.title,
                                                               icon = qt.icon,
                                                               target = qt.target,
                                                               isPinned = qt.isPinned,
                                                               imageUri = qt.imageUri,
                                                               packageId = safeTemplate.packageId,
                                                               objectiveType = qt.objectiveType,
                                                               targetSeconds = qt.targetSeconds,
                                                               healthMetric = qt.healthMetric,
                                                               healthAggregation = qt.healthAggregation
                                                           )
                                                       }

                                                        if (clearExisting) {
                                                            persistCustomTemplates(mappedDailies)
                                                            persistMilestones(safeTemplate.milestones)
                                                            persistCatalogItems(safeTemplate.catalogItems)
                                                           activePackageIds = setOf(safeTemplate.packageId)
                                                           applyTemplateDailyRoutineDefaults(safeTemplate.packageId, clearExisting = true)
                                                           persistSettings()
                                                        } else {
                                                            val customBase = customTemplates.filterNot { it.packageId == safeTemplate.packageId }
                                                            persistCustomTemplates(mergeCustomTemplatesDistinct(customBase, mappedDailies))
                                                            val milestoneBase = milestones.filterNot { it.packageId == safeTemplate.packageId }
                                                            val mergedMilestones = milestoneBase + remapMilestonesForPackage(
                                                                incoming = safeTemplate.milestones,
                                                                packageId = safeTemplate.packageId,
                                                                existing = milestoneBase
                                                            )
                                                            persistMilestones(mergedMilestones.distinctBy { it.id })
                                                            if (safeTemplate.catalogItems.isNotEmpty()) {
                                                                val shopBase = catalogItems.filterNot { it.id.startsWith("${safeTemplate.packageId}_") }
                                                                val mergedShop = (shopBase + safeTemplate.catalogItems).distinctBy { it.id }
                                                                persistCatalogItems(mergedShop)
                                                            }
                                                            activePackageIds = activePackageIds + safeTemplate.packageId
                                                        }
                                                        scope.launch { appContext.dataStore.edit { p -> p[activePacksKey] = activePackageIds.joinToString(",") } }
                                                        regenerateForDay(currentEpochDay())
                                                        scope.launch { snackbarHostState.showSnackbar(appContext.getString(R.string.theme_routines_applied)) }
                                                    },
                                                   onDeleteSavedTemplate = { t ->
                                                        persistSavedTemplates(savedTemplates.filterNot { it == t })
                                                        scope.launch {
                                                            val res = snackbarHostState.showSnackbar(appContext.getString(R.string.template_deleted), actionLabel = "UNDO", duration = SnackbarDuration.Short)
                                                            if (res == SnackbarResult.ActionPerformed) {
                                                                persistSavedTemplates((savedTemplates + t).distinctBy { "${it.packageId}|${it.templateName}" })
                                                            }
                                                        }
                                                    },
                                                    onRequireCustomMode = {
                                                        scope.launch { snackbarHostState.showSnackbar(appContext.getString(R.string.enable_custom_mode_add_routines)) }
                                                    },
                                                    onDeleteCategory = { cat ->
                                                        val list = customTemplates.filterNot { it.category == cat }
                                                        persistCustomTemplates(list)
                                                        scope.launch { snackbarHostState.showSnackbar(getString(R.string.all_category_routines_deleted, categoryLabel(cat))) }
                                                    },
                                                    onDeleteChain = { family, packageId ->
                                                        fun parseFamily(title: String): String {
                                                            val m = Regex("""^(.*?)(?:\s+(\d+))$""").find(title.trim())
                                                            return (m?.groupValues?.getOrNull(1)?.trim().takeUnless { it.isNullOrBlank() } ?: title.trim())
                                                        }
                                                        val familyKey = parseFamily(family).lowercase(java.util.Locale.getDefault())
                                                        val list = milestones.filterNot {
                                                            it.packageId == packageId &&
                                                                parseFamily(it.title).lowercase(java.util.Locale.getDefault()) == familyKey
                                                        }
                                                        persistMilestones(list)
                                                         scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_family_routines_deleted, family)) }
                                                    },
                                                   onOpenCommunityTemplates = { /* removed */ },
                                                   onOpenAdvancedTemplates = {
                                                       settingsExpandedSection = "advanced_templates"
                                                       screen = Screen.SETTINGS
                                                   },
                                                   showTutorial = !RoutinesTutorialSeen,
                                                   onTutorialDismiss = { markRoutinesTutorialSeen() },
                                                    initialTab = RoutinesPreferredTab,
                                                    openDailyEditorForId = pendingHomeEditDailyTemplateId,
                                                    onOpenDailyEditorHandled = { pendingHomeEditDailyTemplateId = null },
                                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                                    onOpenSettings = { screen = Screen.SETTINGS }
                                                )
                }
                val onBackgroundImageTransparencyPercentChangedRef: (Int) -> Unit = { backgroundImageTransparencyPercent = it.coerceIn(0, 100); persistSettings() }
                val onAccentTransparencyChangedRef: (Int) -> Unit = { accentTransparencyPercent = it.coerceIn(0, 100); persistSettings() }
                val onTextTransparencyChangedRef: (Int) -> Unit = { textTransparencyPercent = it.coerceIn(0, 100); persistSettings() }
                val onAppBgTransparencyChangedRef: (Int) -> Unit = { appBgTransparencyPercent = it.coerceIn(0, 100); persistSettings() }
                val onChromeBgTransparencyChangedRef: (Int) -> Unit = { chromeBgTransparencyPercent = it.coerceIn(0, 100); persistSettings() }
                val onCardBgTransparencyChangedRef: (Int) -> Unit = { cardBgTransparencyPercent = it.coerceIn(0, 100); persistSettings() }
                val onJournalPageTransparencyChangedRef: (Int) -> Unit = { journalPageTransparencyPercent = it.coerceIn(0, 100); persistSettings() }
                val onJournalAccentTransparencyChangedRef: (Int) -> Unit = { journalAccentTransparencyPercent = it.coerceIn(0, 100); persistSettings() }
                val onButtonTransparencyChangedRef: (Int) -> Unit = { buttonTransparencyPercent = it.coerceIn(0, 100); persistSettings() }
                val settingsScreenContent: @Composable () -> Unit = {
                    SettingsScreen(
                                                   modifier = Modifier.fillMaxSize().padding(padding),
                                                   autoNewDay = autoNewDay,
                                                   confirmComplete = confirmComplete,
                                                   refreshIncompleteOnly = refreshIncompleteOnly,
                                                   customMode = customMode,
                                                   advancedOptions = advancedOptions,
                                                   highContrastText = highContrastText,
                                                   compactMode = compactMode,
                                                   largerTouchTargets = largerTouchTargets,
                                                   reduceAnimations = reduceAnimations,
                                                   decorativeBorders = neonFlowEnabled,
                                                   neonLightBoost = neonLightBoost,
                                                   neonFlowEnabled = neonFlowEnabled,
                                                   neonFlowSpeed = neonFlowSpeed,
                                                   neonGlowPalette = neonGlowPalette,
                                                   alwaysShowRoutineProgress = alwaysShowRoutineProgress,
                                                   hideCompletedRoutines = hideCompletedRoutines,
                                                   confirmDestructiveActions = confirmDestructiveActions,
                                                    dailyResetHour = dailyResetHour,
                                                    dailyRoutineTarget = dailyRoutineTarget,
                                                    expandedSection = settingsExpandedSection,
                                                    financeFocusRequestNonce = financeFocusRequestNonce,
                                                    premiumUnlocked = premiumUnlocked,
                                                   cloudSyncEnabled = cloudSyncEnabled,
                                                    cloudConnected = isLoggedIn,
                                                    cloudAccountEmail = if (isLoggedIn) authUserEmail else "",
                                                    cloudLastSyncAt = cloudLastSyncAt,
                                                    salaryProfile = salaryProfile,
                                                    remainingBudget = remainingBudgetExact,
                                                    recentTransactions = purchaseTransactions,
                                                    financeDefaultItemType = financeDefaultItemType,
                                                    financeWarnThresholdPercent = financeWarnThresholdPercent,
                                                    financeShowHistoryHints = financeShowHistoryHints,
                                                    dailyRemindersEnabled = dailyRemindersEnabled,
                                                    hapticsEnabled = hapticsEnabled,
                                                    soundEffectsEnabled = soundEffectsEnabled,
                                                   fontStyle = fontStyle,
                                                   fontScalePercent = fontScalePercent,
                                                   appLanguage = appLanguage,
                                                   backgroundImageTransparencyPercent = backgroundImageTransparencyPercent,
                                                   accentTransparencyPercent = accentTransparencyPercent,
                                                   textTransparencyPercent = textTransparencyPercent,
                                                   appBgTransparencyPercent = appBgTransparencyPercent,
                                                   chromeBgTransparencyPercent = chromeBgTransparencyPercent,
                                                   cardBgTransparencyPercent = cardBgTransparencyPercent,
                                                   journalPageTransparencyPercent = journalPageTransparencyPercent,
                                                   journalAccentTransparencyPercent = journalAccentTransparencyPercent,
                                                   buttonTransparencyPercent = buttonTransparencyPercent,
                                                   journalName = journalName,
                                                   textColorOverride = textColorOverride,
                                                   appBackgroundColorOverride = appBackgroundColorOverride,
                                                   chromeBackgroundColorOverride = chromeBackgroundColorOverride,
                                                   cardColorOverride = cardColorOverride,
                                                   buttonColorOverride = buttonColorOverride,
                                                   journalPageColorOverride = journalPageColorOverride,
                                                   journalAccentColorOverride = journalAccentColorOverride,
                                                   appTheme = appTheme,
                                                   accentStrong = accentStrong,
                                                   accentSoft = accentSoft,
                                                   onAutoNewDayChanged = { autoNewDay = it; persistSettings() },
                                                   onConfirmCompleteChanged = { confirmComplete = it; persistSettings() },
                                                   onRefreshIncompleteOnlyChanged = { refreshIncompleteOnly = it; persistSettings() },
                                                   onCustomModeChanged = { customMode = it; persistSettings() },
                                                   onAdvancedOptionsChanged = { advancedOptions = it; persistSettings() },
                                                   onHighContrastTextChanged = { highContrastText = it; persistSettings() },
                                                   onCompactModeChanged = { compactMode = it; persistSettings() },
                                                   onLargeTouchTargetsChanged = { largerTouchTargets = it; persistSettings() },
                                                   onReduceAnimationsChanged = { reduceAnimations = it; persistSettings() },
                                                   onDecorativeBordersChanged = {
                                                       decorativeBorders = it
                                                       neonFlowEnabled = it
                                                       persistSettings()
                                                   },
                                                   onNeonLightBoostChanged = { neonLightBoost = it; persistSettings() },
                                                   onNeonFlowEnabledChanged = {
                                                       neonFlowEnabled = it
                                                       decorativeBorders = it
                                                       persistSettings()
                                                   },
                                                   onNeonFlowSpeedChanged = { neonFlowSpeed = it.coerceIn(0, 2); persistSettings() },
                                                   onNeonGlowPaletteChanged = { neonGlowPalette = it.ifBlank { "magenta" }; persistSettings() },
                                                   onAlwaysShowRoutineProgressChanged = { alwaysShowRoutineProgress = it; persistSettings() },
                                                   onHideCompletedRoutinesChanged = { hideCompletedRoutines = it; persistSettings() },
                                                   onConfirmDestructiveChanged = { confirmDestructiveActions = it; persistSettings() },
                                                   onDailyResetHourChanged = { dailyResetHour = it.coerceIn(0, 23); persistSettings() },
                                                   onDailyRoutineTargetChanged = { dailyRoutineTarget = it.coerceIn(3, 10); persistSettings(); regenerateForDay(currentEpochDay()) },
                                                   onExpandedSectionChanged = { settingsExpandedSection = it },
                                                    onPremiumUnlockedChanged = { premiumUnlocked = it; persistSettings() },
                                                    onCloudSyncEnabledChanged = { cloudSyncEnabled = it; persistSettings() },
                                                    onCloudEmailChanged = { cloudAccountEmail = it.take(60); persistSettings() },
                                                    onSaveSalaryProfile = { profile ->
                                                        salaryProfile = profile
                                                        scope.launch { saveSalaryProfile(appContext, profile) }
                                                    },
                                                    onDailyRemindersEnabledChanged = { dailyRemindersEnabled = it; persistSettings() },
                                                    onHapticsChanged = { hapticsEnabled = it; persistSettings() },
                                                    onSoundEffectsChanged = { soundEffectsEnabled = it; persistSettings() },
                                                    onFinanceDefaultItemTypeChanged = { next ->
                                                        financeDefaultItemType = next
                                                        scope.launch { appContext.dataStore.edit { p -> p[Keys.FINANCE_DEFAULT_ITEM_TYPE] = next.name } }
                                                    },
                                                    onFinanceWarnThresholdPercentChanged = { next ->
                                                        financeWarnThresholdPercent = next.coerceIn(5, 80)
                                                        scope.launch { appContext.dataStore.edit { p -> p[Keys.FINANCE_WARN_THRESHOLD_PERCENT] = financeWarnThresholdPercent } }
                                                    },
                                                    onFinanceShowHistoryHintsChanged = { next ->
                                                        financeShowHistoryHints = next
                                                        scope.launch { appContext.dataStore.edit { p -> p[Keys.FINANCE_SHOW_HISTORY_HINTS] = next } }
                                                    },
                                                    onFontStyleChanged = { fontStyle = it; persistSettings() },
                                                   onFontScalePercentChanged = { fontScalePercent = it.coerceIn(80, 125); persistSettings() },
                                                    onAppLanguageChanged = { lang ->
                                                        appLanguage = lang
                                                        appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                                                            .edit().putString("selected_language", lang).apply()
                                                        scope.launch {
                                                            appContext.dataStore.edit { p -> p[Keys.APP_LANGUAGE] = lang }
                                                            (appContext as android.app.Activity).recreate()
                                                        }
                                                    },
                                                   onJournalNameChanged = { journalName = it.take(24).ifBlank { "Journal" }; persistSettings() },
                                                   onTextColorChanged = { textColorOverride = it; persistSettings() },
                                                   backgroundImageUri = backgroundImageUri,
                                                   backgroundVideoUri = backgroundVideoUri,
                                                   backgroundType = backgroundType,
                                                   onBackgroundImageChanged = {
                                                       backgroundImageUri = it
                                                       persistSettings()
                                                   },
                                                   onBackgroundVideoUriChanged = {
                                                       backgroundVideoUri = it
                                                       persistSettings()
                                                   },
                                                   onBackgroundTypeChanged = {
                                                       backgroundType = it
                                                       persistSettings()
                                                   },
                                                   backgroundVideoMuted = backgroundVideoMuted,
                                                   onBackgroundVideoMutedChanged = {
                                                       backgroundVideoMuted = it
                                                       persistSettings()
                                                   },
                                                   onBackgroundImageTransparencyPercentChanged = onBackgroundImageTransparencyPercentChangedRef,
                                                   onAccentTransparencyChanged = onAccentTransparencyChangedRef,
                                                   onTextTransparencyChanged = onTextTransparencyChangedRef,
                                                   onAppBgTransparencyChanged = onAppBgTransparencyChangedRef,
                                                   onChromeBgTransparencyChanged = onChromeBgTransparencyChangedRef,
                                                   onCardBgTransparencyChanged = onCardBgTransparencyChangedRef,
                                                   onJournalPageTransparencyChanged = onJournalPageTransparencyChangedRef,
                                                   onJournalAccentTransparencyChanged = onJournalAccentTransparencyChangedRef,
                                                   onButtonTransparencyChanged = onButtonTransparencyChangedRef,
                                                   onAppBackgroundColorChanged = { appBackgroundColorOverride = it; persistSettings() },
                                                   onChromeBackgroundColorChanged = { chromeBackgroundColorOverride = it; persistSettings() },
                                                   onCardColorChanged = { cardColorOverride = it; persistSettings() },
                                                   onButtonColorChanged = { buttonColorOverride = it; persistSettings() },
                                                   onJournalPageColorChanged = { journalPageColorOverride = it; persistSettings() },
                                                   onJournalAccentColorChanged = { journalAccentColorOverride = it; persistSettings() },
                                                   onThemeChanged = {
                                                       appTheme = normalizeTheme(it)
                                                       if (buttonColorOverride == null) {
                                                           accent = fallbackAccentForTheme(appTheme)
                                                       }
                                                       persistSettings()
                                                   },
                                                   onAccentChanged = {
                                                       accent = it
                                                       buttonColorOverride = null
                                                       persistSettings()
                                                   },
                                                   onExportBackup = {
                                                       val blob = exportBackupPayload()
                                                       if (blob.isBlank()) {
                                                            scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_backup_econsistencyort_failed)) }
                                                       } else {
                                                           val sendIntent = Intent().apply {
                                                               action = Intent.ACTION_SEND
                                                               putExtra(Intent.EXTRA_TEXT, blob)
                                                               type = "text/plain"
                                                           }
                                                           appContext.startActivity(Intent.createChooser(sendIntent, getString(R.string.settings_econsistencyort_backup)))
                                                       }
                                                   },
                                                   onImportBackup = {
                                                       showBackupImport = true
                                                   },
                                                   onCloudSyncNow = { triggerCloudSnapshotSync(force = true) },
                                                   onCloudRestore = { restoreFromCloud() },
                                                   onCloudConnectRequest = {
                                                       performGoogleLogin()
                                                   },
                                                   onCloudDisconnect = { performLogout() },
                                                   onSendFeedback = { category, text -> shareFeedbackReport(category, text) },
                                                   onExportLogs = {
                                                       val sendIntent = Intent().apply {
                                                           action = Intent.ACTION_SEND
                                                           putExtra(Intent.EXTRA_TEXT, AppLog.exportRecentLogs().ifBlank { "No logs captured." })
                                                           type = "text/plain"
                                                       }
                                                       appContext.startActivity(Intent.createChooser(sendIntent, getString(R.string.settings_econsistencyort_logs)))
                                                   },
                                                   onBuildAdvancedTemplateStarterJson = { buildAdvancedTemplateStarterJson() },
                                                   onBuildAdvancedTemplatePromptFromRequest = { Request, allowThemeChanges ->
                                                       buildAdvancedTemplatePromptFromRequest(Request, allowThemeChanges)
                                                   },
                                                   onImportAdvancedTemplateJson = { json ->
                                                       val result = importAdvancedTemplateJson(json)
                                                        if (result.success) {
                                                            scope.launch {
                                                                snackbarHostState.showSnackbar(appContext.getString(R.string.template_imported_counts, result.dailyAdded, result.mainAdded))
                                                            }
                                                        }
                                                       result
                                                   },
                                                   onApplyAdvancedTemplateByPackage = { pkg ->
                                                       val ok = applyAdvancedImportedTemplate(pkg)
                                                       if (ok) {
                                                            scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_advanced_template_applied)) }
                                                       }
                                                       ok
                                                   },
                                                   onRequestResetAll = {
                                                       if (confirmDestructiveActions) {
                                                           resetBackupBefore = false
                                                           resetBackupName = "Pre-reset backup"
                                                           showResetAll = true
                                                       } else {
                                                           resetAll(saveBackup = false)
                                                       }
                                                   },
                                                   onRequestForceNewDay = {
                                                       if (confirmDestructiveActions) {
                                                           showRefreshDayConfirm = true
                                                       } else {
                                                           onRefreshDay()
                                                       }
                                                   },
                                                   onOpenDrawer = { scope.launch { drawerState.open() } }
                                               )
                }
                val renderScreen: @Composable (Screen) -> Unit = { target ->
                    val appCtx = androidx.compose.ui.platform.LocalContext.current
                    val imageLoader = remember {
                        ImageLoader.Builder(appCtx)
                            .components {
                                if (Build.VERSION.SDK_INT >= 28) {
                                    add(ImageDecoderDecoder.Factory())
                                } else {
                                    add(GifDecoder.Factory())
                                }
                            }
                            .build()
                    }
                    val exoPlayer = remember {
                        ExoPlayer.Builder(appCtx).build().apply {
                            repeatMode = Player.REPEAT_MODE_ALL
                        }
                    }
                    LaunchedEffect(backgroundVideoMuted) {
                        exoPlayer.volume = if (backgroundVideoMuted) 0f else 1f
                    }
                    val lifecycleOwner = LocalLifecycleOwner.current
                    LaunchedEffect(backgroundVideoUri, backgroundType) {
                       if (backgroundType == "video" && !backgroundVideoUri.isNullOrBlank()) {
                           exoPlayer.setMediaItem(MediaItem.fromUri(backgroundVideoUri!!))
                           exoPlayer.prepare()
                           if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                               exoPlayer.playWhenReady = true
                           }
                       } else {
                           exoPlayer.stop()
                       }
                    }
                    DisposableEffect(lifecycleOwner, backgroundVideoUri, backgroundType) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (backgroundType == "video" && !backgroundVideoUri.isNullOrBlank()) {
                                if (event == Lifecycle.Event.ON_RESUME) {
                                    exoPlayer.play()
                                } else if (event == Lifecycle.Event.ON_PAUSE) {
                                    exoPlayer.pause()
                                }
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }
                    DisposableEffect(Unit) {
                       onDispose {
                           exoPlayer.release()
                       }
                    }

                    CompositionLocalProvider(LocalHeaderThemeToggle provides {
                        settingsExpandedSection = "appearance"
                        screen = Screen.SETTINGS
                        persistSettings()
                    }) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (backgroundType == "video" && !backgroundVideoUri.isNullOrBlank()) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = exoPlayer
                                        useController = false
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if ((backgroundType == "image" || backgroundType == "gif") && !backgroundImageUri.isNullOrBlank()) {
                            AsyncImage(
                                model = backgroundImageUri,
                                imageLoader = imageLoader,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        if (backgroundType != "color" && (
                            (backgroundType == "video" && !backgroundVideoUri.isNullOrBlank()) || 
                            ((backgroundType == "image" || backgroundType == "gif") && !backgroundImageUri.isNullOrBlank())
                        )) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (isThemeBgLight) {
                                            Color.White.copy(alpha = 0.70f * (1f - (backgroundImageTransparencyPercent.coerceIn(0, 100) / 100f)))
                                        } else {
                                            Color.Black.copy(alpha = 0.45f * (1f - (backgroundImageTransparencyPercent.coerceIn(0, 100) / 100f)))
                                        }
                                    )
                            )
                        }
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = if (backgroundType == "color" || (backgroundType == "video" && backgroundVideoUri.isNullOrBlank()) || ((backgroundType == "image" || backgroundType == "gif") && backgroundImageUri.isNullOrBlank())) {
                                themeBg.copy(alpha = appBgAlpha)
                            } else {
                                themeBg.copy(alpha = appBgAlpha * (1f - (backgroundImageTransparencyPercent.coerceIn(0, 100) / 100f)))
                            }
                        ) {
                        when (target) {
                            // Home hub
                            Screen.HOME -> HomeScreen(
                                Modifier.fillMaxSize().padding(padding), appContext, routines.filter { !hideCompletedRoutines || !it.completed }, avatar, accentStrong, accentSoft, refreshCount, displayedBalance,
                                alwaysShowRoutineProgress,
                                customMode,
                                { onRefreshTodayRoutines() },
                                homeRefreshInProgress,
                                { quest ->
                                    val list = customTemplates.toMutableList()
                                    val idx = list.indexOfFirst { it.id == quest.id }
                                    if (idx >= 0) list[idx] = quest else list.add(quest)
                                    persistCustomTemplates(list)
                                    scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_daily_routine_saved)) }
                                },
                                {
                                    scope.launch { snackbarHostState.showSnackbar(appContext.getString(R.string.enable_custom_mode_add_routines)) }
                                },
                                { onCompleteRoutineWithUndo(it) },
                                { id, prog -> onUpdateRoutineProgressWithUndo(id, prog) },
                                { id, prog -> onTimerTickProgress(id, prog) },
                                { id, prog -> onTimerComplete(id, prog) },
                                { flushTimerPersist() },
                                { snapshot, startedRoutineId ->
                                    persistHealthDailySnapshot(snapshot)
                                    syncHealthObjectiveRoutineProgress(snapshot, startedRoutineId)
                                },
                                { id -> onResetRoutineProgressWithUndo(id) },
                                { id -> onRemoveRoutineFromToday(id) },
                                { id -> onOpenRoutineEditorFromHome(id) },
                                playerName,
                                { name ->
                                    val fixed = name.trim().ifBlank { playerName }
                                    playerName = fixed
                                    scope.launch { appContext.dataStore.edit { p -> p[Keys.PLAYER_NAME] = fixed } }
                                },
                                { persistAvatar(it) },
                                {
                                    SoundManager.playClick()
                                    if (it == Screen.ROUTINES) RoutinesPreferredTab = 0
                                    screen = it
                                },
                                { scope.launch { drawerState.open() } },
                                { showFocusTimer = true },
                                {
                                    SoundManager.playClick()
                                    RoutinesPreferredTab = 0
                                    screen = Screen.ROUTINES
                                },
                                {
                                    SoundManager.playClick()
                                    RoutinesPreferredTab = 1
                                    screen = Screen.ROUTINES
                                }
                            )



                            Screen.MILESTONE -> MilestonesScreen(
                                modifier = Modifier.fillMaxSize().padding(padding),
                                routines = milestones.filter { it.isActive && (!hideCompletedRoutines || it.currentStep < it.steps.size) },
                                allMilestones = milestones,
                                accentStrong = accentStrong,
                                accentSoft = accentSoft,
                                customMode = customMode,
                                onAddMain = { milestone ->
                                    val list = milestones.toMutableList()
                                    val idx = list.indexOfFirst { it.id == milestone.id }
                                    if (idx >= 0) list[idx] = milestone else list.add(milestone)
                                    persistMilestones(list)
                                    scope.launch { snackbarHostState.showSnackbar(getString(R.string.snackbar_main_routine_saved)) }
                                },
                                onRequireCustomMode = {
                                    scope.launch { snackbarHostState.showSnackbar(appContext.getString(R.string.enable_custom_mode_add_routines)) }
                                },
                                onUpdate = { onUpdateMilestone(it) },
                                onResetProgress = { onResetMilestoneWithUndo(it) },
                                onDelete = { onDeleteMilestone(it) },
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onOpenSettings = { screen = Screen.SETTINGS },
                                onOpenMilestones = {
                                    RoutinesPreferredTab = 1
                                    screen = Screen.ROUTINES
                                }

                            )

                            Screen.GRIMOIRE -> JournalScreen(
                                modifier = Modifier.fillMaxSize().padding(padding),
                                pages = journalPages,
                                accentSoft = accentSoft,
                                journalPageColorOverride = journalPageColorOverride,
                                journalAccentColorOverride = journalAccentColorOverride,
                                journalName = journalName,
                                onJournalNameChanged = {
                                    journalName = it.take(24).ifBlank { "Journal" }
                                    persistSettings()
                                },
                                onUpdatePages = {
                                    journalPages = it
                                    scope.launch { persistJournal(appContext, it) }
                                },
                                onBack = { SoundManager.playClick(); screen = Screen.HOME },
                                pageIndexExternal = grimoirePageIndex,
                                onPageIndexExternalChange = { grimoirePageIndex = it },
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onOpenSettings = { screen = Screen.SETTINGS },
                                onBookOpenStateChanged = { isOpen -> journalBookOpen = isOpen }
                            )
                            Screen.INVENTORY -> InventoryScreen(
                                modifier = Modifier.fillMaxSize().padding(padding),
                                inventory = inventory,
                                catalogItems = catalogItems,
                                customMode = customMode,
                                balance = displayedBalance,
                                salaryProfile = salaryProfile,
                                currencyCode = salaryProfile?.currencyCode ?: "USD",
                                remainingBudget = remainingBudgetExact,
                                purchaseTransactions = purchaseTransactions,
                                estimateRemainingAfterPurchase = { item ->
                                    remainingBudgetExact?.minus(item.cost.toDouble())
                                },
                                accentStrong = accentStrong,
                                accentSoft = accentSoft,
                                showTutorial = !shopTutorialSeen,
                                onTutorialDismiss = { markShopTutorialSeen() },
                                showHoldHint = customMode && !shopHoldHintSeen,
                                onHoldHintShown = { markShopHoldHintSeen() },
                                onBuyShopItem = { onBuyShopItem(it) },
                                onUpsertShopItem = { onUpsertShopItem(it) },
                                onDeleteShopItem = { onDeleteShopItem(it) },
                                financeDefaultItemType = financeDefaultItemType,
                                financeWarnThresholdPercent = financeWarnThresholdPercent,
                                financeShowHistoryHints = financeShowHistoryHints,
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onOpenSettings = { screen = Screen.SETTINGS },
                                onOpenFinanceSettings = {
                                    settingsExpandedSection = "gameplay"
                                    financeFocusRequestNonce += 1
                                    screen = Screen.SETTINGS
                                }
                            )
                            Screen.CALENDAR -> CalendarScreen(
                                modifier = Modifier.fillMaxSize().padding(padding),
                                appContext = appContext,
                                plans = calendarPlans,
                                accentStrong = accentStrong,
                                accentSoft = accentSoft,
                                showTutorial = !calendarTutorialSeen,
                                onTutorialDismiss = { markCalendarTutorialSeen() },
                                onAddPlan = { day, text -> onAddPlan(day, text) },
                                onDeletePlan = { day, idx -> onDeletePlan(day, idx) },
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onOpenSettings = { screen = Screen.SETTINGS }
                            )
                            Screen.ROUTINES -> RoutinesScreenContent()
                            Screen.STATS -> DashboardScreen(
                                modifier = Modifier.fillMaxSize().padding(padding),
                                balance = displayedBalance,
                                salaryProfile = salaryProfile,
                                purchaseTransactions = purchaseTransactions,
                                remainingBudget = remainingBudgetExact,
                                history = historyMap,
                                healthSnapshot = healthDailySnapshot,
                                onSaveHealthSnapshot = {
                                    persistHealthDailySnapshot(it)
                                    syncHealthObjectiveRoutineProgress(it)
                                },
                                accentStrong = accentStrong,
                                accentSoft = accentSoft,
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onOpenSettings = { screen = Screen.SETTINGS }
                            )
                            Screen.SETTINGS -> settingsScreenContent()
                            Screen.ABOUT -> AboutScreen(
                                Modifier.fillMaxSize().padding(padding),
                                accentStrong,
                                accentSoft
                            ) { scope.launch { drawerState.open() } }
                        }
                        }
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    SwipePagerHost(
                        modifier = Modifier.fillMaxSize(),
                        enabled = screen == Screen.HOME || screen == Screen.MILESTONE || screen == Screen.GRIMOIRE,
                        drawerClosed = drawerClosed,
                        current = screen,
                        onRequestScreen = { screen = it },
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSwipeProgress = { swipeVisualProgress = it },
                        content = renderScreen,
                        grimoireBookOpen = journalBookOpen
                    )
                }
            }
        }
    }
    if (showIntroSplash) {
        IntroSplash(backgroundImageUri = backgroundImageUri)
    }
    if (showWelcomeSetup) {
        WelcomeSetupScreen(
            defaultSkipIntro = onboardingSkipIntroDefault,
            onLanguageChanged = { lang ->
                appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .edit().putString("selected_language", lang).apply()
                scope.launch {
                    appContext.dataStore.edit { p -> p[Keys.APP_LANGUAGE] = lang }
                    (appContext as android.app.Activity).recreate()
                }
            },
            onDone = { setup ->
                val finalName = setup.name.trim().take(24).ifBlank { "Player" }
                val chosenAvatar = setup.avatarImageUri
                    ?.takeIf { it.isNotBlank() }
                    ?.let { Avatar.Custom(it.toUri()) }
                    ?: Avatar.Preset(setup.avatar)
                persistAvatar(chosenAvatar)
                playerName = finalName
                scope.launch { appContext.dataStore.edit { p -> p[Keys.PLAYER_NAME] = finalName } }
                onboardingGoal = setup.goal
                dailyResetHour = setup.reminderHour.coerceIn(0, 23)
                val starter = getJourneyBalanceTemplate()
                applyStarterTemplate(starter)
                appTheme = normalizeTheme(setup.theme)
                accent = setup.accentArgb?.let { Color(it.toInt()) } ?: fallbackAccentForTheme(appTheme)
                persistSettings()
                scope.launch { appContext.dataStore.edit { p -> p[Keys.ONBOARDING_DONE] = true } }
                onboardingSkipIntroDefault = false
                screen = Screen.HOME
                showWelcomeSetup = false
            }
        )
    }
}

@Composable
private fun SwipePagerHost(
    modifier: Modifier,
    enabled: Boolean,
    drawerClosed: Boolean,
    current: Screen,
    onRequestScreen: (Screen) -> Unit,
    onOpenDrawer: () -> Unit,
    onSwipeProgress: (Float) -> Unit,
    content: @Composable (Screen) -> Unit,
    grimoireBookOpen: Boolean
) {
    val scope = rememberCoroutineScope(); val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val threshold = widthPx * 0.28f
        var dragOffset by remember { mutableFloatStateOf(0f) }
        var settleJob by remember { mutableStateOf<Job?>(null) }
        val touchSlop = 24f
        LaunchedEffect(current) { settleJob?.cancel(); settleJob = null; dragOffset = 0f }

        val leftTarget: Screen? = when (current) {
            Screen.HOME -> Screen.MILESTONE
            Screen.MILESTONE -> Screen.GRIMOIRE
            else -> null
        }
        val rightTargetScreen: Screen? = when (current) {
            Screen.MILESTONE -> Screen.HOME
            Screen.GRIMOIRE -> Screen.MILESTONE
            else -> null
        }
        val rightOpensDrawer = current == Screen.HOME
        // In RTL, swiping right = next (higher index), left = prev (lower index) — opposite of LTR
        val effectiveRightTarget: Screen? = if (isRtl) leftTarget else rightTargetScreen
        val effectiveLeftTarget: Screen? = if (isRtl) rightTargetScreen else leftTarget
        fun screenSwipeIndex(target: Screen): Float = when (target) {
            Screen.HOME -> 0f
            Screen.MILESTONE -> 1f
            Screen.GRIMOIRE -> 2f
            else -> 0f
        }
        val visualProgress = run {
            val base = screenSwipeIndex(current)
            val hasDrag = (dragOffset > 0f && effectiveRightTarget != null) || (dragOffset < 0f && effectiveLeftTarget != null)
            if (!hasDrag) base
            else {
                val sign = if (isRtl) 1f else -1f
                (base + sign * dragOffset / widthPx).coerceIn(0f, 2f)
            }
        }
        SideEffect { onSwipeProgress(visualProgress) }

        fun clampOffset(v: Float): Float {
            val canSwipeGrimoire = current != Screen.GRIMOIRE || !grimoireBookOpen
            val maxRight = if (effectiveRightTarget != null && canSwipeGrimoire) widthPx else 0f
            val maxLeft = if (effectiveLeftTarget != null && canSwipeGrimoire) -widthPx else 0f
            return v.coerceIn(maxLeft, maxRight)
        }

        Box(modifier = Modifier.fillMaxSize().pointerInput(enabled, drawerClosed, current, rightTargetScreen, leftTarget, rightOpensDrawer, grimoireBookOpen, isRtl) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pointerId = down.id
                if (!drawerClosed) return@awaitEachGesture
                settleJob?.cancel(); settleJob = null
                var totalDx = 0f
                var totalDy = 0f
                var locked = false
                var openDrawerFromEdge = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (!change.pressed) break
                    val delta = change.positionChangeIgnoreConsumed()
                    val dx = delta.x
                    val dy = delta.y
                    totalDx += dx
                    totalDy += dy
                    if (!locked && abs(totalDx) > touchSlop) {
                        val absDx = abs(totalDx)
                        val absDy = abs(totalDy)
                        if (absDx > absDy * 2.0f) {
                            locked = true
                        }
                    }
                    if (!locked) continue
                    if (rightTargetScreen == null && rightOpensDrawer && (if (isRtl) totalDx < 0f else totalDx > 0f)) {
                        openDrawerFromEdge = true
                        break
                    }
                    dragOffset = clampOffset(dragOffset + dx)
                    change.consume()
                }
                if (openDrawerFromEdge) {
                    onOpenDrawer()
                    dragOffset = 0f
                    return@awaitEachGesture
                }
                val v = dragOffset
                if (v == 0f && totalDx <= threshold) return@awaitEachGesture
                val canSwipeGrimoire = current != Screen.GRIMOIRE || !grimoireBookOpen
                val goRight = v > threshold && effectiveRightTarget != null && canSwipeGrimoire
                val goLeft = v < -threshold && effectiveLeftTarget != null && canSwipeGrimoire

                val target = when {
                    goRight -> widthPx
                    goLeft -> -widthPx
                    else -> 0f
                }
                settleJob = scope.launch {
                    animate(
                        initialValue = dragOffset,
                        targetValue = target,
                        animationSpec = tween(durationMillis = if (target == 0f) 230 else 320)
                    ) { value, _ -> dragOffset = value }
                    when {
                        goRight -> onRequestScreen(effectiveRightTarget!!)
                        goLeft -> onRequestScreen(effectiveLeftTarget!!)
                    }
                    dragOffset = 0f
                }
            }
        }) {
            val off = dragOffset
            if (off > 0f) {
                effectiveRightTarget?.let { screen ->
                    Box(Modifier.fillMaxSize().graphicsLayer { translationX = off - widthPx }) { content(screen) }
                }
            } else if (off < 0f) {
                effectiveLeftTarget?.let { screen ->
                    Box(Modifier.fillMaxSize().graphicsLayer { translationX = off + widthPx }) { content(screen) }
                }
            }
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = off }) { content(current) }
        }
    }
}





























