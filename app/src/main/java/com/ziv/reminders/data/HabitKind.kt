package com.ziv.reminders.data

/**
 * The extensibility primitive: adding a new instance of an existing kind needs only a
 * HabitInstance row (see HabitSeeding.kt), zero new Kotlin classes. A genuinely new kind still
 * needs a new enum case, HabitStatus variant, repository, and HabitEngine branch.
 */
enum class HabitKind {
    COUNTER,
    TIMER,
    SCHEDULE_CURSOR,
}
