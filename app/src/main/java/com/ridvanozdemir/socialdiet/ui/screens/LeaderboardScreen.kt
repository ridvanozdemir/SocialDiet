package com.ridvanozdemir.socialdiet.ui.screens

import androidx.compose.runtime.Composable

@Composable
fun LeaderboardScreen() = ScreenShell("Lig") {
    InfoCard("🥇 Ahmet", "%97 hedef uyumu")
    InfoCard("🥈 Sen", "%94 hedef uyumu")
    InfoCard("🥉 Selin", "%92 hedef uyumu")
    InfoCard("Haftalık Lig", "7 günlük uyum puanlarının toplamı kullanılacak.")
}
