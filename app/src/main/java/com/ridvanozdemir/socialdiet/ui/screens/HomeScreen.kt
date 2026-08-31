package com.ridvanozdemir.socialdiet.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ridvanozdemir.socialdiet.data.FirebaseRepository
import com.ridvanozdemir.socialdiet.data.TodaySummary

@Composable
fun HomeScreen(repository: FirebaseRepository, userId: String) {
    var summary by remember { mutableStateOf<TodaySummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        loading = true
        error = null
        repository.loadTodaySummary(userId) { result ->
            loading = false
            result.onSuccess { summary = it }
                .onFailure { error = it.message ?: "Bugünün verileri yüklenemedi." }
        }
    }

    LaunchedEffect(userId) { refresh() }

    ScreenShell("Bugün") {
        if (loading && summary == null) {
            CircularProgressIndicator()
        }

        summary?.let { today ->
            val ratio = if (today.calorieTarget > 0) {
                (today.calorieTotal.toFloat() / today.calorieTarget).coerceIn(0f, 1f)
            } else 0f

            InfoCard(
                "Kalori",
                "${today.calorieTotal} / ${today.calorieTarget} kcal"
            )
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier.fillMaxWidth()
            )
            InfoCard(
                "Öğünler",
                "Kahvaltı ${today.breakfastCalories} • Öğle ${today.lunchCalories} • Akşam ${today.dinnerCalories} • Ara öğün ${today.snackCalories} kcal"
            )
            InfoCard(
                "Günlük hedefe uyum",
                "%${today.adherenceScore}"
            )
            Text(
                "Puan, en az yemeyi değil kendi günlük kalori hedefine ne kadar yakın kaldığını ölçer. Hedefin %75'inin altındaki günler 0 puan alır.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
            onClick = ::refresh
        ) {
            if (loading) CircularProgressIndicator(strokeWidth = 2.dp)
            else Text("Yenile")
        }
    }
}
