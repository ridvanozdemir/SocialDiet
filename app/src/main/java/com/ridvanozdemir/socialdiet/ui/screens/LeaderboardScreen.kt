package com.ridvanozdemir.socialdiet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import com.ridvanozdemir.socialdiet.data.LeaderboardEntry

@Composable
fun LeaderboardScreen(repository: FirebaseRepository, userId: String) {
    var entries by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        loading = true
        error = null
        repository.loadLeaderboard(userId) { result ->
            loading = false
            result.onSuccess { entries = it }
                .onFailure { error = it.message ?: "Lig bilgileri yüklenemedi." }
        }
    }

    LaunchedEffect(userId) { refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Lig", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Sıralama, kimin daha az yediğine göre değil herkesin kendi kalori hedefine uyumuna göre hesaplanır.",
            style = MaterialTheme.typography.bodyMedium
        )

        if (loading && entries.isEmpty()) CircularProgressIndicator()

        if (entries.isNotEmpty()) {
            Text("Bugünün sıralaması", style = MaterialTheme.typography.titleLarge)
            entries.sortedWith(
                compareByDescending<LeaderboardEntry> { it.dailyScore }
                    .thenByDescending { it.weeklyScore }
            ).forEachIndexed { index, entry ->
                LeaderboardCard(index + 1, entry, entry.dailyScore, "günlük")
            }

            Text("Haftalık lig", style = MaterialTheme.typography.titleLarge)
            entries.forEachIndexed { index, entry ->
                LeaderboardCard(index + 1, entry, entry.weeklyScore, "7 günlük ortalama")
            }
        }

        if (!loading && entries.size == 1) {
            Text(
                "Arkadaş ekledikçe ligde onların hedefe uyum puanlarını da göreceksin.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
            onClick = ::refresh
        ) {
            if (loading) CircularProgressIndicator(strokeWidth = 2.dp)
            else Text("Ligi Yenile")
        }
    }
}

@Composable
private fun LeaderboardCard(
    rank: Int,
    entry: LeaderboardEntry,
    score: Int,
    scoreLabel: String
) {
    val medal = when (rank) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "$rank."
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                "$medal ${entry.displayName.ifBlank { entry.username }}${if (entry.isCurrentUser) " • Sen" else ""}",
                style = MaterialTheme.typography.titleMedium
            )
            Text("@${entry.username}")
            Text("%$score hedef uyumu • $scoreLabel")
        }
    }
}
