package com.icoordinator.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icoordinator.data.ReportRepository
import com.icoordinator.data.entity.WeeklyReportEntity
import com.icoordinator.domain.WeekUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class ReportViewModel(private val repo: ReportRepository) : ViewModel() {

    val reports: StateFlow<List<WeeklyReportEntity>> = repo.observeReports()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selected = MutableStateFlow<WeeklyReportEntity?>(null)
    val selected: StateFlow<WeeklyReportEntity?> = _selected.asStateFlow()

    /** گزارش هفته‌ی جاری را می‌سازد (یا اگر قبلاً بوده، به‌روز می‌کند). */
    fun generateCurrentWeek() {
        viewModelScope.launch {
            val week = WeekUtils.weekStartOf(LocalDate.now())
            _selected.value = repo.generate(week)
        }
    }

    fun select(report: WeeklyReportEntity) {
        _selected.value = report
    }
}
