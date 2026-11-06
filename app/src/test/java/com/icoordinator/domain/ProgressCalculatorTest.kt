package com.icoordinator.domain

import com.icoordinator.data.entity.GoalEntity
import com.icoordinator.data.entity.TaskEntity
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

    private fun goalWithId(id: Long, weight: Int, done: Boolean) =
        GoalEntity(id = id, projectId = 1, title = "g$id", weightPercent = weight, isDone = done)

    private fun task(goalId: Long?, done: Boolean, contribution: Int?) = TaskEntity(
        weekStart = 0,
        dueAt = 0,
        title = "t",
        isDone = done,
        goalId = goalId,
        goalContribution = contribution,
    )

    @Test
    fun goalProgress_doneGoalIs100() {
        assertEquals(100, ProgressCalculator.goalProgress(goalWithId(1, 50, true), emptyList()))
    }

    @Test
    fun goalProgress_sumsOnlyDoneTasksOfThatGoal() {
        val tasks = listOf(
            task(1, true, 30),
            task(1, true, 20),
            task(1, false, 40),
            task(1, true, null),
        )
        assertEquals(50, ProgressCalculator.goalProgress(goalWithId(1, 40, false), tasks))
    }

    @Test
    fun goalProgress_isCappedAt100() {
        val tasks = listOf(task(1, true, 70), task(1, true, 60))
        assertEquals(100, ProgressCalculator.goalProgress(goalWithId(1, 40, false), tasks))
    }

    @Test
    fun goalProgress_ignoresTasksOfOtherGoals() {
        val tasks = listOf(task(2, true, 80), task(null, true, 80), task(1, true, 10))
        assertEquals(10, ProgressCalculator.goalProgress(goalWithId(1, 40, false), tasks))
    }

    @Test
    fun projectProgress_combinesWeightsAndGoalProgress() {
        val a = goalWithId(1, 40, false)
        val b = goalWithId(2, 60, true)
        val tasks = listOf(task(1, true, 30), task(1, true, 20))
        assertEquals(50, ProgressCalculator.goalProgress(a, tasks))
        assertEquals(100, ProgressCalculator.goalProgress(b, tasks))
        assertEquals(80, ProgressCalculator.projectProgress(listOf(a, b), tasks))
    }

    @Test
    fun projectProgress_isZeroWithoutGoals() {
        assertEquals(0, ProgressCalculator.projectProgress(emptyList(), emptyList()))
    }
}
