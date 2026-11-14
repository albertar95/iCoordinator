package com.icoordinator.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icoordinator.ICoordinatorApp
import com.icoordinator.personal.PersonalRepository
import com.icoordinator.personal.RecordEntity
import com.icoordinator.personal.RecordMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Field errors of a record dialog (each shown under its own field); [general] is a failure that belongs to no field. */
data class RecordFormErrors(
    val title: String? = null,
    val target: String? = null,
    val current: String? = null,
    val general: String? = null,
) {
    val isEmpty: Boolean get() = title == null && target == null && current == null && general == null
}

class RecordsViewModel : ViewModel() {

    private val repo: PersonalRepository get() = ICoordinatorApp.instance.personalRepository

    /** Records in repository order (sortOrder, id); null until the first load. */
    val records: StateFlow<List<RecordEntity>?> =
        repo.observeRecords().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Validates the three texts and adds the record; [onResult] gets an empty [RecordFormErrors] on success. */
    fun addRecord(title: String, targetText: String, currentText: String, onResult: (RecordFormErrors) -> Unit) {
        val errors = RecordFormErrors(
            title = titleError(title),
            target = RecordMath.validateTarget(targetText),
            current = RecordMath.validateCurrent(currentText),
        )
        val target = RecordMath.parse(targetText)
        val current = RecordMath.parse(currentText)
        if (!errors.isEmpty || target == null || current == null) {
            onResult(errors)
            return
        }
        launchSave(onResult, "افزودن رکورد ممکن نشد") { repo.addRecord(title, target, current, System.currentTimeMillis()) }
    }

    fun editRecord(id: Long, title: String, targetText: String, onResult: (RecordFormErrors) -> Unit) {
        val errors = RecordFormErrors(title = titleError(title), target = RecordMath.validateTarget(targetText))
        val target = RecordMath.parse(targetText)
        if (!errors.isEmpty || target == null) {
            onResult(errors)
            return
        }
        launchSave(onResult, "ذخیره‌ی رکورد ممکن نشد") { repo.editRecord(id, title, target) }
    }

    fun updateValue(id: Long, currentText: String, onResult: (RecordFormErrors) -> Unit) {
        val errors = RecordFormErrors(current = RecordMath.validateCurrent(currentText))
        val value = RecordMath.parse(currentText)
        if (!errors.isEmpty || value == null) {
            onResult(errors)
            return
        }
        launchSave(onResult, "ثبت مقدار ممکن نشد") { repo.updateRecordValue(id, value, System.currentTimeMillis()) }
    }

    fun deleteRecord(id: Long, onResult: (String?) -> Unit = {}) {
        viewModelScope.launch {
            val error = try {
                withContext(Dispatchers.IO) { repo.deleteRecord(id) }
                null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                "حذف رکورد ممکن نشد"
            }
            onResult(error)
        }
    }

    private fun titleError(title: String): String? = if (title.isBlank()) "عنوان را وارد کن" else null

    private fun launchSave(onResult: (RecordFormErrors) -> Unit, failure: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) { block() }
                RecordFormErrors()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                RecordFormErrors(general = failure)
            }
            onResult(result)
        }
    }
}
