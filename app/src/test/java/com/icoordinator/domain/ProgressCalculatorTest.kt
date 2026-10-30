package com.icoordinator.domain

import com.icoordinator.data.entity.GoalEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressCalculatorTest {

    private fun goal(weight: Int, done: Boolean) =
        GoalEntity(projectId = 1, title = "g", weightPercent = weight, isDone = done)

    @Test
    fun progress_sumsOnlyDoneGoals() {
        val goals = listOf(goal(10, true), goal(30, false), goal(20, true))
        assertEquals(30, ProgressCalculator.progress(goals))
    }

    @Test
    fun progress_isZeroWithoutGoals() {
        assertEquals(0, ProgressCalculator.progress(emptyList()))
    }

    @Test
    fun progress_isCappedAt100() {
        val goals = listOf(goal(60, true), goal(40, true))
        assertEquals(100, ProgressCalculator.progress(goals))
    }

    @Test
    fun remaining_neverNegative() {
        assertEquals(40, ProgressCalculator.remaining(60))
        assertEquals(0, ProgressCalculator.remaining(120))
    }

    @Test
    fun validate_acceptsWeightWithinRemaining() {
        assertNull(ProgressCalculator.validateNewWeight(currentTotal = 70, newWeight = 30))
    }

    @Test
    fun validate_rejectsWeightOverRemaining() {
        assertNotNull(ProgressCalculator.validateNewWeight(currentTotal = 70, newWeight = 31))
    }

    @Test
    fun validate_rejectsZeroOrNegative() {
        assertNotNull(ProgressCalculator.validateNewWeight(currentTotal = 0, newWeight = 0))
        assertNotNull(ProgressCalculator.validateNewWeight(currentTotal = 0, newWeight = -5))
    }
}
