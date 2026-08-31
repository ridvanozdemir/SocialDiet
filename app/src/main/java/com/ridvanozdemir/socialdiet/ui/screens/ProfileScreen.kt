package com.ridvanozdemir.socialdiet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ridvanozdemir.socialdiet.auth.GoogleCredentialHelper
import com.ridvanozdemir.socialdiet.data.FirebaseRepository
import com.ridvanozdemir.socialdiet.data.model.UserProfile
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    repository: FirebaseRepository,
    userId: String,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var weightText by remember { mutableStateOf("") }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var changingPassword by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }

    val passwordAccount = repository.usesPasswordProvider()
    val googleAccount = repository.usesGoogleProvider()

    DisposableEffect(userId) {
        val registration = repository.observeProfile(
            userId = userId,
            onProfile = { profile = it },
            onError = {
                statusMessage = it.message ?: "Profil yüklenemedi."
                statusIsError = true
            }
        )
        onDispose { registration.remove() }
    }

    LaunchedEffect(profile?.currentWeightKg) {
        profile?.currentWeightKg?.let { weightText = it.toString() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Profil", style = MaterialTheme.typography.headlineMedium)

        val currentProfile = profile
        if (currentProfile == null) {
            InfoCard("Profil", "Profil bilgileri yükleniyor...")
        } else {
            InfoCard(
                "@${currentProfile.username}",
                currentProfile.displayName.ifBlank { "SocialDiet kullanıcısı" }
            )
            InfoCard(
                "Kilo hedefi",
                "Başlangıç ${formatKg(currentProfile.startWeightKg)} • Güncel ${formatKg(currentProfile.currentWeightKg)} • Hedef ${formatKg(currentProfile.targetWeightKg)}"
            )
            InfoCard(
                "İlerleme",
                "${FirebaseRepository.progressPercent(currentProfile.startWeightKg, currentProfile.currentWeightKg, currentProfile.targetWeightKg)}% tamamlandı${if (currentProfile.programCompleted) " • Hedef tamamlandı 🏆" else ""}"
            )
            InfoCard(
                "Günlük hedef",
                "${currentProfile.dailyCalorieTarget ?: 0} kcal"
            )
            InfoCard(
                "Hesap",
                "${repository.currentUserEmail.orEmpty()} • ${if (googleAccount && !passwordAccount) "Google ile giriş" else "E-posta doğrulandı"}"
            )

            OutlinedTextField(
                value = weightText,
                onValueChange = { weightText = it },
                label = { Text("Güncel kilo (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                enabled = !saving,
                onClick = {
                    val weight = weightText.trim().replace(',', '.').toDoubleOrNull()
                    if (weight == null || weight <= 0) {
                        statusMessage = "Geçerli bir kilo değeri gir."
                        statusIsError = true
                        return@Button
                    }
                    saving = true
                    statusMessage = null
                    repository.updateCurrentWeight(weight) { result ->
                        saving = false
                        if (result.isSuccess) {
                            statusMessage = "Kilo güncellendi."
                            statusIsError = false
                        } else {
                            statusMessage = result.exceptionOrNull()?.message ?: "Kilo güncellenemedi."
                            statusIsError = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (saving) "Kaydediliyor..." else "Kiloyu Güncelle")
            }
        }

        if (passwordAccount) {
            Text("Şifre değiştir", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = currentPassword,
                onValueChange = { currentPassword = it },
                label = { Text("Mevcut şifre") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("Yeni şifre") },
                supportingText = { Text("En az 6 karakter") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                enabled = !changingPassword,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    changingPassword = true
                    statusMessage = null
                    repository.changePassword(currentPassword, newPassword) { result ->
                        changingPassword = false
                        if (result.isSuccess) {
                            currentPassword = ""
                            newPassword = ""
                            statusMessage = "Şifre değiştirildi."
                            statusIsError = false
                        } else {
                            statusMessage = result.exceptionOrNull()?.message ?: "Şifre değiştirilemedi."
                            statusIsError = true
                        }
                    }
                }
            ) {
                if (changingPassword) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text("Şifreyi Değiştir")
            }
        }

        statusMessage?.let {
            Text(
                text = it,
                color = if (statusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }

        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Çıkış Yap")
        }

        Text("Hesap yönetimi", style = MaterialTheme.typography.titleMedium)
        Text(
            "Hesabını sildiğinde profilin, kilo geçmişin, öğünlerin, arkadaşlıkların ve lig verilerin kalıcı olarak silinir.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(
            enabled = !deleting,
            onClick = { showDeleteDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Hesabımı Sil")
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteDialog = false },
            title = { Text("Hesabı kalıcı olarak sil?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Bu işlem geri alınamaz.")
                    if (passwordAccount) {
                        OutlinedTextField(
                            value = deletePassword,
                            onValueChange = { deletePassword = it },
                            label = { Text("Mevcut şifre") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                    } else {
                        Text("Silme işleminden önce Google hesabınla yeniden doğrulama yapılacak.")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting && (!passwordAccount || deletePassword.isNotBlank()),
                    onClick = {
                        deleting = true
                        statusMessage = null
                        if (passwordAccount) {
                            repository.deleteAccount(
                                currentPassword = deletePassword,
                                googleIdToken = null
                            ) { result ->
                                deleting = false
                                if (result.isSuccess) {
                                    showDeleteDialog = false
                                } else {
                                    showDeleteDialog = false
                                    statusMessage = result.exceptionOrNull()?.message ?: "Hesap silinemedi."
                                    statusIsError = true
                                }
                            }
                        } else {
                            scope.launch {
                                val tokenResult = runCatching { GoogleCredentialHelper.requestIdToken(context) }
                                tokenResult.onSuccess { token ->
                                    repository.deleteAccount(
                                        currentPassword = null,
                                        googleIdToken = token
                                    ) { result ->
                                        deleting = false
                                        showDeleteDialog = false
                                        if (result.isFailure) {
                                            statusMessage = result.exceptionOrNull()?.message ?: "Hesap silinemedi."
                                            statusIsError = true
                                        }
                                    }
                                }.onFailure { error ->
                                    deleting = false
                                    showDeleteDialog = false
                                    statusMessage = error.message ?: "Google doğrulaması yapılamadı."
                                    statusIsError = true
                                }
                            }
                        }
                    }
                ) {
                    if (deleting) CircularProgressIndicator(strokeWidth = 2.dp)
                    else Text("Kalıcı Olarak Sil")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = { showDeleteDialog = false }
                ) { Text("Vazgeç") }
            }
        )
    }
}

private fun formatKg(value: Double?): String = value?.let { "%.1f kg".format(it) } ?: "-"
