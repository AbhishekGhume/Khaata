package com.khaata.app

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.khaata.app.data.model.currentMonthKey
import com.khaata.app.data.model.monthLabel
import com.khaata.app.data.model.BudgetStatus
import com.khaata.app.data.repository.FinanceRepository
import com.khaata.app.notifications.ReminderWorker
import com.khaata.app.notifications.EXTRA_OPEN_ADD_ENTRY
import com.khaata.app.notifications.EXTRA_OPEN_TAB
import com.khaata.app.notifications.ensureNotificationChannel
import com.khaata.app.onboarding.OnboardingScreen
import com.khaata.app.onboarding.hasTutorialBeenSeen
import com.khaata.app.onboarding.isOnboardingComplete
import com.khaata.app.onboarding.markTutorialSeen
import com.khaata.app.onboarding.resetAllTutorials
import com.khaata.app.security.isAppLockEnabled
import com.khaata.app.tutorial.TutorialContent
import com.khaata.app.tutorial.TutorialOverlay
import com.khaata.app.ui.screens.AddEntryScreen
import com.khaata.app.ui.screens.AnalyticsScreen
import com.khaata.app.ui.screens.AuthScreen
import com.khaata.app.ui.screens.DashboardScreen
import com.khaata.app.ui.screens.GoalsScreen
import com.khaata.app.ui.screens.HistoryScreen
import com.khaata.app.ui.screens.PeopleScreen
import com.khaata.app.ui.screens.BudgetsScreen
import com.khaata.app.ui.screens.CategoryManagementScreen
import com.khaata.app.ui.screens.ExportScreen
import com.khaata.app.ui.screens.RecurringExpensesScreen
import com.khaata.app.ui.screens.SearchScreen
import com.khaata.app.ui.screens.SecurityGateScreen
import com.khaata.app.ui.screens.NotificationSettingsScreen
import com.khaata.app.ui.theme.Gold
import com.khaata.app.ui.theme.GoldSoft
import com.khaata.app.ui.theme.Green
import com.khaata.app.ui.theme.Ink
import com.khaata.app.ui.theme.KhaataTheme
import com.khaata.app.ui.theme.Muted
import com.khaata.app.ui.theme.MutedOnInk
import com.khaata.app.ui.theme.NavySoft
import com.khaata.app.ui.theme.Paper
import com.khaata.app.ui.theme.PaperCard
import com.khaata.app.ui.theme.Rust
import com.khaata.app.viewmodel.FinanceViewModel
import com.khaata.app.viewmodel.FinanceViewModelFactory
import com.khaata.app.widget.AddEntryWidget
import com.khaata.app.widget.CategoryCache
import com.khaata.app.widget.TemplateCache
import androidx.glance.appwidget.updateAll
import java.util.concurrent.TimeUnit

enum class KhaataTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Filled.Home),
    ANALYTICS("Analytics", Icons.AutoMirrored.Filled.ShowChart),
    BUDGETS("Budgets", Icons.AutoMirrored.Filled.ListAlt),
    ENTRY("Add Entry", Icons.Filled.Add),
    GOALS("Goals", Icons.Filled.Flag),
    PEOPLE("People", Icons.Filled.People),
    SEARCH("Search", Icons.Filled.Search),
    HISTORY("History", Icons.Filled.History),
}

/** Full-screen destinations reached from the "More" sheet, layered over the tab content. */
enum class SubScreen { SETTINGS, RECURRING, CATEGORIES, EXPORT }

/** One entry in the in-app back stack: which tab was showing, and which sub-screen (if any) was layered over it. */
private data class NavState(val tab: KhaataTab, val sub: SubScreen?)

class MainActivity : FragmentActivity() {
    private var openEntryTabRequested by mutableStateOf(false)
    // Tab requested by an alert notification ("budgets" / "goals"), consumed once routed.
    private var openTabRequested by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)
        setContent {
            KhaataTheme {
                // null = "not checked yet", "" is never a real value here \u2014
                // we use a separate flag so we don't flash the login screen
                // for a split second while Firebase restores a saved session.
                var uid by remember { mutableStateOf<String?>(null) }
                var checkedSession by remember { mutableStateOf(false) }
                var unlocked by remember { mutableStateOf(false) }
                // null = the DataStore flag hasn't been read yet. Starting at `false`
                // rendered the first-run OnboardingScreen for a frame on every cold
                // start (the async read below resolves after the first composition),
                // flashing the wizard at returning users. Gate on null until it loads.
                var onboardingDone by remember { mutableStateOf<Boolean?>(null) }
                val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { }

                LaunchedEffect(Unit) {
                    ensureNotificationChannel(this@MainActivity)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                DisposableEffect(Unit) {
                    val auth = FirebaseAuth.getInstance()
                    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                        uid = firebaseAuth.currentUser?.uid
                        // With the app-open lock off (default), there's nothing to
                        // unlock — go straight in. With it on, re-arm the gate on
                        // every auth change so a fresh sign-in must unlock.
                        unlocked = !isAppLockEnabled(this@MainActivity)
                        checkedSession = true
                    }
                    auth.addAuthStateListener(listener)
                    onDispose { auth.removeAuthStateListener(listener) }
                }

                // Load the onboarding flag whenever we become unlocked for a signed-in
                // user — previously this only happened inside the gate's onUnlocked, so
                // skipping the gate must not skip loading it (else onboarding re-shows).
                LaunchedEffect(unlocked, uid) {
                    if (unlocked && uid != null) {
                        onboardingDone = isOnboardingComplete(this@MainActivity)
                    }
                }

                when {
                    !checkedSession -> LoadingScreen()
                    uid == null -> AuthScreen()
                    !unlocked -> SecurityGateScreen(
                        onUnlocked = { unlocked = true },
                        onSignOut = { FirebaseAuth.getInstance().signOut() }
                    )
                    // Onboarding flag still loading — hold on the loader rather than
                    // flashing the wizard for a frame.
                    onboardingDone == null -> LoadingScreen()
                    else -> {
                        val viewModel: FinanceViewModel = viewModel(
                            factory = FinanceViewModelFactory(FinanceRepository(uid!!))
                        )
                        if (onboardingDone == false) {
                            OnboardingScreen(
                                viewModel = viewModel,
                                onComplete = { onboardingDone = true }
                            )
                        } else {
                            LaunchedEffect(uid) {
                                val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                                    .build()
                                WorkManager.getInstance(this@MainActivity).enqueueUniquePeriodicWork(
                                    "khaata_daily_reminders",
                                    ExistingPeriodicWorkPolicy.UPDATE,
                                    request
                                )
                            }
                            KhaataApp(
                                viewModel,
                                openEntryTabRequested = openEntryTabRequested,
                                onOpenEntryTabHandled = { openEntryTabRequested = false },
                                openTabRequested = openTabRequested,
                                onOpenTabHandled = { openTabRequested = null },
                                onSignOut = {
                                    // The auth listener recomputes `unlocked` from the
                                    // lock preference on sign-out, so no manual reset
                                    // here (which would briefly flash the gate).
                                    FirebaseAuth.getInstance().signOut()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_ADD_ENTRY, false) == true) {
            openEntryTabRequested = true
        }
        intent?.getStringExtra(EXTRA_OPEN_TAB)?.let { openTabRequested = it }
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
fun KhaataApp(
    viewModel: FinanceViewModel,
    openEntryTabRequested: Boolean,
    onOpenEntryTabHandled: () -> Unit,
    openTabRequested: String?,
    onOpenTabHandled: () -> Unit,
    onSignOut: () -> Unit,
) {
    var activeTab by remember { mutableStateOf(KhaataTab.DASHBOARD) }
    var activeSubScreen by remember { mutableStateOf<SubScreen?>(null) }
    // In-app back stack. Every navigation pushes the screen we're leaving so the
    // system back button (and sub-screen "back" arrows) can retrace it, instead of
    // falling through to the OS and dropping the user out to their home screen.
    val backStack = remember { androidx.compose.runtime.mutableStateListOf<NavState>() }

    // The single entry point for all in-app navigation: records where we were,
    // then moves. `sub` defaults to null so switching tabs also clears any open
    // sub-screen (Recurring/Categories/Export/Settings) — that's what makes the
    // bottom nav work while a sub-screen is showing.
    fun navigateTo(tab: KhaataTab = activeTab, sub: SubScreen? = null) {
        if (tab == activeTab && sub == activeSubScreen) return
        backStack.add(NavState(activeTab, activeSubScreen))
        activeTab = tab
        activeSubScreen = sub
    }

    // Pop one step. Returns false when there's nothing left to pop (caller lets
    // the OS handle back, i.e. exit the app).
    fun goBack(): Boolean {
        val prev = backStack.removeLastOrNull() ?: return false
        activeTab = prev.tab
        activeSubScreen = prev.sub
        return true
    }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    val viewedMonthKey by viewModel.viewedMonthKey.collectAsState()
    val isViewingCurrentMonth = viewedMonthKey == currentMonthKey()
    val canGoToNextMonth = viewedMonthKey < currentMonthKey()
    val budgetProgress by viewModel.budgetProgress.collectAsState()
    val showMonthNav = activeSubScreen == null && (activeTab == KhaataTab.DASHBOARD || activeTab == KhaataTab.ENTRY || activeTab == KhaataTab.BUDGETS || activeTab == KhaataTab.ANALYTICS)
    val projectedRunoutCount = budgetProgress.count { it.projectedRunout }
    val overCount = budgetProgress.count { it.status == com.khaata.app.data.model.BudgetStatus.OVER }
    val stripColor = when {
        projectedRunoutCount > 0 || overCount > 0 -> com.khaata.app.ui.theme.Rust
        budgetProgress.any { it.status == com.khaata.app.data.model.BudgetStatus.WATCHING } -> Gold
        budgetProgress.isNotEmpty() -> com.khaata.app.ui.theme.Green
        else -> com.khaata.app.ui.theme.NavySoft
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val primaryTabs = remember {
        listOf(
            KhaataTab.DASHBOARD,
            KhaataTab.PEOPLE,
            KhaataTab.BUDGETS,
            KhaataTab.ENTRY,
            KhaataTab.GOALS
        )
    }
    var activeTutorialScreenId by remember { mutableStateOf<String?>(null) }
    var showMonthYearPicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Surface write failures and "deleted — Undo" prompts from the ViewModel.
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message.text,
                actionLabel = message.actionLabel,
                withDismissAction = message.actionLabel == null,
                // Material3 defaults to Indefinite whenever an actionLabel is set, so
                // "Undo" snackbars would otherwise linger until tapped. Give the user
                // ~10s to hit Undo, then let it auto-dismiss like the plain messages.
                duration = if (message.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) message.onAction?.invoke()
        }
    }

    LaunchedEffect(activeTab, activeSubScreen) {
        if (activeSubScreen == null) {
            val screenId = tutorialIdFor(activeTab)
            if (screenId != null && !hasTutorialBeenSeen(context, screenId)) {
                activeTutorialScreenId = screenId
            }
        }
    }

    LaunchedEffect(openEntryTabRequested) {
        if (openEntryTabRequested) {
            navigateTo(KhaataTab.ENTRY)
            onOpenEntryTabHandled()
        }
    }

    // Alert notifications (budget warning / goal milestone / udhaar nudge) land on their subject tab.
    LaunchedEffect(openTabRequested) {
        when (openTabRequested) {
            "budgets" -> navigateTo(KhaataTab.BUDGETS)
            "goals" -> navigateTo(KhaataTab.GOALS)
            "people" -> navigateTo(KhaataTab.PEOPLE)
        }
        if (openTabRequested != null) onOpenTabHandled()
    }

    // Mirror the live category list into the device-local cache the widget and
    // quick-add popup read (they can't hold a Firestore listener), refreshing the
    // widget whenever the list actually changed.
    LaunchedEffect(Unit) {
        viewModel.categories.collect { list ->
            if (CategoryCache.save(context, list)) {
                AddEntryWidget().updateAll(context)
            }
        }
    }

    // Same for quick-add templates — the popup shows them as one-tap chips.
    LaunchedEffect(Unit) {
        viewModel.templates.collect { list ->
            TemplateCache.save(context, list)
        }
    }

    // Intercept the system back button. A visible tutorial overlay swallows back
    // first (it has no dismiss button on the hardware key otherwise); after that
    // we retrace the in-app back stack. When the stack is empty and no overlay is
    // up, this handler is disabled so back reaches the OS and closes the app.
    BackHandler(enabled = activeTutorialScreenId != null || backStack.isNotEmpty()) {
        if (activeTutorialScreenId != null) {
            activeTutorialScreenId?.let { markTutorialSeen(context, it) }
            activeTutorialScreenId = null
        } else {
            goBack()
        }
    }

    Scaffold(
        containerColor = Paper,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "Khaata",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink, titleContentColor = Paper),
                    actions = {
                        IconButton(onClick = { navigateTo(KhaataTab.SEARCH) }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search all entries", tint = Paper)
                        }
                        IconButton(onClick = {
                            val screenId = tutorialIdFor(activeTab)
                            if (activeSubScreen == null && screenId != null) activeTutorialScreenId = screenId
                        }) {
                            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Show help for this screen", tint = Paper)
                        }
                        IconButton(onClick = { showMoreSheet = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Open more options", tint = Paper)
                        }
                    }
                )
                if (showMonthNav) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Ink)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(22.dp))
                                .background(NavySoft),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.goToMonth(-1) },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.Filled.ChevronLeft,
                                    contentDescription = "Previous month",
                                    tint = Paper,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { showMonthYearPicker = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .widthIn(min = 108.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.CalendarMonth,
                                    contentDescription = "Pick a month",
                                    tint = Gold,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    monthLabel(viewedMonthKey),
                                    color = Paper,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                enabled = canGoToNextMonth,
                                onClick = { viewModel.goToMonth(1) },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    contentDescription = "Next month",
                                    tint = if (canGoToNextMonth) Paper else MutedOnInk,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (!isViewingCurrentMonth) {
                            Spacer(modifier = Modifier.width(10.dp))
                            TextButton(onClick = { viewModel.jumpToMonth(currentMonthKey()) }) {
                                Text(
                                    "Today",
                                    color = Gold,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                if (showMonthYearPicker) {
                    MonthYearPickerDialog(
                        selectedMonthKey = viewedMonthKey,
                        onDismiss = { showMonthYearPicker = false },
                        onSelect = { key ->
                            viewModel.jumpToMonth(key)
                            showMonthYearPicker = false
                        }
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(stripColor)
                )
            }
        },
        floatingActionButton = {
            if (activeSubScreen == null && activeTab == KhaataTab.DASHBOARD) {
                FloatingActionButton(
                    onClick = { navigateTo(KhaataTab.ENTRY) },
                    containerColor = Gold,
                    contentColor = Ink
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add an entry")
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Ink, contentColor = Paper) {
                primaryTabs.forEach { tab ->
                    NavigationBarItem(
                        selected = activeSubScreen == null && activeTab == tab,
                        onClick = { navigateTo(tab) },
                        alwaysShowLabel = false,
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = {
                            Text(
                                tab.label,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
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
            if (activeSubScreen != null) {
                when (activeSubScreen) {
                    SubScreen.SETTINGS -> NotificationSettingsScreen(viewModel = viewModel, onBack = { goBack() })
                    SubScreen.RECURRING -> RecurringExpensesScreen(viewModel = viewModel, onBack = { goBack() })
                    SubScreen.CATEGORIES -> CategoryManagementScreen(viewModel = viewModel, onBack = { goBack() })
                    SubScreen.EXPORT -> ExportScreen(viewModel = viewModel, onBack = { goBack() })
                    null -> Unit
                }
            } else {
                when (activeTab) {
                    KhaataTab.DASHBOARD -> DashboardScreen(viewModel)
                    KhaataTab.ANALYTICS -> AnalyticsScreen(viewModel)
                    KhaataTab.BUDGETS -> BudgetsScreen(viewModel)
                    KhaataTab.ENTRY -> AddEntryScreen(viewModel)
                    KhaataTab.GOALS -> GoalsScreen(viewModel)
                    KhaataTab.PEOPLE -> PeopleScreen(viewModel)
                    KhaataTab.SEARCH -> SearchScreen(viewModel)
                    KhaataTab.HISTORY -> HistoryScreen(viewModel) { key ->
                        viewModel.jumpToMonth(key)
                        navigateTo(KhaataTab.DASHBOARD)
                    }
                }
            }
            // Tutorial overlay — shown on top of any screen, never blocks
            // navigation. Dismissed when the user taps "Got it" or "Skip".
            val currentTutorialId = activeTutorialScreenId
            if (currentTutorialId != null) {
                TutorialOverlay(
                    steps = TutorialContent.stepsFor(currentTutorialId),
                    onDismiss = {
                        markTutorialSeen(context, currentTutorialId)
                        activeTutorialScreenId = null
                    }
                )
            }
            if (showSignOutConfirm) {
                AlertDialog(
                    onDismissRequest = { showSignOutConfirm = false },
                    title = { Text("Sign out?") },
                    text = { Text("You will need to unlock again on next open.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showSignOutConfirm = false
                            onSignOut()
                        }) {
                            Text("Sign out")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSignOutConfirm = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
            if (showMoreSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showMoreSheet = false }
                ) {
                    Text(
                        "NAVIGATION",
                        color = Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                    )
                    ListItem(
                        headlineContent = { Text("Analytics") },
                        supportingContent = { Text("Trends, category shifts & savings rate") },
                        leadingContent = {
                            Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .run {
                                clickable {
                                    showMoreSheet = false
                                    navigateTo(KhaataTab.ANALYTICS)
                                }
                            }
                    )
                    ListItem(
                        headlineContent = { Text("History") },
                        leadingContent = {
                            Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .run {
                                clickable {
                                    showMoreSheet = false
                                    navigateTo(KhaataTab.HISTORY)
                                }
                            }
                    )
                    ListItem(
                        headlineContent = { Text("Recurring expenses") },
                        supportingContent = { Text("Rent, subscriptions & other fixed costs") },
                        leadingContent = {
                            Icon(Icons.Filled.Repeat, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .run {
                                clickable {
                                    showMoreSheet = false
                                    navigateTo(sub = SubScreen.RECURRING)
                                }
                            }
                    )
                    ListItem(
                        headlineContent = { Text("Manage categories") },
                        supportingContent = { Text("Add, rename, recolor & re-icon") },
                        leadingContent = {
                            Icon(Icons.Filled.Category, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .run {
                                clickable {
                                    showMoreSheet = false
                                    navigateTo(sub = SubScreen.CATEGORIES)
                                }
                            }
                    )
                    ListItem(
                        headlineContent = { Text("Export data") },
                        supportingContent = { Text("Download your ledger as CSV or PDF") },
                        leadingContent = {
                            Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .run {
                                clickable {
                                    showMoreSheet = false
                                    navigateTo(sub = SubScreen.EXPORT)
                                }
                            }
                    )
                    HorizontalDivider()
                    Text(
                        "APP",
                        color = Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                    )
                    ListItem(
                        headlineContent = { Text("Settings") },
                        leadingContent = {
                            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .run {
                                clickable {
                                    showMoreSheet = false
                                    navigateTo(sub = SubScreen.SETTINGS)
                                }
                            }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Reset tutorials") },
                        leadingContent = {
                            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        supportingContent = { Text("Show onboarding tips again") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .run {
                                clickable {
                                    resetAllTutorials(context)
                                    showMoreSheet = false
                                }
                            }
                    )
                    HorizontalDivider()
                    Text(
                        "SAFETY",
                        color = Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                    )
                    ListItem(
                        headlineContent = { Text("Sign out", color = Rust) },
                        supportingContent = { Text("Requires unlock on next open", color = Muted) },
                        leadingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Rust
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .run {
                                clickable {
                                    showMoreSheet = false
                                    showSignOutConfirm = true
                                }
                            }
                    )
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}

@Composable
private fun MonthYearPickerDialog(
    selectedMonthKey: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val selectedYearMonth = remember(selectedMonthKey) { YearMonth.parse(selectedMonthKey) }
    val currentYearMonth = remember { YearMonth.now() }
    var pickerYear by remember(selectedMonthKey) { mutableStateOf(selectedYearMonth.year) }
    val monthShortNames = remember {
        (1..12).map { java.time.Month.of(it).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = PaperCard
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Jump to month",
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Pick any month to view its ledger",
                    color = Muted,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(GoldSoft)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { pickerYear -= 1 }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous year", tint = Ink)
                    }
                    Text(
                        pickerYear.toString(),
                        color = Ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    IconButton(
                        enabled = pickerYear < currentYearMonth.year,
                        onClick = { pickerYear += 1 }
                    ) {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = "Next year",
                            tint = if (pickerYear < currentYearMonth.year) Ink else Muted
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in 0 until 4) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for (col in 0 until 3) {
                                val monthNum = row * 3 + col + 1
                                val candidate = YearMonth.of(pickerYear, monthNum)
                                val isFuture = candidate.isAfter(currentYearMonth)
                                val isSelected = candidate == selectedYearMonth
                                val isCurrent = candidate == currentYearMonth
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1.5f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            when {
                                                isSelected -> Gold
                                                isFuture -> PaperCard
                                                else -> GoldSoft.copy(alpha = 0.55f)
                                            }
                                        )
                                        .then(
                                            if (isFuture) Modifier else Modifier.clickable {
                                                onSelect(candidate.toString())
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            monthShortNames[monthNum - 1],
                                            color = when {
                                                isSelected -> Paper
                                                isFuture -> Muted.copy(alpha = 0.4f)
                                                else -> Ink
                                            },
                                            fontWeight = if (isSelected || isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp
                                        )
                                        if (isCurrent && !isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(top = 2.dp)
                                                    .size(4.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(Green)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Muted, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = { onSelect(currentYearMonth.toString()) }) {
                        Text("Today", color = Green, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** Maps a nav tab to the tutorial screen id, or null if that tab has no tutorial. */
private fun tutorialIdFor(tab: KhaataTab): String? = when (tab) {
    KhaataTab.DASHBOARD -> TutorialContent.DASHBOARD
    KhaataTab.ANALYTICS -> TutorialContent.ANALYTICS
    KhaataTab.BUDGETS   -> TutorialContent.BUDGETS
    KhaataTab.ENTRY     -> TutorialContent.ADD_ENTRY
    KhaataTab.GOALS     -> TutorialContent.GOALS
    KhaataTab.PEOPLE    -> TutorialContent.PEOPLE
    KhaataTab.SEARCH    -> TutorialContent.SEARCH
    KhaataTab.HISTORY   -> TutorialContent.HISTORY
}