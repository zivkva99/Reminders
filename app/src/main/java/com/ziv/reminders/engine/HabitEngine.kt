package com.ziv.reminders.engine

import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.HabitKind
import com.ziv.reminders.data.HabitStatus
import java.time.LocalDate

/**
 * Dispatches the two calls every kind can answer generically. Write actions (Counter's
 * increment, later Timer's start/stop, ScheduleCursor's markRead) deliberately stay on each
 * kind's own repository, not here — see this plan's Architecture section for why.
 */
class HabitEngine(private val counterRepository: CounterHabitRepository) {

    suspend fun todayStatus(instance: HabitInstance, today: LocalDate): HabitStatus =
        when (instance.kind) {
            HabitKind.COUNTER.name -> counterRepository.todayStatus(instance, today)
            else -> throw IllegalArgumentException("Unknown habit kind: ${instance.kind}")
        }

    suspend fun currentStreak(instance: HabitInstance, today: LocalDate): Int =
        when (instance.kind) {
            HabitKind.COUNTER.name -> counterRepository.currentStreak(instance, today)
            else -> throw IllegalArgumentException("Unknown habit kind: ${instance.kind}")
        }
}
