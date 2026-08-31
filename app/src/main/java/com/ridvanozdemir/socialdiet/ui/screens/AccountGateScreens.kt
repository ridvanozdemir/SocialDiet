package com.ridvanozdemir.socialdiet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseUser
import com.ridvanozdemir.socialdiet.data.FirebaseRepository
import com.ridvanozdemir.socialdiet.data.ProfileSetupInput

@Composable
fun EmailVerificationScreen(
    repository: FirebaseRepository,
    user: FirebaseUser,
    onUserRefreshed: (FirebaseUser) -> Unit
) {
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("E-postanı doğrula", style = MaterialTheme.typography.headlineMedium)
        Text(
            "${user.email.orEmpty()} adresine doğrulama bağlantısı gönderdik. Bağlantıya dokunduktan sonra aşağıdaki kontrol butonunu kullan.",
            modifier = Modifier.padding(top = 12.dp)
        )

        message?.let {
            Text(
                text = it,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            )
        }

        Button(
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            onClick = {
                loading = true
                message = null
                repository.reloadCurrentUser { result ->
                    loading = false
                    result.onSuccess { refreshed ->
                        if (refreshed.isEmailVerified) {
                            onUserRefreshed(refreshed)
                        } else {
                            message = "E-posta henüz doğrulanmamış görünüyor."
                            isError = true
                        }
                    }.onFailure {
                        message = it.message ?: "Doğrulama durumu kontrol edilemedi."
                        isError = true
                    }
                }
            }
        ) {
            if (loading) CircularProgressIndicator(strokeWidth = 2.dp)
            else Text("Doğruladım, Kontrol Et")
        }

        OutlinedButton(
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            onClick = {
                loading = true
                message = null
                repository.sendVerificationEmail { result ->
                    loading = false
                    if (result.isSuccess) {
                        message = "Doğrulama e-postası tekrar gönderildi."
                        isError = false
                    } else {
                        message = result.exceptionOrNull()?.message ?: "E-posta gönderilemedi."
                        isError = true
                    }
                }
            }
        ) {
            Text("Doğrulama E-postasını Yeniden Gönder")
        }

        OutlinedButton(
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            onClick = repository::signOut
        ) {
            Text("Farklı Hesapla Giriş Yap")
        }
    }
}

@Composable
fun ProfileSetupScreen(repository: FirebaseRepository) {
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf(repository.currentUserDisplayName.orEmpty()) }
    var height by remember { mutableStateOf("") }
    var startWeight by remember { mutableStateOf("") }
    var targetWeight by remember { mutableStateOf("") }
    var calorieTarget by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun decimal(value: String): Double? = value.trim().replace(',', '.').toDoubleOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Profilini tamamla", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Google hesabınla giriş yaptın. SocialDiet hedeflerini oluşturmak için birkaç bilgi daha gerekiyor.",
            modifier = Modifier.padding(top = 10.dp)
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Kullanıcı adı") },
            supportingText = { Text("3-20 karakter: a-z, 0-9, . veya _") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
        )
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Ad / görünen isim") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        OutlinedTextField(
            value = height,
            onValueChange = { height = it.filter(Char::isDigit) },
            label = { Text("Boy (cm)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        OutlinedTextField(
            value = startWeight,
            onValueChange = { startWeight = it },
            label = { Text("Başlangıç kilosu (kg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        OutlinedTextField(
            value = targetWeight,
            onValueChange = { targetWeight = it },
            label = { Text("Hedef kilo (kg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        OutlinedTextField(
            value = calorieTarget,
            onValueChange = { calorieTarget = it.filter(Char::isDigit) },
            label = { Text("Günlük kalori hedefi") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
        }

        Button(
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            onClick = {
                error = null
                val parsedHeight = height.toIntOrNull()
                val parsedStart = decimal(startWeight)
                val parsedTarget = decimal(targetWeight)
                val parsedCalories = calorieTarget.toIntOrNull()
                if (
                    username.isBlank() || displayName.isBlank() ||
                    parsedHeight == null || parsedHeight <= 0 ||
                    parsedStart == null || parsedStart <= 0 ||
                    parsedTarget == null || parsedTarget <= 0 ||
                    parsedCalories == null || parsedCalories <= 0
                ) {
                    error = "Tüm profil bilgilerini geçerli değerlerle doldur."
                    return@Button
                }

                loading = true
                repository.completeCurrentUserProfile(
                    ProfileSetupInput(
                        username = username,
                        displayName = displayName,
                        heightCm = parsedHeight,
                        startWeightKg = parsedStart,
                        targetWeightKg = parsedTarget,
                        dailyCalorieTarget = parsedCalories
                    )
                ) { result ->
                    loading = false
                    result.exceptionOrNull()?.let {
                        error = it.message ?: "Profil oluşturulamadı."
                    }
                }
            }
        ) {
            if (loading) CircularProgressIndicator(strokeWidth = 2.dp)
            else Text("Profili Tamamla")
        }

        OutlinedButton(
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            onClick = repository::signOut
        ) {
            Text("Çıkış Yap")
        }
    }
}
