package com.example.alarmclock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AlarmDatabase.getInstance(application).alarmDao()
    val allAlarms: LiveData<List<Alarm>> = dao.getAll()

    fun insert(alarm: Alarm, onInserted: (Alarm) -> Unit) {
        viewModelScope.launch {
            val id = dao.insert(alarm)
            onInserted(alarm.copy(id = id.toInt()))
        }
    }

    fun update(alarm: Alarm) {
        viewModelScope.launch { dao.update(alarm) }
    }

    fun delete(alarm: Alarm) {
        viewModelScope.launch { dao.delete(alarm) }
    }

    suspend fun getById(id: Int): Alarm? = dao.getById(id)
}
