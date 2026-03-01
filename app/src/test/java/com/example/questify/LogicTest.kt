package com.example.questify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {

    @Test
    fun generateDailyRoutines_isDeterministicForSeed() {
        val pool = listOf(
            RoutineTemplate(RoutineCategory.FITNESS, 1, "Push-ups", "💪", 20),
            RoutineTemplate(RoutineCategory.STUDY, 1, "Read", "📚", 20),
            RoutineTemplate(RoutineCategory.HYDRATION, 1, "Water", "💧", 20),
            RoutineTemplate(RoutineCategory.DISCIPLINE, 1, "Clean desk", "🧹", 20),
            RoutineTemplate(RoutineCategory.MIND, 1, "Meditate", "🧘", 20),
            RoutineTemplate(RoutineCategory.FITNESS, 2, "Run", "🏃", 30)
        )

        val first = generateDailyRoutines(seed = 12345L, pool = pool)
        val second = generateDailyRoutines(seed = 12345L, pool = pool)

        assertEquals(first.map { it.id }, second.map { it.id })
        assertEquals(first.map { it.title }, second.map { it.title })
    }

    @Test
    fun generateDailyRoutines_respectsDesiredCount() {
        val pool = listOf(
            RoutineTemplate(RoutineCategory.FITNESS, 1, "Easy", "✅", 10),
            RoutineTemplate(RoutineCategory.FITNESS, 4, "Hard", "🔥", 60),
            RoutineTemplate(RoutineCategory.STUDY, 2, "Focus", "🧠", 20)
        )

        val routines = generateDailyRoutines(seed = 77L, pool = pool, desiredCount = 3)

        assertEquals(3, routines.size)
    }

    @Test
    fun stableRoutineId_isStableAndPositive() {
        val template = RoutineTemplate(RoutineCategory.STUDY, 2, "Deep Work", "🧠", 40)
        val id1 = stableRoutineId(RoutineCategory.STUDY, template)
        val id2 = stableRoutineId(RoutineCategory.STUDY, template)

        assertEquals(id1, id2)
        assertTrue(id1 > 0)
    }

    @Test
    fun generateDailyRoutinesAdaptive_fallsBackWhenFilteredPoolTooSmall() {
        val pool = listOf(
            RoutineTemplate(RoutineCategory.FITNESS, 1, "Easy", "✅", 10),
            RoutineTemplate(RoutineCategory.STUDY, 1, "Focus", "🧠", 10),
            RoutineTemplate(RoutineCategory.HYDRATION, 1, "Drink", "💧", 10)
        )
        val history = mapOf(
            1L to HistoryEntry(done = 0, total = 5, allDone = false),
            2L to HistoryEntry(done = 1, total = 5, allDone = false)
        )
        val routines = generateDailyRoutinesAdaptive(
            seed = 42L,
            pool = pool,
            history = history,
            recentFailedTitles = setOf("Easy"),
            desiredCount = 2
        )
        assertEquals(3, routines.size)
    }

    @Test
    fun refreshKeepingCompleted_respectsDesiredCount() {
        val pool = listOf(
            RoutineTemplate(RoutineCategory.FITNESS, 1, "Push", "💪", 10),
            RoutineTemplate(RoutineCategory.FITNESS, 1, "Run", "🏃", 12),
            RoutineTemplate(RoutineCategory.STUDY, 1, "Read", "📘", 10),
            RoutineTemplate(RoutineCategory.STUDY, 2, "Review", "🧠", 16),
            RoutineTemplate(RoutineCategory.HYDRATION, 1, "Water", "💧", 10),
            RoutineTemplate(RoutineCategory.HYDRATION, 2, "Electrolytes", "🫗", 14),
            RoutineTemplate(RoutineCategory.DISCIPLINE, 1, "Tidy", "🧹", 10),
            RoutineTemplate(RoutineCategory.DISCIPLINE, 2, "Budget", "📊", 16),
            RoutineTemplate(RoutineCategory.MIND, 1, "Breathe", "🌬️", 10),
            RoutineTemplate(RoutineCategory.MIND, 2, "Journal", "📖", 14)
        )
        val current = generateDailyRoutines(
            seed = 8L,
            pool = pool,
            desiredCount = 8
        )
        val refreshed = refreshKeepingCompleted(
            current = current,
            seed = 9L,
            pool = pool,
            desiredCount = 8
        )
        assertEquals(8, refreshed.size)
    }

    @Test
    fun bestWeekdayByCompletion_returnsReadableName() {
        val history = mapOf(
            epochDayFromYmd(2026, 2, 9) to HistoryEntry(5, 5, true),
            epochDayFromYmd(2026, 2, 10) to HistoryEntry(2, 5, false)
        )
        val best = bestWeekdayByCompletion(history)
        assertTrue(best.isNotBlank())
    }
}
