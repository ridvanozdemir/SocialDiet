package com.ridvanozdemir.socialdiet.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseUser
import com.ridvanozdemir.socialdiet.auth.GoogleCredentialHelper
import com.ridvanozdemir.socialdiet.data.FirebaseRepository
import com.ridvanozdemir.socialdiet.data.model.UserProfile
import com.ridvanozdemir.socialdiet.ui.screens.AuthScreen
import com.ridvanozdemir.socialdiet.ui.screens.EmailVerificationScreen
import com.ridvanozdemir.socialdiet.ui.screens.FriendsScreen
import com.ridvanozdemir.socialdiet.ui.screens.HomeScreen
import com.ridvanozdemir.socialdiet.ui.screens.LeaderboardScreen
import com.ridvanozdemir.socialdiet.ui.screens.MealScreen
import com.ridvanozdemir.socialdiet.ui.screens.ProfileScreen
import com.ridvanozdemir.socialdiet.ui.screens.ProfileSetupScreen
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String)

@Composable
fun SocialDietApp() {
    val repository = remember { FirebaseRepository() }
    var currentUser by remember { mutableStateOf<FirebaseUser?>(null) }
    var authResolved by remember { mutableStateOf(false) }

    DisposableEffect(repository) {
        val listener = repository.addAuthStateListener { user ->
            currentUser = user
            authResolved = true
        }
        onDispose { repository.removeAuthStateListener(listener) }
    }

    if (!authResolved) {
        LoadingScreen()
        return
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(repository = repository)
        return
    }

    if (!user.isEmailVerified) {
        EmailVerificationScreen(
            repository = repository,
            user = user,
            onUserRefreshed = { refreshed -> currentUser = refreshed }
        )
        return
    }

    ProfileGate(repository = repository, user = user)
}

@Composable
private fun ProfileGate(repository: FirebaseRepository, user: FirebaseUser) {
    var profile by remember(user.uid) { mutableStateOf<UserProfile?>(null) }
    var loaded by remember(user.uid) { mutableStateOf(false) }
    var error by remember(user.uid) { mutableStateOf<String?>(null) }

    DisposableEffect(user.uid) {
        val registration = repository.observeProfile(
            userId = user.uid,
            onProfile = {
                profile = it
                loaded = true
            },
            onError = {
                error = it.message ?: "Profil yüklenemedi."
                loaded = true
            }
        )
        onDispose { registration.remove() }
    }

    if (!loaded) {
        LoadingScreen()
        return
    }

    error?.let {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(it)
        }
        return
    }

    val activeProfile = profile
    if (activeProfile == null) {
        ProfileSetupScreen(repository = repository)
        return
    }

    LaunchedEffect(
        activeProfile.uid,
        activeProfile.username,
        activeProfile.currentWeightKg,
        activeProfile.programCompleted
    ) {
        repository.ensurePublicProfile(activeProfile)
    }

    MainApp(repository = repository, userId = user.uid)
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MainApp(repository: FirebaseRepository, userId: String) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
            composable("home") {
                HomeScreen(repository = repository, userId = userId)
            }
            composable("friends") {
                FriendsScreen(repository = repository, userId = userId)
            }
            composable("meal") {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "⚠ AI ile kalori tahmini test aşamasındadır. Sonuçları kaydetmeden önce porsiyon ve kalori değerlerini kontrol edin.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        MealScreen(repository = repository, userId = userId)
                    }
                }
            }
            composable("leaderboard") {
                LeaderboardScreen(repository = repository, userId = userId)
            }
            composable("profile") {
                ProfileScreen(
                    repository = repository,
                    userId = userId,
                    onSignOut = {
                        scope.launch {
                            runCatching { GoogleCredentialHelper.clearCredentialState(context) }
                            repository.signOut()
                        }
                    }
                )
            }
        }
    }
}
