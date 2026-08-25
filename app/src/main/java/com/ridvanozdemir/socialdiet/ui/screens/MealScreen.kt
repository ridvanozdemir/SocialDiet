package com.ridvanozdemir.socialdiet.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.ridvanozdemir.socialdiet.data.FirebaseRepository
import com.ridvanozdemir.socialdiet.data.ai.FoodNutritionAnalyzer
import com.ridvanozdemir.socialdiet.data.ai.NutritionPrediction
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun MealScreen(
    repository: FirebaseRepository,
    userId: String
) {
    val context = LocalContext.current
    val analyzer = remember { FoodNutritionAnalyzer(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var prediction by remember { mutableStateOf<NutritionPrediction?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var selectedMealType by remember { mutableStateOf("LUNCH") }
    var massText by remember { mutableStateOf("") }
    var caloriesText by remember { mutableStateOf("") }

    DisposableEffect(analyzer) {
        onDispose { analyzer.close() }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            prediction = null
            massText = ""
            caloriesText = ""
            statusMessage = "Galeriden fotoğraf seçildi."
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val uri = pendingCameraUri
        if (saved && uri != null) {
            selectedImageUri = uri
            prediction = null
            massText = ""
            caloriesText = ""
            statusMessage = "Fotoğraf çekildi."
        } else {
            statusMessage = "Fotoğraf çekimi iptal edildi."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Öğün Ekle", style = MaterialTheme.typography.headlineMedium)

        InfoCard(
            "Öğün fotoğrafı",
            "Kameradan yeni fotoğraf çek veya galeriden mevcut bir fotoğraf seç."
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    runCatching { createMealPhotoUri(context) }
                        .onSuccess { uri ->
                            pendingCameraUri = uri
                            cameraLauncher.launch(uri)
                        }
                        .onFailure {
                            statusMessage = "Kamera için geçici dosya oluşturulamadı."
                        }
                }
            ) {
                Text("Kameradan Çek")
            }

            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                }
            ) {
                Text("Galeriden Seç")
            }
        }

        selectedImageUri?.let { uri ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        factory = { imageContext ->
                            ImageView(imageContext).apply {
                                scaleType = ImageView.ScaleType.CENTER_CROP
                            }
                        },
                        update = { imageView ->
                            imageView.setImageURI(uri)
                        }
                    )

                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Fotoğraf hazır",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isAnalyzing,
                            onClick = {
                                isAnalyzing = true
                                prediction = null
                                statusMessage = "AI analizi yapılıyor..."

                                scope.launch {
                                    val result = runCatching {
                                        withContext(Dispatchers.Default) {
                                            analyzer.analyze(uri)
                                        }
                                    }

                                    result.onSuccess { value ->
                                        prediction = value
                                        massText = value.massGrams.roundToInt().toString()
                                        caloriesText = value.totalCalories.toString()
                                        statusMessage = "AI tahmini hazır."
                                    }.onFailure { error ->
                                        statusMessage =
                                            "AI analizi başarısız: ${error.message ?: "Bilinmeyen hata"}"
                                    }
                                    isAnalyzing = false
                                }
                            }
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator()
                            } else {
                                Text("AI ile Analiz Et")
                            }
                        }

                        TextButton(
                            onClick = {
                                selectedImageUri = null
                                prediction = null
                                massText = ""
                                caloriesText = ""
                                statusMessage = null
                            }
                        ) {
                            Text("Fotoğrafı Kaldır")
                        }
                    }
                }
            }
        }

        prediction?.let { ai ->
            NutritionResultCard(
                prediction = ai,
                massText = massText,
                onMassChanged = { massText = it.filterAllowedDecimal() },
                caloriesText = caloriesText,
                onCaloriesChanged = { caloriesText = it.filter(Char::isDigit) },
                selectedMealType = selectedMealType,
                onMealTypeSelected = { selectedMealType = it },
                isSaving = isSaving,
                onSave = {
                    val mass = massText.toFloatOrNull()
                    val confirmedCalories = caloriesText.toIntOrNull()

                    if (mass == null || mass !in 1f..5000f) {
                        statusMessage = "Porsiyon gramını 1-5000 g arasında gir."
                        return@NutritionResultCard
                    }
                    if (confirmedCalories == null || confirmedCalories !in 0..10000) {
                        statusMessage = "Kalori değerini 0-10000 kcal arasında gir."
                        return@NutritionResultCard
                    }

                    val aiCalories = ai.totalCalories
                    isSaving = true
                    repository.saveMeal(
                        userId = userId,
                        mealType = selectedMealType,
                        aiCalories = aiCalories,
                        confirmedCalories = confirmedCalories,
                        estimatedMassGrams = mass.toDouble(),
                        fatGrams = ai.fatGramsFor(mass).toDouble(),
                        carbsGrams = ai.carbsGramsFor(mass).toDouble(),
                        proteinGrams = ai.proteinGramsFor(mass).toDouble()
                    ) { result ->
                        isSaving = false
                        result.onSuccess {
                            selectedImageUri = null
                            prediction = null
                            massText = ""
                            caloriesText = ""
                            statusMessage = "Öğün kaydedildi."
                        }.onFailure { error ->
                            statusMessage =
                                "Öğün kaydedilemedi: ${error.message ?: "Bilinmeyen hata"}"
                        }
                    }
                }
            )
        }

        statusMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        Text(
            "Not: Fotoğraftan kalori ve porsiyon hesabı yaklaşık bir AI tahminidir. " +
                "Kaydetmeden önce porsiyon ve kaloriyi düzeltebilirsin.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun NutritionResultCard(
    prediction: NutritionPrediction,
    massText: String,
    onMassChanged: (String) -> Unit,
    caloriesText: String,
    onCaloriesChanged: (String) -> Unit,
    selectedMealType: String,
    onMealTypeSelected: (String) -> Unit,
    isSaving: Boolean,
    onSave: () -> Unit
) {
    val mass = massText.toFloatOrNull() ?: prediction.massGrams

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("AI tahmini", style = MaterialTheme.typography.titleLarge)

            Text(
                "Model: ${format1(prediction.caloriesPer100g)} kcal / 100 g"
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = massText,
                onValueChange = onMassChanged,
                label = { Text("Tahmini porsiyon (g)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = caloriesText,
                onValueChange = onCaloriesChanged,
                label = { Text("Toplam kalori (kcal)") },
                supportingText = { Text("AI değerini gerekirse düzelt.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            Text(
                "Makrolar: Yağ ${format1(prediction.fatGramsFor(mass))} g • " +
                    "Karbonhidrat ${format1(prediction.carbsGramsFor(mass))} g • " +
                    "Protein ${format1(prediction.proteinGramsFor(mass))} g"
            )

            Text("Öğün türü", style = MaterialTheme.typography.titleSmall)
            MealTypeChips(
                selected = selectedMealType,
                onSelected = onMealTypeSelected
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                onClick = onSave
            ) {
                if (isSaving) {
                    CircularProgressIndicator()
                } else {
                    Text("Öğünü Kaydet")
                }
            }
        }
    }
}

@Composable
private fun MealTypeChips(
    selected: String,
    onSelected: (String) -> Unit
) {
    val mealTypes = listOf(
        "BREAKFAST" to "Kahvaltı",
        "LUNCH" to "Öğle",
        "DINNER" to "Akşam",
        "SNACK" to "Ara Öğün"
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        mealTypes.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { (value, label) ->
                    FilterChip(
                        modifier = Modifier.weight(1f),
                        selected = selected == value,
                        onClick = { onSelected(value) },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}

private fun createMealPhotoUri(context: Context): Uri {
    val imageDirectory = File(context.cacheDir, "meal_images").apply { mkdirs() }
    val imageFile = File.createTempFile("meal_", ".jpg", imageDirectory)
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        imageFile
    )
}

private fun String.filterAllowedDecimal(): String {
    var dotSeen = false
    return buildString {
        this@filterAllowedDecimal.forEach { ch ->
            when {
                ch.isDigit() -> append(ch)
                (ch == '.' || ch == ',') && !dotSeen -> {
                    append('.')
                    dotSeen = true
                }
            }
        }
    }
}

private fun format1(value: Float): String =
    String.format(Locale.getDefault(), "%.1f", value)
