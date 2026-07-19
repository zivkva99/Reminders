package com.ziv.reminders.ui.exercise

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziv.reminders.R
import com.ziv.reminders.data.ALL_EXERCISE_KEYS
import com.ziv.reminders.data.EXERCISE_KEY_ARM_ROTATION
import com.ziv.reminders.data.EXERCISE_KEY_LATERAL_RAISE
import com.ziv.reminders.data.EXERCISE_KEY_PUSHUP
import com.ziv.reminders.data.EXERCISE_KEY_SITUP
import com.ziv.reminders.data.EXERCISE_SUB_COUNTER_DEFAULT

private val exerciseIcons = mapOf(
    EXERCISE_KEY_LATERAL_RAISE to R.drawable.exercise_weight_side,
    EXERCISE_KEY_ARM_ROTATION to R.drawable.exercise_weight_front,
    EXERCISE_KEY_SITUP to R.drawable.exercise_situp,
    EXERCISE_KEY_PUSHUP to R.drawable.exercise_pushup,
)

internal val exerciseLabels = mapOf(
    EXERCISE_KEY_LATERAL_RAISE to "Lateral Raise",
    EXERCISE_KEY_ARM_ROTATION to "Arm Rotation",
    EXERCISE_KEY_SITUP to "Sit-up",
    EXERCISE_KEY_PUSHUP to "Push-up",
)

@Composable
fun ExerciseCounterScreen(viewModel: ExerciseViewModel, onOpenStats: () -> Unit, onBack: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val uiState by viewModel.uiState.collectAsState()
    if (!uiState.isLoaded) return
    BackHandler(onBack = onBack)

    val goalReached = uiState.completed
    val countColor = if (goalReached) GoalGreen else MaterialTheme.colorScheme.onBackground

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 80.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onOpenStats) {
                Icon(imageVector = Icons.Default.DateRange, contentDescription = "Your Progress", tint = GoalGreen)
            }
        }

        Text("Today's Exercises", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text("${uiState.current}", fontSize = 96.sp, fontWeight = FontWeight.Bold, color = countColor)
        Text(
            text = if (goalReached) "Goal reached!" else "goal: ${uiState.goal}",
            style = MaterialTheme.typography.bodyLarge,
            color = if (goalReached) GoalGreen else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(48.dp))

        FloatingActionButton(
            onClick = { viewModel.increment() },
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.width(320.dp).height(80.dp),
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Increment", modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(32.dp))

        ALL_EXERCISE_KEYS.forEach { key ->
            ExerciseSubCounterRow(
                iconRes = exerciseIcons.getValue(key),
                label = exerciseLabels.getValue(key),
                count = uiState.subCounters[key] ?: EXERCISE_SUB_COUNTER_DEFAULT,
                onDecrement = { viewModel.adjustSubCounter(key, -1) },
                onIncrement = { viewModel.adjustSubCounter(key, 1) },
            )
        }
    }
}

@Composable
private fun ExerciseSubCounterRow(iconRes: Int, label: String, count: Int, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painter = painterResource(iconRes), contentDescription = label, modifier = Modifier.size(52.dp))
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onDecrement, modifier = Modifier.size(40.dp), shape = CircleShape, contentPadding = PaddingValues(0.dp)) {
            Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Text(text = count.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp), textAlign = TextAlign.Center)
        OutlinedButton(onClick = onIncrement, modifier = Modifier.size(40.dp), shape = CircleShape, contentPadding = PaddingValues(0.dp)) {
            Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}
