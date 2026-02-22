package com.example.livinglifemmo

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/* ===================== LOGIC ===================== */

val UTC: TimeZone = TimeZone.getTimeZone("UTC")
const val REAL_DAILY_LIFE_PACKAGE_ID: String = "real_daily_life_v1"
const val REAL_WORLD_MOMENTUM_PACKAGE_ID: String = "real_world_momentum_v1"

fun epochDayNow(): Long {
    val cal = Calendar.getInstance()
    val y = cal.get(Calendar.YEAR)
    val m = cal.get(Calendar.MONTH) + 1
    val d = cal.get(Calendar.DAY_OF_MONTH)
    return epochDayFromYmd(y, m, d)
}

fun epochDayNowAtHour(resetHour: Int): Long {
    val cal = Calendar.getInstance()
    cal.add(Calendar.HOUR_OF_DAY, -resetHour.coerceIn(0, 23))
    val y = cal.get(Calendar.YEAR)
    val m = cal.get(Calendar.MONTH) + 1
    val d = cal.get(Calendar.DAY_OF_MONTH)
    return epochDayFromYmd(y, m, d)
}

fun epochDayFromYmd(year: Int, month1to12: Int, day: Int): Long {
    val cal = GregorianCalendar(UTC)
    cal.clear()
    cal.set(year, month1to12 - 1, day, 0, 0, 0)
    return TimeUnit.MILLISECONDS.toDays(cal.timeInMillis)
}

fun ymdFromEpoch(epochDay: Long): Triple<Int, Int, Int> {
    val cal = GregorianCalendar(UTC)
    cal.timeInMillis = TimeUnit.DAYS.toMillis(epochDay)
    return Triple(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
}

fun todayYmd(): Pair<Int, Int> {
    val cal = Calendar.getInstance()
    return cal.get(Calendar.YEAR) to (cal.get(Calendar.MONTH) + 1)
}

fun dayOfMonthFromEpoch(epochDay: Long): Int = ymdFromEpoch(epochDay).third

fun monthTitle(year: Int, month: Int): String {
    val cal = GregorianCalendar(UTC)
    cal.clear()
    cal.set(year, month - 1, 1, 0, 0, 0)
    val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    fmt.timeZone = UTC
    return fmt.format(cal.time)
}

fun prevMonth(year: Int, month: Int): Pair<Int, Int> {
    val cal = GregorianCalendar(UTC)
    cal.clear()
    cal.set(year, month - 1, 1, 0, 0, 0)
    cal.add(Calendar.MONTH, -1)
    return cal.get(Calendar.YEAR) to (cal.get(Calendar.MONTH) + 1)
}

fun nextMonth(year: Int, month: Int): Pair<Int, Int> {
    val cal = GregorianCalendar(UTC)
    cal.clear()
    cal.set(year, month - 1, 1, 0, 0, 0)
    cal.add(Calendar.MONTH, 1)
    return cal.get(Calendar.YEAR) to (cal.get(Calendar.MONTH) + 1)
}

fun buildMonthGrid(year: Int, month: Int): List<Long?> {
    val cal = GregorianCalendar(UTC)
    cal.clear()
    cal.set(year, month - 1, 1, 0, 0, 0)
    val dow = cal.get(Calendar.DAY_OF_WEEK)
    val offset = dow - Calendar.SUNDAY
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val out = MutableList<Long?>(42) { null }
    var idx = offset
    for (d in 1..daysInMonth) { out[idx] = epochDayFromYmd(year, month, d); idx++ }
    return out
}

fun formatEpochDayFull(epochDay: Long): String {
    val (y, m, d) = ymdFromEpoch(epochDay)
    val cal = GregorianCalendar(UTC)
    cal.clear()
    cal.set(y, m - 1, d, 0, 0, 0)
    val fmt = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
    fmt.timeZone = UTC
    return fmt.format(cal.time)
}

fun formatEpochDay(epochDay: Long, lang: String = "en"): String {
    val today = epochDayNow()
    val isAr = lang == "ar"
    return when (epochDay) {
        today -> if (isAr) "اليوم" else "Today"
        today - 1L -> if (isAr) "أمس" else "Yesterday"
        else -> {
            val (y, m, d) = ymdFromEpoch(epochDay)
            val cal = GregorianCalendar(UTC)
            cal.clear()
            cal.set(y, m - 1, d, 0, 0, 0)
            val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
            fmt.timeZone = UTC
            fmt.format(cal.time)
        }
    }
}

fun customTemplatesToQuestTemplates(customs: List<CustomTemplate>): List<QuestTemplate> {
    return customs.map {
        QuestTemplate(it.category, it.difficulty, it.title, it.icon, it.target, it.isPinned, it.imageUri, it.packageId)
    }
}

val categoryOrder = listOf(QuestCategory.FITNESS, QuestCategory.STUDY, QuestCategory.HYDRATION, QuestCategory.DISCIPLINE, QuestCategory.MIND)

fun stableQuestId(cat: QuestCategory, t: QuestTemplate): Int {
    val key = "${cat.name}|${t.title}|${t.icon}|${t.difficulty}"
    val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
    return java.nio.ByteBuffer.wrap(bytes, 0, 4).int and 0x7fffffff
}

fun generateDailyQuests(seed: Long, pool: List<QuestTemplate>, desiredCount: Int = 5): List<Quest> {
    if (pool.isEmpty()) return emptyList()

    val pinned = pool.filter { it.isPinned }
    val others = pool.filter { !it.isPinned }
    val countNeeded = (desiredCount.coerceIn(3, 10) - pinned.size).coerceAtLeast(0)

    val random = java.util.Random(seed)
    val randomSelection = others.shuffled(random).take(countNeeded)

    return (pinned + randomSelection).map { t ->
        Quest(
            id = stableQuestId(t.category, t),
            title = t.title,
            icon = t.icon,
            difficulty = t.difficulty,
            category = t.category,
            target = t.target,
            imageUri = t.imageUri
        )
    }
}
fun refreshKeepingCompleted(
    current: List<Quest>,
    seed: Long,
    pool: List<QuestTemplate>,
    desiredCount: Int = 5
): List<Quest> {
    val rng = Random(seed)
    val templates = pool

    val pinnedTemplates = templates.filter { it.isPinned }

    val currentPinnedIds = current.filter { q ->
        pinnedTemplates.any { it.title == q.title }
    }

    val currentTitles = current.map { it.title }.toSet()

    val replacements = categoryOrder.mapNotNull { cat ->
        val existingPinned = currentPinnedIds.firstOrNull { it.category == cat }
        if (existingPinned != null) return@mapNotNull existingPinned

        val existingCompleted = current.firstOrNull { it.category == cat && it.completed && !currentPinnedIds.contains(it) }
        if (existingCompleted != null) return@mapNotNull existingCompleted

        val catPool = templates.filter { it.category == cat && !it.isPinned }
        if (catPool.isEmpty()) return@mapNotNull null

        val candidates = catPool.filter { !currentTitles.contains(it.title) }
        val finalPool = if (candidates.isNotEmpty()) candidates else catPool

        val t = finalPool[rng.nextInt(finalPool.size)]
        Quest(stableQuestId(cat, t), t.title, t.icon, t.category, t.difficulty, t.target, 0, false, t.imageUri)
    }

    val targetCount = desiredCount.coerceIn(3, 10)
    val seeded = (currentPinnedIds + replacements).distinctBy { it.id }.toMutableList()
    if (seeded.size >= targetCount) return seeded.take(targetCount)

    val usedIds = seeded.map { it.id }.toMutableSet()
    val extraTemplates = templates.shuffled(rng)
    for (template in extraTemplates) {
        val quest = Quest(
            id = stableQuestId(template.category, template),
            title = template.title,
            icon = template.icon,
            category = template.category,
            difficulty = template.difficulty,
            target = template.target,
            currentProgress = 0,
            completed = false,
            imageUri = template.imageUri
        )
        if (usedIds.add(quest.id)) {
            seeded += quest
            if (seeded.size >= targetCount) break
        }
    }
    return seeded.take(targetCount)
}

fun getInitialDefaultPool(lang: String = "en"): List<CustomTemplate> {
    val pool = mutableListOf<CustomTemplate>()
    val pkg = "default_pack"
    val isAr = lang == "ar"

    fun add(title: String, icon: String, cat: QuestCategory, diff: Int = 2, target: Int = 2) {
        pool.add(
            CustomTemplate(
                id = java.util.UUID.randomUUID().toString(),
                category = cat,
                difficulty = diff,
                title = title,
                icon = icon,
                target = target,
                isPinned = false,
                imageUri = null,
                packageId = pkg,
                isActive = true
            )
        )
    }

    if (isAr) {
        add("مشى 2,000 خطوة", "🚶", QuestCategory.FITNESS, 1)
        add("القيام بـ 15 تمرين ضغط", "💪", QuestCategory.FITNESS, 1)
        add("جري 1 كم", "🏃", QuestCategory.FITNESS, 3)
        add("10 قفزات (Jumping Jacks)", "✨", QuestCategory.FITNESS, 1)

        add("قراءة 5 صفحات", "📖", QuestCategory.STUDY, 1, 5)
        add("دراسة لمدة 15 دقيقة", "📚", QuestCategory.STUDY, 1)
        add("مراجعة 10 بطاقات تعليمية", "🃏", QuestCategory.STUDY, 1)
        add("عمل عميق لمدة ساعة", "🧠", QuestCategory.STUDY, 5)

        add("ترتيب سريرك", "🛏️", QuestCategory.DISCIPLINE, 1)
        add("تنظيف لمدة 5 دقائق", "🧹", QuestCategory.DISCIPLINE, 1)
        add("لا طعام غير صحي", "🥗", QuestCategory.DISCIPLINE, 3)
        add("دش بارد", "🧊", QuestCategory.DISCIPLINE, 3)

        add("تأمل لمدة 3 دقائق", "🧘", QuestCategory.MIND, 1)
        add("سجل الامتنان", "🙏", QuestCategory.MIND, 1)
        add("بدون هاتف لمدة 30 دقيقة", "📵", QuestCategory.MIND, 2)

        add("شرب الماء", "🥤", QuestCategory.HYDRATION, 1, 2)
        add("ترطيب الجسم (8 أكواب)", "💧", QuestCategory.HYDRATION, 3, 8)
        add("إنهاء 2.0 لتر", "🌊", QuestCategory.HYDRATION, 4, 4)
    } else {
        add("Walk 2,000 steps", "🚶", QuestCategory.FITNESS, 1)
        add("Do 15 push-ups", "💪", QuestCategory.FITNESS, 1)
        add("Run 1km", "🏃", QuestCategory.FITNESS, 3)
        add("10 Jumping Jacks", "✨", QuestCategory.FITNESS, 1)

        add("Read 5 pages", "📖", QuestCategory.STUDY, 1, 5)
        add("Study 15 mins", "📚", QuestCategory.STUDY, 1)
        add("Review 10 Flashcards", "🃏", QuestCategory.STUDY, 1)
        add("Deep work 1 hour", "🧠", QuestCategory.STUDY, 5)

        add("Make your bed", "🛏️", QuestCategory.DISCIPLINE, 1)
        add("Clean 5 mins", "🧹", QuestCategory.DISCIPLINE, 1)
        add("No junk food", "🥗", QuestCategory.DISCIPLINE, 3)
        add("Cold shower", "🧊", QuestCategory.DISCIPLINE, 3)

        add("Meditate 3 mins", "🧘", QuestCategory.MIND, 1)
        add("Gratitude Journal", "🙏", QuestCategory.MIND, 1)
        add("No phone 30 mins", "📵", QuestCategory.MIND, 2)

        add("Drink Water", "🥤", QuestCategory.HYDRATION, 1, 2)
        add("Hydrate (8 Cups)", "💧", QuestCategory.HYDRATION, 3, 8)
        add("Finish 2.0L", "🌊", QuestCategory.HYDRATION, 4, 4)
    }

    return pool
}
fun getInitialMainQuests(lang: String = "en"): List<CustomMainQuest> {
    val pkg = "default_pack"
    val isAr = lang == "ar"
    return if (isAr) {
        listOf(
            CustomMainQuest(java.util.UUID.randomUUID().toString(), "نزهة صباحية", "تسلق التلة المحلية.", listOf("حزم المعدات", "الوصول للقاعدة", "القمة"), packageId = pkg),
            CustomMainQuest(java.util.UUID.randomUUID().toString(), "تنظيف الشقة", "تنظيف عميق لمساحة المعيشة.", listOf("إزالة الفوضى", "الكنس", "المسح"), packageId = pkg),
            CustomMainQuest(java.util.UUID.randomUUID().toString(), "إنهاء كتاب", "قراءة الفصول الأخيرة.", listOf("قراءة الفصل 10", "قراءة الفصل 11", "الإنهاء"), packageId = pkg)
        )
    } else {
        listOf(
            CustomMainQuest(java.util.UUID.randomUUID().toString(), "Morning Hike", "Climb the local hill.", listOf("Pack Gear", "Reach Base", "Summit"), packageId = pkg),
            CustomMainQuest(java.util.UUID.randomUUID().toString(), "Clean Apartment", "Deep clean the living space.", listOf("Declutter", "Vacuum", "Mop"), packageId = pkg),
            CustomMainQuest(java.util.UUID.randomUUID().toString(), "Finish Book", "Read final chapters.", listOf("Read Ch 10", "Read Ch 11", "Finish"), packageId = pkg)
        )
    }
}

fun getLimitBreakerTemplate(lang: String = "en"): GameTemplate {
    val pkg = "saitama_v1"
    val isAr = lang == "ar"

    val daily = if (isAr) {
        listOf(
            QuestTemplate(QuestCategory.FITNESS, 1, "10 تمارين ضغط", "💪", 1, false, null, pkg),
            QuestTemplate(QuestCategory.FITNESS, 1, "10 تمارين بطن", "🔥", 1, false, null, pkg),
            QuestTemplate(QuestCategory.FITNESS, 1, "10 تمارين قرفصاء", "🦵", 1, false, null, pkg),
            QuestTemplate(QuestCategory.FITNESS, 1, "جري 1 كم", "🏃", 1, false, null, pkg),

            QuestTemplate(QuestCategory.FITNESS, 3, "50 تمرين ضغط", "💪", 1, false, null, pkg),
            QuestTemplate(QuestCategory.FITNESS, 3, "50 تمرين بطن", "🔥", 1, false, null, pkg),
            QuestTemplate(QuestCategory.FITNESS, 3, "50 تمرين قرفصاء", "🦵", 1, false, null, pkg),
            QuestTemplate(QuestCategory.FITNESS, 3, "جري 5 كم", "🏃", 1, false, null, pkg),

            QuestTemplate(QuestCategory.FITNESS, 5, "100 تمرين ضغط", "💥", 1, true, null, pkg),
            QuestTemplate(QuestCategory.FITNESS, 5, "100 تمرين بطن", "🔥", 1, true, null, pkg),
            QuestTemplate(QuestCategory.FITNESS, 5, "100 تمرين قرفصاء", "🦵", 1, true, null, pkg),
            QuestTemplate(QuestCategory.FITNESS, 5, "جري 10 كم", "🏃", 1, true, null, pkg),

            QuestTemplate(QuestCategory.DISCIPLINE, 5, "لا مكيف في الصيف", "🥵", 1, true, null, pkg),
            QuestTemplate(QuestCategory.DISCIPLINE, 5, "لا مدفأة في الشتاء", "🥶", 1, true, null, pkg),
            QuestTemplate(QuestCategory.HYDRATION, 2, "أكل موزة", "🍌", 1, true, null, pkg),
            QuestTemplate(QuestCategory.MIND, 4, "قراءة مانجا البطل", "📚", 1, false, null, pkg)
        )
    } else {
        listOf(
            QuestTemplate(QuestCategory.FITNESS, 1, "10 Push-ups", "💪", 1, false, null, pkg),
            QuestTemplate(QuestCategory.FITNESS, 1, "10 Sit-ups", "🔥", 1, false, null, pkg),
            QuestTemplate(QuestCategory.FITNESS, 1, "10 Squats", "🦵", 1, false, null, pkg),
            QuestTemplate(QuestCategory.FITNESS, 1, "1km Run", "🏃", 1, false, null, pkg),

            QuestTemplate(QuestCategory.FITNESS, 3, "50 Push-ups", "💪", 1, false, null, pkg),
            QuestTemplate(QuestCategory.FITNESS, 3, "50 Sit-ups", "🔥", 1, false, null, pkg),
            QuestTemplate(QuestCategory.FITNESS, 3, "50 Squats", "🦵", 1, false, null, pkg),
            QuestTemplate(QuestCategory.FITNESS, 3, "5km Run", "🏃", 1, false, null, pkg),

            QuestTemplate(QuestCategory.FITNESS, 5, "100 Push-ups", "💥", 1, true, null, pkg),
            QuestTemplate(QuestCategory.FITNESS, 5, "100 Sit-ups", "🔥", 1, true, null, pkg),
            QuestTemplate(QuestCategory.FITNESS, 5, "100 Squats", "🦵", 1, true, null, pkg),
            QuestTemplate(QuestCategory.FITNESS, 5, "10km Run", "🏃", 1, true, null, pkg),

            QuestTemplate(QuestCategory.DISCIPLINE, 5, "No AC in Summer", "🥵", 1, true, null, pkg),
            QuestTemplate(QuestCategory.DISCIPLINE, 5, "No Heater in Winter", "🥶", 1, true, null, pkg),
            QuestTemplate(QuestCategory.HYDRATION, 2, "Eat a Banana", "🍌", 1, true, null, pkg),
            QuestTemplate(QuestCategory.MIND, 4, "Read Focus Manga", "📚", 1, false, null, pkg)
        )
    }

    val mq1 = if (isAr) {
        CustomMainQuest("hero_1", "الفئة C: مساعدة المدنيين", "ساعد غريبًا في مهمة بدنية.", listOf("مسح المنطقة", "عرض المساعدة", "رفع جسم ثقيل"), packageId = pkg)
    } else {
        CustomMainQuest("hero_1", "C-Class: Civilian Assist", "Help a stranger with a physical task.", listOf("Scan area", "Offer help", "Lift heavy object"), packageId = pkg)
    }
    val mq2 = if (isAr) {
        CustomMainQuest("hero_2", "الفئة B: عشر النقابة", "تبرع للجمعيات الخيرية أو اشترِ وجبة لشخص ما.", listOf("البحث عن جمعية", "التبرع"), prerequisiteId = "hero_1", packageId = pkg)
    } else {
        CustomMainQuest("hero_2", "B-Class: Guild Tithe", "Donate to charity or buy someone a meal.", listOf("Find charity", "Donate"), prerequisiteId = "hero_1", packageId = pkg)
    }
    val mq3 = if (isAr) {
        CustomMainQuest("hero_3", "الفئة A: جناح الشفاء", "قم بزيارة أو اتصل بصديق/أحد أفراد الأسرة المريض.", listOf("اتصال", "تقديم التشجيع"), prerequisiteId = "hero_2", packageId = pkg)
    } else {
        CustomMainQuest("hero_3", "A-Class: Healing Ward", "Visit or call a sick friend/family member.", listOf("Call contact", "Give encouragement"), prerequisiteId = "hero_2", packageId = pkg)
    }
    val mq4 = if (isAr) {
        CustomMainQuest("hero_4", "الفئة S: عمل العمالقة", "ساعد شخصًا في نقل منزله أو القيام بعمل شاق.", listOf("الوصول", "العمل بجد"), prerequisiteId = "hero_3", packageId = pkg)
    } else {
        CustomMainQuest("hero_4", "S-Class: Titan Labor", "Help someone move house or do heavy labor.", listOf("Arrive", "Work hard"), prerequisiteId = "hero_3", packageId = pkg)
    }

    return GameTemplate(
        templateName = if (isAr) "كاسر الحدود (سايتاما)" else "Limit Breaker (Saitama)",
        appTheme = AppTheme.DEFAULT,
        dailyQuests = daily,
        mainQuests = listOf(mq1, mq2, mq3, mq4),
        packageId = pkg,
        templateSettings = TemplateSettings(),
        isPremium = true
    )
}

fun generateDailyQuestsAdaptive(
    seed: Long,
    pool: List<QuestTemplate>,
    history: Map<Long, HistoryEntry>,
    recentFailedTitles: Set<String>,
    difficultyPreference: DifficultyPreference,
    desiredCount: Int = 5
): List<Quest> {
    if (pool.isEmpty()) return emptyList()
    val completion7 = history.toList().sortedByDescending { it.first }.take(7)
    val completionRate = if (completion7.isEmpty()) 0.65f else {
        val done = completion7.sumOf { it.second.done }
        val total = completion7.sumOf { it.second.total.coerceAtLeast(1) }
        done.toFloat() / total.toFloat()
    }
    val preferenceOffset = when (difficultyPreference) {
        DifficultyPreference.CHILL -> -1
        DifficultyPreference.NORMAL -> 0
        DifficultyPreference.HARDCORE -> 1
    }
    val adaptiveOffset = when {
        completionRate >= 0.85f -> 1
        completionRate <= 0.45f -> -1
        else -> 0
    }
    val adaptiveCap = (3 + adaptiveOffset + preferenceOffset).coerceIn(1, 5)
    val filtered = pool.filter { it.difficulty <= adaptiveCap && !recentFailedTitles.contains(it.title) }
    val fallback = if (filtered.isEmpty()) pool.filter { it.difficulty <= adaptiveCap } else filtered
    val result = generateDailyQuests(seed = seed, pool = fallback, desiredCount = desiredCount)
    return if (result.size == desiredCount.coerceIn(3, 10)) result else generateDailyQuests(seed = seed, pool = pool, desiredCount = desiredCount)
}

fun bestWeekdayByCompletion(history: Map<Long, HistoryEntry>, lang: String = "en"): String {
    if (history.isEmpty()) return "N/A"
    val isAr = lang == "ar"
    val buckets = mutableMapOf<Int, MutableList<Float>>()
    history.forEach { (day, entry) ->
        val cal = GregorianCalendar(UTC)
        cal.timeInMillis = TimeUnit.DAYS.toMillis(day)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val ratio = entry.done.toFloat() / entry.total.coerceAtLeast(1).toFloat()
        buckets.getOrPut(dow) { mutableListOf() }.add(ratio)
    }
    val best = buckets.maxByOrNull { (_, values) -> values.average() }?.key ?: return "N/A"
    return when (best) {
        Calendar.MONDAY -> if (isAr) "الإثنين" else "Monday"
        Calendar.TUESDAY -> if (isAr) "الثلاثاء" else "Tuesday"
        Calendar.WEDNESDAY -> if (isAr) "الأربعاء" else "Wednesday"
        Calendar.THURSDAY -> if (isAr) "الخميس" else "Thursday"
        Calendar.FRIDAY -> if (isAr) "الجمعة" else "Friday"
        Calendar.SATURDAY -> if (isAr) "السبت" else "Saturday"
        else -> if (isAr) "الأحد" else "Sunday"
    }
}

fun getDefaultGameTemplate(lang: String = "en"): GameTemplate {
    val pkg = REAL_DAILY_LIFE_PACKAGE_ID
    val daily = getRealDailyLifePool(pkg, lang)
    val main = getRealDailyLifeMainQuests(pkg, lang)
    return GameTemplate(
        templateName = if (lang == "ar") "نظام الحياة اليومية الحقيقي" else "Real Daily Life System",
        appTheme = AppTheme.DEFAULT,
        dailyQuests = daily,
        mainQuests = main,
        packageId = pkg,
        templateSettings = TemplateSettings()
    )
}

fun getEmptyStarterTemplate(lang: String = "en"): GameTemplate {
    val pkg = "empty_pack"
    return GameTemplate(
        templateName = if (lang == "ar") "بداية فارغة" else "Empty Start",
        appTheme = AppTheme.DEFAULT,
        dailyQuests = emptyList(),
        mainQuests = emptyList(),
        packageId = pkg,
        templateSettings = TemplateSettings()
    )
}

private data class RealLifeSeed(
    val title: String,
    val titleAr: String,
    val icon: String,
    val baseTarget: Int = 1
)

private fun expandRealLifeSeeds(
    category: QuestCategory,
    packageId: String,
    seeds: List<RealLifeSeed>,
    lang: String = "en"
): List<QuestTemplate> {
    val isAr = lang == "ar"
    val tierLabels = if (isAr) listOf("مبتدئ", "استمرارية", "تقدم", "تحدي", "إتقان") else listOf("Starter", "Consistency", "Progress", "Challenge", "Mastery")
    val tierTargetScale = listOf(1, 1, 2, 3, 4)
    return seeds.flatMap { seed ->
        (1..5).map { tier ->
            val tierIndex = tier - 1
            val title = if (isAr) "${seed.titleAr} • ${tierLabels[tierIndex]}" else "${seed.title} • ${tierLabels[tierIndex]}"
            QuestTemplate(
                category = category,
                difficulty = tier,
                title = title,
                icon = seed.icon,
                target = (seed.baseTarget * tierTargetScale[tierIndex]).coerceAtLeast(1),
                isPinned = false,
                imageUri = null,
                packageId = packageId
            )
        }
    }
}

private fun getRealDailyLifePool(packageId: String, lang: String = "en"): List<QuestTemplate> {
    val fitnessSeeds = listOf(
        RealLifeSeed("Walk 1 km", "مشى 1 كم", "🚶", 1),
        RealLifeSeed("Mobility stretch routine", "روتين تمدد الحركة", "🤸", 1),
        RealLifeSeed("Bodyweight circuit", "دائرة وزن الجسم", "🏋️", 1),
        RealLifeSeed("Climb stairs intentionally", "صعود الدرج عمداً", "🪜", 1),
        RealLifeSeed("Core stability session", "جلسة استقرار الجذع", "🧱", 1),
        RealLifeSeed("Posture and back care", "العناية بالقوام والظهر", "🧍", 1),
        RealLifeSeed("Cardio interval block", "كتلة تمارين الكارديو", "❤️", 1),
        RealLifeSeed("Leg strength routine", "روتين قوة الساق", "🦵", 1),
        RealLifeSeed("Upper body push session", "جلسة دفع الجزء العلوي", "💪", 1),
        RealLifeSeed("Recovery walk after meal", "مشية استشفاء بعد الوجبة", "🌤️", 1),
        RealLifeSeed("Desk break movement set", "مجموعة حركة استراحة المكتب", "🖥️", 2),
        RealLifeSeed("Breath + movement reset", "إعادة ضبط التنفس والحركة", "🌬️", 2),
        RealLifeSeed("Hip opening routine", "روتين فتح الحوض", "🧘", 1),
        RealLifeSeed("Balance drill practice", "ممارسة تمارين التوازن", "⚖️", 2),
        RealLifeSeed("Outdoor sunlight session", "جلسة ضوء الشمس في الهواء الطلق", "☀️", 1),
        RealLifeSeed("Jog or brisk walk", "جري أو مشي سريع", "🏃", 1),
        RealLifeSeed("Push-up quality reps", "تكرارات ضغط عالية الجودة", "🔥", 6),
        RealLifeSeed("Squat quality reps", "تكرارات قرفصاء عالية الجودة", "🦿", 8),
        RealLifeSeed("Low-impact cardio", "كارديو منخفض التأثير", "🚴", 1),
        RealLifeSeed("Evening unwind stretch", "تمدد الاسترخاء المسائي", "🌙", 1)
    )
    val studySeeds = listOf(
        RealLifeSeed("Deep work block", "كتلة عمل عميق", "🧠", 1),
        RealLifeSeed("Read non-fiction pages", "قراءة صفحات غير خيالية", "📚", 12),
        RealLifeSeed("Skill practice session", "جلسة ممارسة مهارة", "🛠️", 1),
        RealLifeSeed("Write project notes", "كتابة ملاحظات المشروع", "📝", 1),
        RealLifeSeed("Plan next learning goal", "تخطيط هدف التعلم القادم", "🎯", 1),
        RealLifeSeed("Review flashcards", "مراجعة بطاقات تعليمية", "🃏", 20),
        RealLifeSeed("Language practice", "ممارسة اللغة", "🗣️", 1),
        RealLifeSeed("Career learning module", "وحدة تعلم وظيفي", "💼", 1),
        RealLifeSeed("Budget learning session", "جلسة تعلم الميزانية", "📊", 1),
        RealLifeSeed("Research life admin topic", "البحث في موضوع إداري حياتي", "🔎", 1),
        RealLifeSeed("Summarize an article", "تلخيص مقال", "📰", 1),
        RealLifeSeed("Practice focused typing", "ممارسة الكتابة المركزة", "⌨️", 1),
        RealLifeSeed("Organize study materials", "تنظيم مواد الدراسة", "🗂️", 1),
        RealLifeSeed("Problem-solving drills", "تمارين حل المشكلات", "🧩", 1),
        RealLifeSeed("Learn from a lecture", "التعلم من محاضرة", "🎓", 1),
        RealLifeSeed("Review yesterday notes", "مراجعة ملاحظات الأمس", "📒", 1),
        RealLifeSeed("Create a mini project", "إنشاء مشروع صغير", "🧪", 1),
        RealLifeSeed("Document wins and gaps", "توثيق الإنجازات والفجوات", "✅", 1),
        RealLifeSeed("Practice communication skill", "ممارسة مهارة التواصل", "🎤", 1),
        RealLifeSeed("Study with no distractions", "الدراسة بدون تشتيت", "📵", 1)
    )
    val hydrationSeeds = listOf(
        RealLifeSeed("Drink 3 cups of water", "شرب 3 أكواب ماء", "💧", 3),
        RealLifeSeed("Finish 2 water bottles", "إنهاء زجاجتي ماء", "🫗", 2),
        RealLifeSeed("Hydrate before each meal", "شرب الماء قبل كل وجبة", "🥤", 3),
        RealLifeSeed("Eat fruit serving", "تناول حصة فاكهة", "🍎", 1),
        RealLifeSeed("Eat protein-focused meal", "تناول وجبة غنية بالبروتين", "🥚", 1),
        RealLifeSeed("Add vegetables to meal", "إضافة خضروات للوجبة", "🥦", 1),
        RealLifeSeed("Avoid sugary drinks today", "تجنب المشروبات السكرية اليوم", "🚫", 1),
        RealLifeSeed("Make healthy lunch choice", "اختيار غداء صحي", "🥗", 1),
        RealLifeSeed("Track water intake", "تتبع كمية شرب الماء", "📏", 1),
        RealLifeSeed("Prepare water for tomorrow", "تحضير الماء للغد", "🧊", 1),
        RealLifeSeed("Drink tea without sugar", "شرب شاي بدون سكر", "🍵", 1),
        RealLifeSeed("No late-night heavy snack", "لا وجبات ثقيلة في وقت متأخر", "🌜", 1),
        RealLifeSeed("Balanced breakfast", "إفطار متوازن", "🍳", 1),
        RealLifeSeed("Mindful eating pace", "وتيرة أكل واعية", "⏱️", 1),
        RealLifeSeed("Limit processed snack", "الحد من الوجبات الخفيفة المصنعة", "📉", 1),
        RealLifeSeed("Carry water when outside", "حمل الماء عند الخروج", "🎒", 1),
        RealLifeSeed("Refill bottle proactively", "إعادة ملء الزجاجة بشكل استباقي", "🚰", 2),
        RealLifeSeed("Electrolyte-friendly hydration", "ترطيب غني بالإلكتروليتات", "⚡", 1),
        RealLifeSeed("Healthy dinner portion", "حصة عشاء صحية", "🍲", 1),
        RealLifeSeed("Daily nutrition check-in", "تسجيل التغذية اليومي", "📋", 1)
    )
    val disciplineSeeds = listOf(
        RealLifeSeed("Make your bed", "ترتيب سريرك", "🛏️", 1),
        RealLifeSeed("10-minute room reset", "إعادة ضبط الغرفة في 10 دقائق", "🧹", 1),
        RealLifeSeed("Clear sink and dishes", "تنظيف الحوض والأطباق", "🍽️", 1),
        RealLifeSeed("Laundry progress step", "خطوة في غسيل الملابس", "🧺", 1),
        RealLifeSeed("Declutter one zone", "إزالة الفوضى من منطقة واحدة", "📦", 1),
        RealLifeSeed("Inbox cleanup session", "جلسة تنظيف صندوق الوارد", "📥", 1),
        RealLifeSeed("Pay bill or track due dates", "دفع فاتورة أو تتبع المواعيد", "💸", 1),
        RealLifeSeed("Schedule appointment", "تحديد موعد", "📅", 1),
        RealLifeSeed("Prepare tomorrow outfit", "تحضير ملابس الغد", "👕", 1),
        RealLifeSeed("Meal prep basic step", "خطوة أساسية في تحضير الوجبات", "🍱", 1),
        RealLifeSeed("20-min focused cleaning", "تنظيف مركز لمدة 20 دقيقة", "🧼", 1),
        RealLifeSeed("No impulse buy today", "لا شراء اندفاعي اليوم", "🛑", 1),
        RealLifeSeed("Review monthly budget", "مراجعة الميزانية الشهرية", "📈", 1),
        RealLifeSeed("Family logistics planning", "تخطيط الخدمات اللوجستية للعائلة", "🏠", 1),
        RealLifeSeed("Errand completion", "إكمال مهمة خارجية", "🛒", 1),
        RealLifeSeed("Organize important docs", "تنظيم الوثائق الهامة", "🗃️", 1),
        RealLifeSeed("Sleep routine on time", "روتين النوم في الوقت المحدد", "😴", 1),
        RealLifeSeed("Morning routine consistency", "اتساق الروتين الصباحي", "🌅", 1),
        RealLifeSeed("Evening shutdown ritual", "طقوس الإغلاق المسائية", "🌆", 1),
        RealLifeSeed("Respect personal boundaries", "احترام الحدود الشخصية", "🧭", 1)
    )
    val mindSeeds = listOf(
        RealLifeSeed("Journal reflection", "تأمل في السجل", "📖", 1),
        RealLifeSeed("Meditation practice", "ممارسة التأمل", "🧘", 1),
        RealLifeSeed("Breathing reset", "إعادة ضبط التنفس", "🌬️", 2),
        RealLifeSeed("Call a family member", "الاتصال بأحد أفراد العائلة", "📞", 1),
        RealLifeSeed("Visit family or elder", "زيارة العائلة أو كبار السن", "👨‍👩‍👧", 1),
        RealLifeSeed("Quality talk with partner/friend", "حديث ذو جودة مع شريك/صديق", "💬", 1),
        RealLifeSeed("Express gratitude to someone", "التعبير عن الامتنان لشخص ما", "🙏", 1),
        RealLifeSeed("Check in on a friend", "الاطمئنان على صديق", "🤝", 1),
        RealLifeSeed("Digital detox block", "كتلة التخلص من السموم الرقمية", "📵", 1),
        RealLifeSeed("Nature reset walk", "مشية استرخاء في الطبيعة", "🌳", 1),
        RealLifeSeed("Acts of kindness", "أفعال طيبة", "💖", 1),
        RealLifeSeed("Therapy/self-help exercise", "تمرين علاج/مساعدة ذاتية", "🧠", 1),
        RealLifeSeed("Set one emotional boundary", "وضع حد عاطفي واحد", "🛡️", 1),
        RealLifeSeed("Low-stress hobby time", "وقت لهواية منخفضة التوتر", "🎨", 1),
        RealLifeSeed("Silent reflection block", "كتلة تأمل صامت", "🕯️", 1),
        RealLifeSeed("Conflict repair message", "رسالة إصلاح خلاف", "🕊️", 1),
        RealLifeSeed("Family appreciation note", "ملاحظة تقدير للعائلة", "💌", 1),
        RealLifeSeed("Mindful break from rushing", "استراحة واعية من العجلة", "🐢", 1),
        RealLifeSeed("Positive self-talk practice", "ممارسة حديث إيجابي مع الذات", "✨", 1),
        RealLifeSeed("Weekly purpose review", "مراجعة الغرض الأسبوعية", "🧭", 1)
    )

    return buildList {
        addAll(expandRealLifeSeeds(QuestCategory.FITNESS, packageId, fitnessSeeds, lang))
        addAll(expandRealLifeSeeds(QuestCategory.STUDY, packageId, studySeeds, lang))
        addAll(expandRealLifeSeeds(QuestCategory.HYDRATION, packageId, hydrationSeeds, lang))
        addAll(expandRealLifeSeeds(QuestCategory.DISCIPLINE, packageId, disciplineSeeds, lang))
        addAll(expandRealLifeSeeds(QuestCategory.MIND, packageId, mindSeeds, lang))
    }
}

private fun getRealDailyLifeMainQuests(packageId: String, lang: String = "en"): List<CustomMainQuest> {
    val isAr = lang == "ar"
    return if (isAr) {
        listOf(
            CustomMainQuest("life_arc_1", "الأسبوع 1: استقرار الصباح", "ثبت روتينًا صباحيًا قابلاً للتكرار وحدًا أدنى للترطيب.", listOf("الاستيقاظ في الوقت المحدد 4 أيام", "شرب الماء عند الاستيقاظ", "تخطيط أولوية واحدة كل يوم"), packageId = packageId),
            CustomMainQuest("life_arc_2", "الأسبوع 2: أسس الصحة", "ابنِ اتساقاً في الحركة والوجبات.", listOf("حركة لمدة 5 أيام", "وجبات غنية بالبروتين", "اتساق نافذة النوم"), prerequisiteId = "life_arc_1", packageId = packageId),
            CustomMainQuest("life_arc_3", "الأسبوع 3: أنظمة المنزل", "قلل الاحتكاك في المنزل بأنظمة التنظيف والتخطيط.", listOf("إزالة الفوضى من منطقتين", "إيقاع الغسيل + الأطباق", "تجميع المهمات الخارجية"), prerequisiteId = "life_arc_2", packageId = packageId),
            CustomMainQuest("life_arc_4", "الأسبوع 4: تنظيف المالية", "اجلب الوضوح للمال ومواعيد الاستحقاق.", listOf("تتبع الإنفاق", "دفع أو جدولة الفواتير الرئيسية", "تحديد ميزانية الشهر القادم"), prerequisiteId = "life_arc_3", packageId = packageId),
            CustomMainQuest("life_arc_5", "الأسبوع 5: الاتصال العائلي", "أصلح وعزز العلاقات العائلية.", listOf("اتصال/اطمئنان مرتين", "زيارة عائلية واحدة", "رسالة تقدير واحدة"), prerequisiteId = "life_arc_4", packageId = packageId),
            CustomMainQuest("life_arc_6", "الأسبوع 6: سباق نمو المهارات", "أنجز مشروع نمو عملي واحد.", listOf("اختر مهارة واحدة", "ممارسة 5 جلسات", "نشر/مشاركة النتيجة"), prerequisiteId = "life_arc_5", packageId = packageId),
            CustomMainQuest("life_arc_7", "الأسبوع 7: المرونة تجاه التوتر", "درب التنظيم العاطفي تحت ضغط حقيقي.", listOf("إعادة ضبط التنفس يومياً", "ثلاث كتل تركيز بدون هاتف", "التعامل بهدوء مع محادثة صعبة واحدة"), prerequisiteId = "life_arc_6", packageId = packageId),
            CustomMainQuest("life_arc_8", "الأسبوع 8: المجتمع والخدمة", "ساهم في الأشخاص من حولك.", listOf("عمل تطوعي/مساعدة واحد", "دعم صديق أو جار", "متابعة مقصودة"), prerequisiteId = "life_arc_7", packageId = packageId),
            CustomMainQuest("life_arc_9", "الأسبوع 9: مراجعة وترقية الحياة", "راجع العادات وأعد تصميم نقاط الضعف.", listOf("مراجعة سجل 8 أسابيع", "استبدال عادتين منخفضة القيمة", "تصميم خطة الـ 30 يوماً القادمة"), prerequisiteId = "life_arc_8", packageId = packageId)
        )
    } else {
        listOf(
            CustomMainQuest("life_arc_1", "Week 1: Stabilize Morning", "Lock in a repeatable morning routine and hydration baseline.", listOf("Wake on time 4 days", "Hydrate at wake-up", "Plan one priority each day"), packageId = packageId),
            CustomMainQuest("life_arc_2", "Week 2: Health Foundations", "Build movement and meal consistency.", listOf("Movement 5 days", "Protein-forward meals", "Sleep window consistency"), prerequisiteId = "life_arc_1", packageId = packageId),
            CustomMainQuest("life_arc_3", "Week 3: Home Systems", "Reduce friction at home with cleanup and planning systems.", listOf("Declutter two zones", "Laundry + dishes rhythm", "Errand batching"), prerequisiteId = "life_arc_2", packageId = packageId),
            CustomMainQuest("life_arc_4", "Week 4: Finance Cleanup", "Bring clarity to money and due dates.", listOf("Track spending", "Pay or schedule key bills", "Set next month budget"), prerequisiteId = "life_arc_3", packageId = packageId),
            CustomMainQuest("life_arc_5", "Week 5: Family Connection", "Repair and strengthen family relationships.", listOf("Call/check-in twice", "One family visit", "One appreciation message"), prerequisiteId = "life_arc_4", packageId = packageId),
            CustomMainQuest("life_arc_6", "Week 6: Skill Growth Sprint", "Ship one practical growth project.", listOf("Pick one skill", "Practice 5 sessions", "Publish/share outcome"), prerequisiteId = "life_arc_5", packageId = packageId),
            CustomMainQuest("life_arc_7", "Week 7: Stress Resilience", "Train emotional regulation under real pressure.", listOf("Daily breath reset", "Three no-phone focus blocks", "One hard conversation handled calmly"), prerequisiteId = "life_arc_6", packageId = packageId),
            CustomMainQuest("life_arc_8", "Week 8: Community and Service", "Contribute to people around you.", listOf("One volunteer/help act", "Support friend or neighbor", "Follow up intentionally"), prerequisiteId = "life_arc_7", packageId = packageId),
            CustomMainQuest("life_arc_9", "Week 9: Life Review and Upgrade", "Audit habits and redesign weak points.", listOf("Review 8-week history", "Replace 2 low-value habits", "Design next 30-day plan"), prerequisiteId = "life_arc_8", packageId = packageId)
        )
    }
}

fun getRealWorldMomentumTemplate(lang: String = "en"): GameTemplate {
    val pkg = REAL_WORLD_MOMENTUM_PACKAGE_ID
    val isAr = lang == "ar"
    return GameTemplate(
        templateName = if (isAr) "زخم العالم الحقيقي" else "Real World Momentum",
        appTheme = AppTheme.DEFAULT,
        dailyQuests = getRealDailyLifePool(pkg, lang),
        mainQuests = getRealWorldMomentumMainQuests(pkg, lang),
        packageId = pkg,
        templateSettings = TemplateSettings()
    )
}

private fun getRealWorldMomentumMainQuests(packageId: String, lang: String = "en"): List<CustomMainQuest> {
    val isAr = lang == "ar"
    return if (isAr) {
        listOf(
            CustomMainQuest("rw_arc_1", "الفصل 1: قاعدة المشي + الترطيب", "ثبت عاداتك الأساسية أولاً.", listOf("مشى 1 كم في 3 أيام منفصلة", "شرب 3 أكواب ماء قبل الظهر في 4 أيام", "تسجيل الإكمال بصدق"), packageId = packageId),
            CustomMainQuest("rw_arc_2", "الفصل 2: نظام المنزل", "ابنِ إيقاع منزلي نظيف وقليل الاحتكاك.", listOf("إعادة ضبط الغرفة لمدة 10 دقائق لـ 5 أيام", "إيقاع الغسيل + الأطباق", "تحضير الغد كل مساء"), prerequisiteId = "rw_arc_1", packageId = packageId),
            CustomMainQuest("rw_arc_3", "الفصل 3: إعادة الاتصال بالدائرة", "عزز شبكة الدعم الاجتماعي الخاصة بك.", listOf("الاطمئنان على 3 أصدقاء/عائلة", "اتصال هادف واحد", "رسالة امتنان واحدة"), prerequisiteId = "rw_arc_2", packageId = packageId),
            CustomMainQuest("rw_arc_4", "الفصل 4: زيارة دعم", "كن متواجداً لشخص في فترة صعبة.", listOf("زيارة صديق/عائلة في مستشفى أو سجن عند الاقتضاء", "إذا لم يكن ممكناً، إجراء اتصال دعم طويل", "المتابعة مرة أخرى خلال الأسبوع"), prerequisiteId = "rw_arc_3", packageId = packageId),
            CustomMainQuest("rw_arc_5", "الفصل 5: مهمة نزهة نهاية الأسبوع", "ادفع قدرتك على التحمل بمجهود خارجي هادف.", listOf("تخطيط المسار + تحضير السلامة", "إكمال نزهة واحدة أو مشي طويل في ممر", "روتين استشفاء ما بعد النزهة"), prerequisiteId = "rw_arc_4", packageId = packageId),
            CustomMainQuest("rw_arc_6", "الفصل 6: انضباط التغذية", "ارتقِ بجودة الوجبات واتساقها.", listOf("وجبات غنية بالبروتين لـ 5 أيام", "لا مشروبات سكرية لـ 6 أيام", "تحقيق هدف الترطيب لـ 6 أيام"), prerequisiteId = "rw_arc_5", packageId = packageId),
            CustomMainQuest("rw_arc_7", "الفصل 7: المثبت المالي", "قلل من ضغوط المال بأنظمة نظيفة.", listOf("تتبع كل إنفاق لمدة 7 أيام", "دفع/جدولة الرسوم الرئيسية", "تحديد سقف الشهر القادم + مخزن طوارئ"), prerequisiteId = "rw_arc_6", packageId = packageId),
            CustomMainQuest("rw_arc_8", "الفصل 8: خدمة المجتمع", "ساهم بما هو أبعد من قائمة مهامك الخاصة.", listOf("عمل تطوعي/مساعدة واحد", "دعم مهمة لجار/صديق", "توثيق الأثر"), prerequisiteId = "rw_arc_7", packageId = packageId),
            CustomMainQuest("rw_arc_9", "الفصل 9: سباق نمو المهارات", "أنجز ترقية واحدة عملية للحياة/الوظيفة.", listOf("اختر مهارة نمو واحدة", "5 جلسات مركزة", "مشاركة أو نشر النتيجة"), prerequisiteId = "rw_arc_8", packageId = packageId),
            CustomMainQuest("rw_arc_10", "الفصل 10: الصعود في الحياة الحقيقية", "عزز المكاسب وثبت عادات المستوى التالي.", listOf("مراجعة قوس التقدم الكامل", "ترقية 3 عادات إلى غير قابلة للتفاوض", "كتابة مهمتك للـ 30 يوماً القادمة"), prerequisiteId = "rw_arc_9", packageId = packageId)
        )
    } else {
        listOf(
            CustomMainQuest("rw_arc_1", "Chapter 1: Walk + Hydrate Base", "Establish your baseline habits first.", listOf("Walk 1 km on 3 separate days", "Drink 3 cups of water before noon on 4 days", "Log completion honestly"), packageId = packageId),
            CustomMainQuest("rw_arc_2", "Chapter 2: Home Order", "Build a clean, low-friction home rhythm.", listOf("10-minute room reset for 5 days", "Laundry + dishes cadence", "Prep tomorrow each evening"), prerequisiteId = "rw_arc_1", packageId = packageId),
            CustomMainQuest("rw_arc_3", "Chapter 3: Reconnect Circle", "Strengthen your social support network.", listOf("Check in with 3 friends/family", "One meaningful call", "One gratitude message"), prerequisiteId = "rw_arc_2", packageId = packageId),
            CustomMainQuest("rw_arc_4", "Chapter 4: Support Visit", "Show up for someone in a difficult season.", listOf("Visit a friend/family in hospital or prison when applicable", "If not possible, complete a long support call", "Follow up again within the week"), prerequisiteId = "rw_arc_3", packageId = packageId),
            CustomMainQuest("rw_arc_5", "Chapter 5: Weekend Hike Mission", "Push endurance with one meaningful outdoor effort.", listOf("Plan route + safety prep", "Complete one hike or long trail walk", "Post-hike recovery routine"), prerequisiteId = "rw_arc_4", packageId = packageId),
            CustomMainQuest("rw_arc_6", "Chapter 6: Nutrition Discipline", "Upgrade meal quality and consistency.", listOf("Protein-forward meals 5 days", "No sugary drinks for 6 days", "Hydration target met for 6 days"), prerequisiteId = "rw_arc_5", packageId = packageId),
            CustomMainQuest("rw_arc_7", "Chapter 7: Financial Stabilizer", "Reduce money stress with clean systems.", listOf("Track every spend for 7 days", "Pay/schedule key dues", "Set next month cap + emergency buffer"), prerequisiteId = "rw_arc_6", packageId = packageId),
            CustomMainQuest("rw_arc_8", "Chapter 8: Community Service", "Contribute beyond your own to-do list.", listOf("One volunteer/help action", "Support a neighbor/friend task", "Document the impact"), prerequisiteId = "rw_arc_7", packageId = packageId),
            CustomMainQuest("rw_arc_9", "Chapter 9: Skill Growth Sprint", "Ship one practical life/career upgrade.", listOf("Choose one growth skill", "5 focused sessions", "Share or deploy the result"), prerequisiteId = "rw_arc_8", packageId = packageId),
            CustomMainQuest("rw_arc_10", "Chapter 10: Real-Life Ascension", "Consolidate wins and lock next-level habits.", listOf("Review the full progression arc", "Promote 3 habits to non-negotiables", "Write your next 30-day mission"), prerequisiteId = "rw_arc_9", packageId = packageId)
        )
    }
}

fun getStarterCommunityPosts(lang: String = "en"): List<CommunityPost> {
    val now = System.currentTimeMillis()
    val isAr = lang == "ar"
    return listOf(
        CommunityPost(
            authorId = "system_builder",
            authorName = if (isAr) "بناء النقابة" else "Guild Builder",
            title = if (isAr) "حزمة المغامر المبتدئ" else "Starter Adventurer Pack",
            description = if (isAr) "عادات يومية متوازنة ومهام أساسية مناسبة للمبتدئين." else "Balanced daily habits and beginner-friendly main quests.",
            tags = listOf("starter", "balanced", "habits"),
            template = getDefaultGameTemplate(lang),
            createdAtMillis = now - 1000L * 60L * 60L * 24L * 2L,
            ratingAverage = 4.6,
            ratingCount = 12,
            remixCount = 5
        ),
        CommunityPost(
            authorId = "system_saitama",
            authorName = if (isAr) "نادي كسر الحدود" else "Limit Break Club",
            title = if (isAr) "تحدي كاسر الحدود" else "Limit Breaker Challenge",
            description = if (isAr) "طحن لياقة بدنية عالي الانضباط مستوحى من أقواس تدريب الأبطال." else "High-discipline fitness grind inspired by hero training arcs.",
            tags = listOf("fitness", "hardcore", "discipline"),
            template = getLimitBreakerTemplate(lang),
            createdAtMillis = now - 1000L * 60L * 60L * 14L,
            ratingAverage = 4.8,
            ratingCount = 9,
            remixCount = 7
        )
    )
}

