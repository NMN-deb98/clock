package com.example.alarmclock

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.alarmclock.databinding.ItemAlarmBinding
import java.util.Calendar
import java.util.Locale

class AlarmAdapter(
    private val onItemClick: (Alarm) -> Unit,
    private val onToggle: (Alarm, Boolean) -> Unit,
    private val onDelete: (Alarm) -> Unit
) : ListAdapter<Alarm, AlarmAdapter.AlarmViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val binding = ItemAlarmBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AlarmViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AlarmViewHolder(private val binding: ItemAlarmBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(alarm: Alarm) {
            binding.textTime.text = String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute)
            binding.textDays.text = formatDays(alarm.dayList())
            binding.textLabel.text = alarm.label
            binding.textLabel.visibility = if (alarm.label.isBlank())
                android.view.View.GONE else android.view.View.VISIBLE

            binding.switchEnabled.setOnCheckedChangeListener(null)
            binding.switchEnabled.isChecked = alarm.enabled
            binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggle(alarm, isChecked)
            }

            binding.root.setOnClickListener { onItemClick(alarm) }
            binding.buttonDelete.setOnClickListener { onDelete(alarm) }
        }

        private fun formatDays(days: List<Int>): String {
            if (days.isEmpty()) return binding.root.context.getString(R.string.one_time_alarm)
            val names = mapOf(
                Calendar.SATURDAY to "ش", Calendar.SUNDAY to "ی", Calendar.MONDAY to "د",
                Calendar.TUESDAY to "س", Calendar.WEDNESDAY to "چ", Calendar.THURSDAY to "پ",
                Calendar.FRIDAY to "ج"
            )
            val order = listOf(
                Calendar.SATURDAY, Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
                Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY
            )
            return order.filter { days.contains(it) }.joinToString(" ") { names[it] ?: "" }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Alarm>() {
        override fun areItemsTheSame(oldItem: Alarm, newItem: Alarm) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Alarm, newItem: Alarm) = oldItem == newItem
    }
}
