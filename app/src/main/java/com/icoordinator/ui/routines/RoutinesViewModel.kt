package com.icoordinator.ui.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icoordinator.data.RoutineRepository
import com.icoordinator.data.entity.RoutineEntity
import com.icoordinator.domain.PersianCalendar
import com.icoordinator.domain.WeekUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

class RoutinesViewModel(private val repo: RoutineRepository) : ViewModel() {

    val routines: StateFlow<List<RoutineEntity>> = repo.observeRoutines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** برمی‌گرداند: true اگر روتین ثبت شد. startText/endText به شکل شمسی؛ endText خالی یعنی بدون پایان. */
    fun addRoutine(
        title: String,
        days: Set<DayOfWeek>,
        timeText: String,
        startText: String,
        endText: String,
    ): Boolean {
        if (title.isBlank()) {
            _error.value = "عنوان روتین را وارد کن"
            return false
        }
        if (days.isEmpty()) {
            _error.value = "حداقل یک روز از هفته را انتخاب کن"
            return false
        }
        val time = WeekUtils.parseTime(timeText)
        if (time == null) {
            _error.value = "ساعت را به‌صورت HH:mm وارد کن، مثلاً 07:30"
            return false
        }
        val start: LocalDate = if (startText.isBlank()) {
            LocalDate.now()
        } else {
            PersianCalendar.parse(startText) ?: run {
                _error.value = "تاریخ شروع را به شکل ۱۴۰۵/۰۸/۱۰ وارد کن"
                return false
            }
        }
        val end: LocalDate? = if (endText.isBlank()) {
            null
        } else {
            val parsed = PersianCalendar.parse(endText)
            if (parsed == null || parsed.isBefore(start)) {
                _error.value = "تاریخ پایان باید معتبر و بعد از تاریخ شروع باشد"
                return false
            }
            parsed
        }
        _error.value = null
        viewModelScope.launch { repo.addRoutine(title, days, time, start, end) }
        return true
    }

    fun deleteRoutine(routine: RoutineEntity) {
        viewModelScope.launch { repo.deleteRoutine(routine) }
    }

    fun clearError() {
        _error.value = null
    }
}
