package com.icoordinator.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.DayOfWeek

class RequestCodeTest {

    @Test
    fun routineCodes_differentPerDay() {
        val codes = DayOfWeek.values().map { TaskAlarmScheduler.routineCode(7L, it) }.toSet()
        assertEquals(7, codes.size)
    }

    @Test
    fun routineCodes_differFromTaskCodes() {
        val taskCode = TaskAlarmScheduler.taskCode(7L)
        val routineCode = TaskAlarmScheduler.routineCode(7L, DayOfWeek.MONDAY)
        assertNotEquals(taskCode, routineCode)
    }
}
