package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.HealthDatabase
import com.example.data.HealthRepository
import com.example.data.HeartRateEntry
import com.example.data.SleepEntry
import com.example.data.SyncState
import com.example.data.WeeklyInsight
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HeartRateSpike(
    val id: Int,
    val bpm: Int,
    val timestamp: Long,
    val dateLabel: String,
    val deviceSource: String
)

class HealthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: HealthRepository

    init {
        val database = HealthDatabase.getDatabase(application)
        repository = HealthRepository(database.healthDao())
    }

    val allHeartRates: StateFlow<List<HeartRateEntry>> = repository.allHeartRates
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentHeartRates: StateFlow<List<HeartRateEntry>> = repository.recentHeartRates
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allSleepEntries: StateFlow<List<SleepEntry>> = repository.allSleepEntries
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allSyncStates: StateFlow<List<SyncState>> = repository.allSyncStates
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val weeklyInsight: StateFlow<WeeklyInsight?> = repository.weeklyInsight
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _dismissedSpikeIds = MutableStateFlow<Set<Int>>(emptySet())
    val dismissedSpikeIds: StateFlow<Set<Int>> = _dismissedSpikeIds.asStateFlow()

    fun dismissSpikeAlert(id: Int) {
        _dismissedSpikeIds.value = _dismissedSpikeIds.value + id
    }

    val restingSpikes: StateFlow<List<HeartRateSpike>> = combine(
        repository.allHeartRates,
        _dismissedSpikeIds
    ) { heartRates, dismissedIds ->
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        heartRates.filter { it.activityType == "Resting" && it.bpm >= 90 && it.id !in dismissedIds }
            .map { entry ->
                HeartRateSpike(
                    id = entry.id,
                    bpm = entry.bpm,
                    timestamp = entry.timestamp,
                    dateLabel = sdf.format(Date(entry.timestamp)),
                    deviceSource = entry.deviceSource
                )
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _isSyncingFitbit = MutableStateFlow(false)
    val isSyncingFitbit: StateFlow<Boolean> = _isSyncingFitbit.asStateFlow()

    private val _isSyncingAppleWatch = MutableStateFlow(false)
    val isSyncingAppleWatch: StateFlow<Boolean> = _isSyncingAppleWatch.asStateFlow()

    private val _isGeneratingInsights = MutableStateFlow(false)
    val isGeneratingInsights: StateFlow<Boolean> = _isGeneratingInsights.asStateFlow()

    fun syncFitbit() {
        viewModelScope.launch {
            _isSyncingFitbit.value = true
            // Simulate networking delay
            kotlinx.coroutines.delay(2000)
            repository.syncDevice("Fitbit")
            _isSyncingFitbit.value = false
        }
    }

    fun syncAppleWatch() {
        viewModelScope.launch {
            _isSyncingAppleWatch.value = true
            // Simulate networking delay
            kotlinx.coroutines.delay(2000)
            repository.syncDevice("Apple Watch")
            _isSyncingAppleWatch.value = false
        }
    }

    fun addHeartRate(bpm: Int, activityType: String) {
        viewModelScope.launch {
            repository.addHeartRate(bpm, activityType, "Manual")
        }
    }

    fun addSleep(day: String, deep: Int, light: Int, rem: Int, awake: Int) {
        viewModelScope.launch {
            repository.addSleep(day, deep, light, rem, awake, "Manual")
        }
    }

    fun generateInsights() {
        viewModelScope.launch {
            _isGeneratingInsights.value = true
            // Allow simulated progress spinners to show
            kotlinx.coroutines.delay(1000)
            repository.generateWeeklyInsight()
            _isGeneratingInsights.value = false
        }
    }

    fun resetData() {
        viewModelScope.launch {
            repository.clearAllData()
        }
    }

    // Factory pattern for ViewModel injection
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HealthViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return HealthViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
