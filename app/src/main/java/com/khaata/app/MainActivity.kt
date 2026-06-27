package com.khaata.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.khaata.app.data.model.monthLabel
import com.khaata.app.data.repository.FinanceRepository
import com.khaata.app.ui.screens.AddEntryScreen
import com.khaata.app.ui.screens.AuthScreen
import com.khaata.app.ui.screens.DashboardScreen
import com.khaata.app.ui.screens.GoalsScreen
import com.khaata.app.ui.screens.HistoryScreen
import com.khaata.app.ui.theme.Gold
import com.khaata.app.ui.theme.Ink
import com.khaata.app.ui.theme.KhaataTheme
import com.khaata.app.ui.theme.MutedOnInk
import com.khaata.app.ui.theme.NavySoft
import com.khaata.app.ui.theme.Paper
import com.khaata.app.viewmodel.FinanceViewModel
import com.khaata.app.viewmodel.FinanceViewModelFactory

enum class KhaataTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Filled.Home),
    ENTRY("Add Entry", Icons.Filled.Add),
    GOALS("Goals", Icons.Filled.Flag),
    HISTORY("History", Icons.Filled.History),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KhaataTheme {
                // null = "not checked yet", "" is never a real value here \u2014
                // we use a separate flag so we don't flash the login screen
                // for a split second while Firebase restores a saved session.
                var uid by remember { mutableStateOf<String?>(null) }
                var checkedSession by remember { mutableStateOf(false) }

                DisposableEffect(Unit) {
                    val auth = FirebaseAuth.getInstance()
                    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                        uid = firebaseAuth.currentUser?.uid
                        checkedSession = true
                    }
                    auth.addAuthStateListener(listener)
                    onDispose { auth.removeAuthStateListener(listener) }
                }

                when {
                    !checkedSession -> LoadingScreen()
                    uid == null -> AuthScreen()
                    else -> {
                        val viewModel: FinanceViewModel = viewModel(
                            factory = FinanceViewModelFactory(FinanceRepository(uid!!))
                        )
                        KhaataApp(viewModel, onSignOut = { FirebaseAuth.getInstance().signOut() })
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Gold)
            Text("Opening your ledger\u2026", color = Paper, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KhaataApp(viewModel: FinanceViewModel, onSignOut: () -> Unit) {
    var activeTab by remember { mutableStateOf(KhaataTab.DASHBOARD) }
    val viewedMonthKey by viewModel.viewedMonthKey.collectAsState()
    val showMonthNav = activeTab == KhaataTab.DASHBOARD || activeTab == KhaataTab.ENTRY

    Scaffold(
        containerColor = Paper,
        topBar = {
            TopAppBar(
                title = { Text("Khaata", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink, titleContentColor = Paper),
                actions = {
                    if (showMonthNav) {
                        IconButton(onClick = { viewModel.goToMonth(-1) }) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month", tint = Paper)
                        }
                        Text(monthLabel(viewedMonthKey), color = Paper, modifier = Modifier.padding(horizontal = 2.dp))
                        IconButton(onClick = { viewModel.goToMonth(1) }) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Next month", tint = Paper)
                        }
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.Filled.Logout, contentDescription = "Sign out", tint = Paper)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Ink, contentColor = Paper) {
                KhaataTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Gold,
                            selectedTextColor = Gold,
                            unselectedIconColor = MutedOnInk,
                            unselectedTextColor = MutedOnInk,
                            indicatorColor = NavySoft
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (activeTab) {
                KhaataTab.DASHBOARD -> DashboardScreen(viewModel)
                KhaataTab.ENTRY -> AddEntryScreen(viewModel)
                KhaataTab.GOALS -> GoalsScreen(viewModel)
                KhaataTab.HISTORY -> HistoryScreen(viewModel) { key ->
                    viewModel.jumpToMonth(key)
                    activeTab = KhaataTab.DASHBOARD
                }
            }
        }
    }
}