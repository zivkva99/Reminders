package com.ziv.reminders.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ziv.reminders.ui.exercise.HeatmapHit
import com.ziv.reminders.ui.exercise.HeatmapPending
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Shared across Exercise/Reading/Tanakh's Activity sections (Task 6) — extracted from
 * ExerciseStatsScreen.kt's original private HeatmapGrid, with the day-alignment/status math
 * moved into the pure, unit-tested buildHeatmapDays.
 *
 * **Corrected during /autoplan design review — deliberately NOT a `LazyVerticalGrid`.** The
 * original extraction kept the source composable's `LazyVerticalGrid`, but Task 6 nests this
 * composable (three times — once per habit section) inside `ActivityScreen`'s outer
 * `Column(Modifier.verticalScroll(...))`. A `LazyVerticalGrid` given no explicit height,
 * placed inside a `verticalScroll` parent, crashes at runtime ("Vertically scrollable
 * component was measured with an infinite height constraints") — a well-known Compose nested-
 * scroll failure mode. `ExerciseStatsScreen.kt`'s original standalone screen never hit this
 * because its outer `Column` had no `verticalScroll` of its own (the single grid was the only
 * scrollable element). Fix: render as a plain, eager `Column` of `Row`s (7 cells per row) —
 * the heatmap's dataset is small and fixed-size (~53 weeks × 7 days year-round), so eager
 * rendering costs nothing meaningful, and a plain Column/Row nests inside any parent scroll
 * container with zero risk, unlike any `Lazy*` composable. This also avoids the awkward
 * scroll-within-scroll UX a fixed-height workaround would have introduced.
 */
@Composable
fun HeatmapGrid(dates: Set<LocalDate>, today: LocalDate, onDayClick: (LocalDate) -> Unit) {
    val days = remember(dates, today) { buildHeatmapDays(dates, today) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        days.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    val color = when (day.status) {
                        HeatmapDayStatus.PENDING -> HeatmapPending
                        HeatmapDayStatus.HIT -> HeatmapHit
                        HeatmapDayStatus.MISS -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val description = "${day.date.format(DateTimeFormatter.ISO_LOCAL_DATE)}: " + when (day.status) {
                        HeatmapDayStatus.PENDING -> "not yet done"
                        HeatmapDayStatus.HIT -> "goal hit"
                        HeatmapDayStatus.MISS -> "missed"
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(1.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(color)
                            .clickable { onDayClick(day.date) }
                            .semantics { contentDescription = description }
                    ) {}
                }
            }
        }
    }
}
