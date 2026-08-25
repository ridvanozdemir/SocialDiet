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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import java.io.File

@Composable
fun MealScreen() {
    val context = LocalContext.current
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            statusMessage = "Galeriden fotoğraf seçildi."
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val uri = pendingCameraUri
        if (saved && uri != null) {
            selectedImageUri = uri
            statusMessage = "Fotoğraf çekildi."
        } else {
            statusMessage = "Fotoğraf çekimi iptal edildi."
        }
    }

    ScreenShell("Öğün Ekle") {
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
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            ) {
                Text("Galeriden Seç")
            }
        }

        selectedImageUri?.let { uri ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Fotoğraf hazır",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Bir sonraki adımda bu fotoğrafı AI ile analiz edip kalori tahmini oluşturacağız.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(
                            onClick = {
                                selectedImageUri = null
                                statusMessage = null
                            }
                        ) {
                            Text("Fotoğrafı Kaldır")
                        }
                    }
                }
            }
        }

        statusMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
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
