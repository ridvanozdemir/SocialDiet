package com.ridvanozdemir.socialdiet.ui.screens

import androidx.compose.runtime.Composable

@Composable
fun HomeScreen() = ScreenShell("Bugün") {
    InfoCard("Kalori", "1.420 / 2.000 kcal")
    InfoCard("Öğünler", "Kahvaltı 350 • Öğle 620 • Ara öğün 450")
    InfoCard("Günlük durum", "Hedefe uyum puanın burada gösterilecek.")
}
