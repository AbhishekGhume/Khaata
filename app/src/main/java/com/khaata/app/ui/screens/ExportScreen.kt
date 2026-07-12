package com.khaata.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khaata.app.data.model.monthKeyFromDate
import com.khaata.app.data.model.monthLabel
import com.khaata.app.data.model.todayStr
import com.khaata.app.ui.components.DatePickerField
import com.khaata.app.ui.theme.Gold
import com.khaata.app.ui.theme.Green
import com.khaata.app.ui.theme.Ink
import com.khaata.app.ui.theme.Muted
import com.khaata.app.ui.theme.Paper
import com.khaata.app.ui.theme.PaperCard
import com.khaata.app.ui.theme.PaperLine
import com.khaata.app.ui.theme.Rust
import com.khaata.app.util.Exporter
import com.khaata.app.viewmodel.FinanceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The slice of the ledger the user wants to export. */
private enum class ExportScope(val label: String) {
    MONTH("This month"),
    RANGE("Custom range"),
    ALL("All data")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(viewModel: FinanceViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allExpenses by viewModel.allExpenses.collectAsState()
    val allMonths by viewModel.allMonths.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val loading by viewModel.allExpensesLoading.collectAsState()
    val viewedMonthKey by viewModel.viewedMonthKey.collectAsState()

    var working by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var selectedScope by remember { mutableStateOf(ExportScope.MONTH) }
    var rangeStart by remember { mutableStateOf("") }
    var rangeEnd by remember { mutableStateOf(todayStr()) }

    // Restore: the picked file's text, held until the user confirms the overwrite.
    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }
    val restoreLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            working = true
            exportError = null
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }
                        .getOrNull()
                }
                working = false
                if (text.isNullOrBlank()) exportError = "Couldn't read that file. Pick a Khaata backup (.json)."
                else pendingRestoreJson = text
            }
        }
    }

    // Pull a fresh all-time snapshot whenever this screen opens.
    LaunchedEffect(Unit) { viewModel.refreshAllExpenses() }

    // A well-formed custom range is required before the RANGE scope can export.
    val rangeValid = rangeStart.isNotBlank() && rangeEnd.isNotBlank() && rangeStart <= rangeEnd

    // Filter the all-time snapshot down to the chosen scope. Dates are ISO
    // yyyy-MM-dd strings, so lexicographic comparison is chronological.
    val scopedExpenses = remember(allExpenses, selectedScope, viewedMonthKey, rangeStart, rangeEnd) {
        when (selectedScope) {
            ExportScope.MONTH -> allExpenses.filter { monthKeyFromDate(it.date) == viewedMonthKey }
            ExportScope.RANGE -> if (rangeValid) allExpenses.filter { it.date in rangeStart..rangeEnd } else emptyList()
            ExportScope.ALL -> allExpenses
        }
    }
    val scopedMonths = remember(allMonths, scopedExpenses, selectedScope) {
        when (selectedScope) {
            ExportScope.ALL -> allMonths
            else -> {
                val keys = scopedExpenses.map { monthKeyFromDate(it.date) }.toSet()
                allMonths.filter { it.monthKey in keys }
            }
        }
    }

    // Labels/filenames that describe the current scope for the PDF header and cache file.
    val scopeLabel = when (selectedScope) {
        ExportScope.MONTH -> monthLabel(viewedMonthKey)
        ExportScope.RANGE -> if (rangeValid) "$rangeStart to $rangeEnd" else "Custom range"
        ExportScope.ALL -> "All-time report"
    }
    val fileTag = when (selectedScope) {
        ExportScope.MONTH -> viewedMonthKey
        ExportScope.RANGE -> "${rangeStart}_to_${rangeEnd}"
        ExportScope.ALL -> "all"
    }

    val busy = loading || working
    val rangeIncomplete = selectedScope == ExportScope.RANGE && !rangeValid
    val hasData = scopedExpenses.isNotEmpty()

    Column(Modifier.fillMaxSize().background(Paper)) {
        TopAppBar(
            title = { Text("Export data") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Paper)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink, titleContentColor = Paper)
        )

        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Choose how much of your ledger to export. CSV suits spreadsheets and PDF makes a " +
                    "printable record — both cover expenses and monthly income for the selected scope. " +
                    "For a true backup of everything (goals, contributions, budgets, and the people " +
                    "ledger too), use Full backup below.",
                color = Muted, fontSize = 13.sp
            )

            // ── Scope selector ─────────────────────────────────────────────
            Text("What to export", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportScope.entries.forEach { option ->
                    FilterChip(
                        selected = selectedScope == option,
                        onClick = { selectedScope = option; exportError = null },
                        label = { Text(option.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Ink,
                            selectedLabelColor = Paper
                        )
                    )
                }
            }

            if (selectedScope == ExportScope.RANGE) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DatePickerField(
                        label = "From",
                        value = rangeStart,
                        onValueChange = { rangeStart = it; exportError = null },
                        modifier = Modifier.weight(1f)
                    )
                    DatePickerField(
                        label = "To",
                        value = rangeEnd,
                        onValueChange = { rangeEnd = it; exportError = null },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rangeStart.isNotBlank() && rangeEnd.isNotBlank() && rangeStart > rangeEnd) {
                    Text("The start date must be on or before the end date.", color = Rust, fontSize = 12.sp)
                }
            }

            Surface(color = PaperCard, border = BorderStroke(1.dp, PaperLine), shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Green)
                        Spacer(Modifier.width(10.dp))
                        Text("Loading your ledger…", fontSize = 13.sp, color = Muted)
                    } else if (rangeIncomplete) {
                        Text("Pick a start and end date to export a custom range.", fontSize = 13.sp, color = Muted)
                    } else {
                        Text(
                            "${scopedExpenses.size} entr${if (scopedExpenses.size == 1) "y" else "ies"} " +
                                "across ${scopedMonths.size} month${if (scopedMonths.size == 1) "" else "s"} " +
                                "ready to export ($scopeLabel).",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Button(
                onClick = {
                    working = true
                    exportError = null
                    scope.launch {
                        try {
                            val uri = withContext(Dispatchers.IO) {
                                val csv = Exporter.buildExpensesCsv(scopedExpenses, categories)
                                Exporter.writeTextToCache(context, "khaata-expenses-$fileTag.csv", csv)
                            }
                            Exporter.shareFile(context, uri, "text/csv", "Khaata expenses (CSV)")
                        } catch (e: Exception) {
                            exportError = "Couldn't create the CSV. Please try again."
                        } finally {
                            working = false
                        }
                    }
                },
                enabled = !busy && hasData,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper)
            ) {
                Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export as CSV")
            }

            Button(
                onClick = {
                    working = true
                    exportError = null
                    scope.launch {
                        try {
                            val uri = withContext(Dispatchers.IO) {
                                val file = Exporter.buildLedgerPdf(
                                    context, scopedMonths, scopedExpenses, categories,
                                    scopeLabel = scopeLabel,
                                    fileName = "khaata-ledger-$fileTag.pdf"
                                )
                                Exporter.uriFor(context, file)
                            }
                            Exporter.shareFile(context, uri, "application/pdf", "Khaata ledger report (PDF)")
                        } catch (e: Exception) {
                            exportError = "Couldn't create the PDF. Please try again."
                        } finally {
                            working = false
                        }
                    }
                },
                enabled = !busy && hasData,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Paper)
            ) {
                Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export as PDF report")
            }

            HorizontalDivider(color = PaperLine)
            Text("Full backup", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(
                "One JSON file with everything on your account — every month, expense, budget, goal, " +
                    "contribution, and person in your udhaar ledger. Keep it somewhere safe.",
                color = Muted, fontSize = 12.sp
            )
            Button(
                onClick = {
                    working = true
                    exportError = null
                    scope.launch {
                        try {
                            val json = viewModel.buildBackupJson()
                            if (json == null) {
                                // ViewModel already surfaced a message; just stop.
                                return@launch
                            }
                            val uri = withContext(Dispatchers.IO) {
                                Exporter.writeTextToCache(context, "khaata-backup-${todayStr()}.json", json)
                            }
                            Exporter.shareFile(context, uri, "application/json", "Khaata full backup")
                        } catch (e: Exception) {
                            exportError = "Couldn't create the backup file. Please try again."
                        } finally {
                            working = false
                        }
                    }
                },
                // Not gated on `hasData`: the backup is account-wide, so it's useful even
                // when the currently-scoped expense list happens to be empty (e.g. a user
                // who only tracks goals or udhaar).
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)
            ) {
                Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Download full backup (JSON)")
            }

            HorizontalDivider(color = PaperLine)
            Text("Restore from backup", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(
                "Load a Khaata backup file to rebuild your account. Best used on a fresh or empty " +
                    "account — entries that share an id with the backup are overwritten.",
                color = Muted, fontSize = 12.sp
            )
            Button(
                onClick = { exportError = null; restoreLauncher.launch("application/json") },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PaperCard, contentColor = Ink),
                border = BorderStroke(1.dp, PaperLine)
            ) {
                Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Restore from a backup file")
            }

            if (working) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Green)
                    Spacer(Modifier.width(8.dp))
                    Text("Preparing your file…", fontSize = 12.sp, color = Muted)
                }
            }
            if (!loading && !rangeIncomplete && !hasData) {
                Text("No expenses to export for this selection.", color = Rust, fontSize = 12.sp)
            }
            exportError?.let { Text(it, color = Rust, fontSize = 12.sp) }
            Spacer(Modifier.height(4.dp))
        }
    }

    pendingRestoreJson?.let { json ->
        AlertDialog(
            onDismissRequest = { pendingRestoreJson = null },
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "This writes the backup's months, expenses, budgets, goals, and people back into " +
                        "your account. Anything sharing an id with the backup is overwritten. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val text = json
                    pendingRestoreJson = null
                    working = true
                    scope.launch {
                        viewModel.restoreFromBackup(text)
                        working = false
                    }
                }) { Text("Restore", color = Rust) }
            },
            dismissButton = { TextButton(onClick = { pendingRestoreJson = null }) { Text("Cancel") } }
        )
    }
}
