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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ridvanozdemir.socialdiet.data.FirebaseRepository
import com.ridvanozdemir.socialdiet.data.RegistrationInput

@Composable
fun AuthScreen(repository: FirebaseRepository) {
    var registerMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var startWeight by remember { mutableStateOf("") }
    var targetWeight by remember { mutableStateOf("") }
    var calorieTarget by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun parseDouble(value: String): Double? = value.trim().replace(',', '.').toDoubleOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("SocialDiet", style = MaterialTheme.typography.headlineLarge)
        Text("Arkadaşlarınla hedefe ilerle", style = MaterialTheme.typography.bodyLarge)

        if (registerMode) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Kullanıcı adı") },
                supportingText = { Text("3-20 karakter: a-z, 0-9, . veya _") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
            )
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Ad / görünen isim") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-posta") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = if (registerMode) 8.dp else 24.dp)
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Şifre") },
            supportingText = if (registerMode) ({ Text("En az 6 karakter") }) else null,
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        if (registerMode) {
            OutlinedTextField(
                value = height,
                onValueChange = { height = it },
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
                onValueChange = { calorieTarget = it },
                label = { Text("Günlük kalori hedefi") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
        }

        Button(
            enabled = !loading,
            onClick = {
                errorMessage = null
                if (email.isBlank() || password.length < 6) {
                    errorMessage = "Geçerli bir e-posta ve en az 6 karakterlik şifre gir."
                    return@Button
                }

                loading = true
                if (registerMode) {
                    val parsedHeight = height.toIntOrNull()
                    val parsedStart = parseDouble(startWeight)
                    val parsedTarget = parseDouble(targetWeight)
                    val parsedCalorieTarget = calorieTarget.toIntOrNull()

                    if (
                        username.isBlank() || displayName.isBlank() ||
                        parsedHeight == null || parsedHeight <= 0 ||
                        parsedStart == null || parsedStart <= 0 ||
                        parsedTarget == null || parsedTarget <= 0 ||
                        parsedCalorieTarget == null || parsedCalorieTarget <= 0
                    ) {
                        loading = false
                        errorMessage = "Profil bilgilerini eksiksiz ve geçerli değerlerle doldur."
                        return@Button
                    }

                    repository.register(
                        RegistrationInput(
                            email = email,
                            password = password,
                            username = username,
                            displayName = displayName,
                            heightCm = parsedHeight,
                            startWeightKg = parsedStart,
                            targetWeightKg = parsedTarget,
                            dailyCalorieTarget = parsedCalorieTarget
                        )
                    ) { result ->
                        loading = false
                        result.exceptionOrNull()?.let { errorMessage = it.message ?: "Kayıt başarısız." }
                    }
                } else {
                    repository.signIn(email, password) { result ->
                        loading = false
                        result.exceptionOrNull()?.let { errorMessage = it.message ?: "Giriş başarısız." }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            } else {
                Text(if (registerMode) "Hesap Oluştur" else "Giriş Yap")
            }
        }

        TextButton(
            enabled = !loading,
            onClick = {
                registerMode = !registerMode
                errorMessage = null
            }
        ) {
            Text(if (registerMode) "Zaten hesabın var mı? Giriş yap" else "Hesabın yok mu? Kayıt ol")
        }
    }
}
