package com.ridvanozdemir.socialdiet.ui.screens

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun MealScreen() = ScreenShell("Öğün Ekle") {
    InfoCard("1", "Kameradan çek veya galeriden fotoğraf seç.")
    InfoCard("2", "AI yemek türü ve tahmini kaloriyi çıkaracak.")
    InfoCard("3", "Kaydetmeden önce kullanıcı değeri düzeltebilecek.")
    Button(onClick = { /* CameraX / Photo Picker next milestone */ }) {
        Text("Fotoğraf Seç")
    }
}
