package com.icoordinator.ui.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icoordinator.data.RoutineRepository
import com.icoordinator.data.entity.RoutineEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class RoutinesViewModel(private val repo: RoutineRepository) : ViewModel() {

    val routines: StateFlow<List<RoutineEntity>> = repo.observeRoutines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** پیام خطا برای ورودی نامعتبر، وگرنه null. */
    private fun validate(title: String, days: Set<DayOfWeek>, startDate: LocalDate, endDate: LocalDate?): String? = when {
        title.isBlank() -> "عنوان روتین را وارد کن"
        days.isEmpty() -> "حداقل یک روز از هفته را انتخاب کن"
        endDate != null && endDate.isBefore(startDate) -> "تاریخ پایان نباید قبل از تاریخ شروع باشد"
        else -> null
    }

    /** برمی‌گرداند: true اگر روتین ثبت شد. endDate = null یعنی بدون پایان. */
    fun addRoutine(
        title: String,
        days: Set<DayOfWeek>,
        time: LocalTime,
        startDate: LocalDate,
        endDate: LocalDate?,
    ): Boolean {
        val message = validate(title, days, startDate, endDate)
        _error.value = message
        if (message != null) return false
        viewModelScope.launch { repo.addRoutine(title, days, time, startDate, endDate) }
        return true
    }

    /** برمی‌گرداند: true اگر ویرایش ثبت شد. */
    fun updateRoutine(
        routine: RoutineEntity,
        title: String,
        days: Set<DayOfWeek>,
        time: LocalTime,
        startDate: LocalDate,
        endDate: LocalDate?,
    ): Boolean {
        val message = validate(title, days, startDate, endDate)
        _error.value = message
        if (message != null) return false
        viewModelScope.launch { repo.updateRoutine(routine, title, days, time, startDate, endDate) }
        return true
    }

    fun deleteRoutine(routine: RoutineEntity) {
        viewModelScope.launch { repo.deleteRoutine(routine) }
    }

    fun clearError() {
        _error.value = null
    }
}
