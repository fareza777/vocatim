package com.vocatim.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vocatim.app.data.db.TranscriptEntity
import com.vocatim.app.data.repository.TranscriptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    repository: TranscriptRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val _month = MutableStateFlow(YearMonth.now(zone))
    val month: StateFlow<YearMonth> = _month

    private val _selectedDate = MutableStateFlow(LocalDate.now(zone))
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    /** Every transcript bucketed by its creation date. */
    private val byDate: StateFlow<Map<LocalDate, List<TranscriptEntity>>> =
        repository.observeAll()
            .map { list ->
                list.groupBy {
                    Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate()
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Dates in the visible month that have at least one transcript. */
    val markedDates: StateFlow<Set<LocalDate>> =
        combine(byDate, _month) { map, month ->
            map.keys.filterTo(mutableSetOf()) { YearMonth.from(it) == month }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val itemsForSelectedDate: StateFlow<List<TranscriptEntity>> =
        combine(byDate, _selectedDate) { map, date ->
            map[date].orEmpty().sortedByDescending { it.createdAt }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun previousMonth() {
        _month.value = _month.value.minusMonths(1)
    }

    fun nextMonth() {
        _month.value = _month.value.plusMonths(1)
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        if (YearMonth.from(date) != _month.value) {
            _month.value = YearMonth.from(date)
        }
    }
}
