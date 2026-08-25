package com.ridvanozdemir.socialdiet.ui.screens

import androidx.compose.runtime.Composable

@Composable
fun ProfileScreen() = ScreenShell("Profil") {
    InfoCard("Kilo hedefi", "Başlangıç 92 kg • Güncel 88 kg • Hedef 85 kg")
    InfoCard("İlerleme", "4 / 7 kg • %57 tamamlandı")
    InfoCard("Program", "Hedef kiloya ulaşıldığında tamamlandı olarak işaretlenecek.")
}
