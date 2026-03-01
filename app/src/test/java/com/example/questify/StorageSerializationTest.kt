package com.example.questify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageSerializationTest {

    @Test
    fun routines_roundTripSerialization() {
        val routines = listOf(
            Routine(
                id = 1,
                title = "Read",
                icon = "📘",
                category = RoutineCategory.STUDY,
                target = 2,
                currentProgress = 1,
                completed = false,
                packageId = "pkg"
            ),
            Routine(
                id = 2,
                title = "Walk",
                icon = "🚶",
                category = RoutineCategory.FITNESS,
                target = 1,
                currentProgress = 1,
                completed = true,
                packageId = "pkg"
            )
        )

        val encoded = serializeRoutines(routines)
        val decoded = deserializeRoutines(encoded)

        assertEquals(routines.size, decoded.size)
        assertEquals(routines[0].id, decoded[0].id)
        assertEquals(routines[0].title, decoded[0].title)
        assertEquals(routines[1].category, decoded[1].category)
    }

    @Test
    fun history_roundTripSerialization() {
        val history = mapOf(
            100L to HistoryEntry(done = 2, total = 5, allDone = false),
            101L to HistoryEntry(done = 5, total = 5, allDone = true)
        )

        val encoded = serializeHistory(history)
        val decoded = parseHistory(encoded)

        assertEquals(history.size, decoded.size)
        assertEquals(2, decoded[100L]?.done)
        assertEquals(true, decoded[101L]?.allDone)
    }

    @Test
    fun parseIds_handlesInvalidEntries() {
        val parsed = parseIds("1,2,not_number, 3,")
        assertEquals(setOf(1, 2, 3), parsed)
    }

    @Test
    fun importGameTemplate_rejectsOversizedPayload() {
        val oversized = "x".repeat(300_000)
        val parsed = importGameTemplate(oversized)
        assertTrue(parsed == null)
    }
}
