package com.ridvanozdemir.socialdiet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.ridvanozdemir.socialdiet.data.FriendRequestItem
import com.ridvanozdemir.socialdiet.data.FriendsSnapshot
import com.ridvanozdemir.socialdiet.data.SocialProfile

@Composable
fun FriendsScreen(repository: FirebaseRepository, userId: String) {
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SocialProfile>>(emptyList()) }
    var snapshot by remember { mutableStateOf(FriendsSnapshot()) }
    var selectedProfile by remember { mutableStateOf<SocialProfile?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }

    fun refresh() {
        loading = true
        repository.loadFriendsSnapshot(userId) { result ->
            loading = false
            result.onSuccess { snapshot = it }
                .onFailure {
                    message = it.message ?: "Arkadaş listesi yüklenemedi."
                    messageIsError = true
                }
        }
    }

    fun action(block: ((Result<Unit>) -> Unit) -> Unit, successText: String) {
        busy = true
        message = null
        block { result ->
            busy = false
            if (result.isSuccess) {
                message = successText
                messageIsError = false
                refresh()
            } else {
                message = result.exceptionOrNull()?.message ?: "İşlem tamamlanamadı."
                messageIsError = true
            }
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
        Text("Arkadaşlar", style = MaterialTheme.typography.headlineMedium)

        Text("Kullanıcı ara", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Kullanıcı adı") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            enabled = !busy && query.trim().length >= 2,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                busy = true
                message = null
                repository.searchUsers(query) { result ->
                    busy = false
                    result.onSuccess {
                        searchResults = it
                        if (it.isEmpty()) {
                            message = "Eşleşen kullanıcı bulunamadı."
                            messageIsError = false
                        }
                    }.onFailure {
                        message = it.message ?: "Arama yapılamadı."
                        messageIsError = true
                    }
                }
            }
        ) {
            if (busy) CircularProgressIndicator(strokeWidth = 2.dp)
            else Text("Ara")
        }

        searchResults.forEach { profile ->
            ProfileCard(profile = profile) {
                Button(
                    enabled = !busy,
                    onClick = {
                        action(
                            block = { callback -> repository.sendFriendRequest(profile.uid, callback) },
                            successText = "Arkadaşlık isteği gönderildi."
                        )
                    }
                ) { Text("Arkadaş Ekle") }
            }
        }

        message?.let {
            Text(
                it,
                color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }

        if (loading) CircularProgressIndicator()

        if (snapshot.incoming.isNotEmpty()) {
            Text("Gelen istekler", style = MaterialTheme.typography.titleMedium)
            snapshot.incoming.forEach { request ->
                RequestCard(
                    request = request,
                    positiveLabel = "Kabul Et",
                    negativeLabel = "Reddet",
                    enabled = !busy,
                    onPositive = {
                        action(
                            block = { callback -> repository.acceptFriendRequest(request.friendshipId, callback) },
                            successText = "Arkadaşlık isteği kabul edildi."
                        )
                    },
                    onNegative = {
                        action(
                            block = { callback -> repository.rejectFriendRequest(request.friendshipId, callback) },
                            successText = "Arkadaşlık isteği reddedildi."
                        )
                    }
                )
            }
        }

        if (snapshot.outgoing.isNotEmpty()) {
            Text("Gönderilen istekler", style = MaterialTheme.typography.titleMedium)
            snapshot.outgoing.forEach { request ->
                ProfileCard(profile = request.profile) {
                    Text("Yanıt bekleniyor", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Text("Arkadaşların", style = MaterialTheme.typography.titleMedium)
        if (!loading && snapshot.friends.isEmpty()) {
            Text("Henüz kabul edilmiş arkadaşın yok.")
        }
        snapshot.friends.forEach { friend ->
            ProfileCard(profile = friend) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !busy,
                        onClick = { selectedProfile = friend }
                    ) {
                        Text("Profili Gör")
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !busy,
                        onClick = {
                            action(
                                block = { callback -> repository.removeFriend(friend.uid, callback) },
                                successText = "Arkadaş kaldırıldı."
                            )
                            if (selectedProfile?.uid == friend.uid) selectedProfile = null
                        }
                    ) {
                        Text("Arkadaşı Sil")
                    }
                }
            }
        }

        selectedProfile?.let { friend ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Arkadaş profili", style = MaterialTheme.typography.titleLarge)
                    Text(friend.displayName.ifBlank { "SocialDiet kullanıcısı" })
                    Text("@${friend.username}")
                    Text("Hedef ilerlemesi: %${friend.progressPercent}")
                    if (friend.programCompleted) Text("Hedefini tamamladı 🏆")
                    Text(
                        "Gizlilik için arkadaş profilinde e-posta ve ham kilo değerleri gösterilmez.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { selectedProfile = null }
                    ) { Text("Kapat") }
                }
            }
        }

        OutlinedButton(
            enabled = !loading && !busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = ::refresh
        ) { Text("Arkadaş Listesini Yenile") }
    }
}

@Composable
private fun ProfileCard(
    profile: SocialProfile,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(profile.displayName.ifBlank { "SocialDiet kullanıcısı" }, style = MaterialTheme.typography.titleMedium)
            Text("@${profile.username}", style = MaterialTheme.typography.bodyMedium)
            Text("Hedef ilerlemesi: %${profile.progressPercent}", style = MaterialTheme.typography.bodySmall)
            content()
        }
    }
}

@Composable
private fun RequestCard(
    request: FriendRequestItem,
    positiveLabel: String,
    negativeLabel: String,
    enabled: Boolean,
    onPositive: () -> Unit,
    onNegative: () -> Unit
) {
    ProfileCard(profile = request.profile) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = enabled,
                onClick = onPositive
            ) { Text(positiveLabel) }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = enabled,
                onClick = onNegative
            ) { Text(negativeLabel) }
        }
    }
}
