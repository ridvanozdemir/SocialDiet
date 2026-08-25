package com.ridvanozdemir.socialdiet.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ridvanozdemir.socialdiet.data.FirebaseRepository
import com.ridvanozdemir.socialdiet.ui.screens.AuthScreen
import com.ridvanozdemir.socialdiet.ui.screens.FriendsScreen
import com.ridvanozdemir.socialdiet.ui.screens.HomeScreen
import com.ridvanozdemir.socialdiet.ui.screens.LeaderboardScreen
import com.ridvanozdemir.socialdiet.ui.screens.MealScreen
import com.ridvanozdemir.socialdiet.ui.screens.ProfileScreen

private data class Tab(val route: String, val label: String)

@Composable
fun SocialDietApp() {
    val repository = remember { FirebaseRepository() }
    var userId by remember { mutableStateOf(repository.currentUserId) }

    DisposableEffect(repository) {
        val listener = repository.addAuthStateListener { user ->
            userId = user?.uid
        }
        onDispose {
            repository.removeAuthStateListener(listener)
        }
    }

    val activeUserId = userId
    if (activeUserId == null) {
        AuthScreen(repository = repository)
        return
    }

    MainApp(repository = repository, userId = activeUserId)
}

@Composable
private fun MainApp(repository: FirebaseRepository, userId: String) {
    val navController = rememberNavController()
    val tabs = listOf(
        Tab("home", "Bugün"),
        Tab("friends", "Arkadaşlar"),
        Tab("meal", "+ Öğün"),
        Tab("leaderboard", "Lig"),
        Tab("profile", "Profil")
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val current = backStackEntry?.destination
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(tab.label.take(1)) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") { HomeScreen() }
            composable("friends") { FriendsScreen() }
            composable("meal") { MealScreen() }
            composable("leaderboard") { LeaderboardScreen() }
            composable("profile") {
                ProfileScreen(
                    repository = repository,
                    userId = userId,
                    onSignOut = repository::signOut
                )
            }
        }
    }
}
