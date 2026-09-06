from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


repo = Path('.')

firebase_path = repo / 'app/src/main/java/com/ridvanozdemir/socialdiet/data/FirebaseRepository.kt'
firebase = firebase_path.read_text()

firebase = replace_once(
    firebase,
    '''        if (confirmedCalories !in 0..10000 || estimatedMassGrams !in 1.0..5000.0) {
            onResult(Result.failure(IllegalArgumentException("Öğün değerleri geçersiz.")))
            return
        }
''',
    '''        val manualEntry = calorieSource == "manual"
        val massIsValid = if (manualEntry) {
            estimatedMassGrams == 0.0
        } else {
            estimatedMassGrams in 1.0..5000.0
        }
        if (confirmedCalories !in 0..10000 || !massIsValid) {
            onResult(Result.failure(IllegalArgumentException("Öğün değerleri geçersiz.")))
            return
        }
''',
    'meal validation'
)

firebase = replace_once(
    firebase,
    '''        ).addOnSuccessListener {
            loadTodaySummary(userId) { }
            onResult(Result.success(Unit))
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun searchUsers(query: String, onResult: (Result<List<SocialProfile>>) -> Unit) {
''',
    '''        ).addOnSuccessListener {
            loadTodaySummary(userId) { scoreResult ->
                scoreResult.onSuccess {
                    onResult(Result.success(Unit))
                }.onFailure { error ->
                    onResult(
                        Result.failure(
                            IllegalStateException(
                                "Öğün kaydedildi ancak lig puanı güncellenemedi. Ligi yenileyerek tekrar dene.",
                                error
                            )
                        )
                    )
                }
            }
        }.addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun saveManualMeal(
        userId: String,
        mealType: String,
        calories: Int,
        label: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        saveMeal(
            userId = userId,
            mealType = mealType,
            aiLabel = label.trim().ifBlank { "Manuel giriş" },
            aiConfidence = null,
            calorieSource = "manual",
            aiCalories = calories,
            confirmedCalories = calories,
            estimatedMassGrams = 0.0,
            fatGrams = 0.0,
            carbsGrams = 0.0,
            proteinGrams = 0.0,
            onResult = onResult
        )
    }

    fun searchUsers(query: String, onResult: (Result<List<SocialProfile>>) -> Unit) {
''',
    'saveMeal completion/manual insertion'
)

firebase = replace_once(
    firebase,
    '''        fun adherenceScore(total: Int, target: Int): Int {
            if (target <= 0) return 0
            if (total.toDouble() / target < 0.75) return 0
            val deviationPercent = abs(total - target).toDouble() / target * 100.0
            return (100.0 - deviationPercent).roundToInt().coerceIn(0, 100)
        }
''',
    '''        fun adherenceScore(total: Int, target: Int): Int {
            if (target <= 0) return 0
            val deviationPercent = abs(total - target).toDouble() / target * 100.0
            return (100.0 - deviationPercent).roundToInt().coerceIn(0, 100)
        }
''',
    'adherence score'
)
firebase_path.write_text(firebase)

meal_path = repo / 'app/src/main/java/com/ridvanozdemir/socialdiet/ui/screens/MealScreen.kt'
meal = meal_path.read_text()

meal = replace_once(
    meal,
    '''    var selectedMealType by remember { mutableStateOf("LUNCH") }
    var massText by remember { mutableStateOf("") }
    var caloriesText by remember { mutableStateOf("") }
''',
    '''    var selectedMealType by remember { mutableStateOf("LUNCH") }
    var massText by remember { mutableStateOf("") }
    var caloriesText by remember { mutableStateOf("") }
    var manualMode by remember { mutableStateOf(false) }
    var manualCaloriesText by remember { mutableStateOf("") }
    var manualMealName by remember { mutableStateOf("") }
''',
    'manual state'
)

meal = replace_once(
    meal,
    '''        Text("Öğün Ekle", style = MaterialTheme.typography.headlineMedium)

        InfoCard(
''',
    '''        Text("Öğün Ekle", style = MaterialTheme.typography.headlineMedium)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                modifier = Modifier.weight(1f),
                selected = !manualMode,
                onClick = {
                    manualMode = false
                    statusMessage = null
                },
                label = { Text("Fotoğraf ile") }
            )
            FilterChip(
                modifier = Modifier.weight(1f),
                selected = manualMode,
                onClick = {
                    manualMode = true
                    statusMessage = null
                },
                label = { Text("Manuel kalori") }
            )
        }

        if (!manualMode) {
        InfoCard(
''',
    'mode selector/start photo block'
)

meal = replace_once(
    meal,
    '''        statusMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        Text(
            "Not: Fotoğraftan kalori ve porsiyon hesabı yaklaşık bir AI tahminidir. " +
                "Kaydetmeden önce yemek türünü, porsiyonu ve kaloriyi düzeltebilirsin.",
            style = MaterialTheme.typography.bodySmall
        )
''',
    '''        } else {
            ManualMealCard(
                caloriesText = manualCaloriesText,
                onCaloriesChanged = { manualCaloriesText = it.filter(Char::isDigit) },
                mealName = manualMealName,
                onMealNameChanged = { manualMealName = it.take(60) },
                selectedMealType = selectedMealType,
                onMealTypeSelected = { selectedMealType = it },
                isSaving = isSaving,
                onSave = {
                    val calories = manualCaloriesText.toIntOrNull()
                    if (calories == null || calories !in 1..10000) {
                        statusMessage = "Kalori değerini 1-10000 kcal arasında gir."
                        return@ManualMealCard
                    }

                    isSaving = true
                    repository.saveManualMeal(
                        userId = userId,
                        mealType = selectedMealType,
                        calories = calories,
                        label = manualMealName
                    ) { result ->
                        isSaving = false
                        result.onSuccess {
                            manualCaloriesText = ""
                            manualMealName = ""
                            statusMessage = "Manuel öğün kaydedildi ve lig puanı güncellendi."
                        }.onFailure { error ->
                            statusMessage = error.message ?: "Manuel öğün kaydedilemedi."
                        }
                    }
                }
            )
        }

        statusMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        Text(
            if (manualMode) {
                "Manuel giriş, fotoğraf çekmeyi unuttuğun öğünleri günlük toplamına ve lig puanına ekler."
            } else {
                "Not: Fotoğraftan kalori ve porsiyon hesabı yaklaşık bir AI tahminidir. " +
                    "Kaydetmeden önce yemek türünü, porsiyonu ve kaloriyi düzeltebilirsin."
            },
            style = MaterialTheme.typography.bodySmall
        )
''',
    'manual block/end photo block'
)

meal = replace_once(
    meal,
    '''@Composable
private fun NutritionResultCard(
''',
    '''@Composable
private fun ManualMealCard(
    caloriesText: String,
    onCaloriesChanged: (String) -> Unit,
    mealName: String,
    onMealNameChanged: (String) -> Unit,
    selectedMealType: String,
    onMealTypeSelected: (String) -> Unit,
    isSaving: Boolean,
    onSave: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Manuel kalori girişi", style = MaterialTheme.typography.titleLarge)
            Text(
                "Fotoğraf çekmediğin bir öğünün yaklaşık kalorisini elle ekleyebilirsin.",
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = mealName,
                onValueChange = onMealNameChanged,
                label = { Text("Öğün adı (isteğe bağlı)") },
                singleLine = true
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = caloriesText,
                onValueChange = onCaloriesChanged,
                label = { Text("Toplam kalori (kcal)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            Text("Öğün türü", style = MaterialTheme.typography.titleSmall)
            MealTypeChips(selected = selectedMealType, onSelected = onMealTypeSelected)

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                onClick = onSave
            ) {
                if (isSaving) CircularProgressIndicator()
                else Text("Manuel Öğünü Kaydet")
            }
        }
    }
}

@Composable
private fun NutritionResultCard(
''',
    'manual card composable'
)
meal_path.write_text(meal)

league_path = repo / 'app/src/main/java/com/ridvanozdemir/socialdiet/ui/screens/LeaderboardScreen.kt'
league = league_path.read_text()
league = replace_once(
    league,
    '''            "Sıralama, kimin daha az yediğine göre değil herkesin kendi kalori hedefine uyumuna göre hesaplanır.",
''',
    '''            "Sıralama, herkesin kendi kalori hedefine ne kadar yaklaştığına göre 0-100 puanla hesaplanır. Hedefe yaklaştıkça puan artar, hedef aşılırsa tekrar düşer. Öğün kaydından sonra lig puanı güncellenir.",
''',
    'league explanation'
)
league_path.write_text(league)

gradle_path = repo / 'app/build.gradle.kts'
gradle = gradle_path.read_text()
gradle = replace_once(
    gradle,
    '        versionCode = 11\n        versionName = "0.3.8"',
    '        versionCode = 12\n        versionName = "0.3.9"',
    'version bump'
)
gradle_path.write_text(gradle)

release_path = repo / '.github/workflows/release-aab.yml'
release = release_path.read_text().replace('socialdiet-release-aab-v11', 'socialdiet-release-aab-v12')
release_path.write_text(release)
