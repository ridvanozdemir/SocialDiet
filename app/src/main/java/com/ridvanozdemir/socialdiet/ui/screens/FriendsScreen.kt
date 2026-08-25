package com.ridvanozdemir.socialdiet.ui.screens

import androidx.compose.runtime.Composable

@Composable
fun FriendsScreen() = ScreenShell("Arkadaşlar") {
    InfoCard("Arkadaş ekle", "Kullanıcı adıyla ara ve arkadaşlık isteği gönder.")
    InfoCard("Ahmet", "Bugün: 1.760 kcal")
    InfoCard("Selin", "Bugün: 1.640 kcal")
}
