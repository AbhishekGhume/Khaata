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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(viewModel: FinanceViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allExpenses by viewModel.allExpenses.collectAsState()
    val allMonths by viewModel.allMonths.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val loading by viewModel.allExpensesLoading.collectAsState()

    var working by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }

    // Pull a fresh all-time snapshot whenever this screen opens.
    LaunchedEffect(Unit) { viewModel.refreshAllExpenses() }

    val busy = loading || working
    val hasData = allExpenses.isNotEmpty()

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
                "Export your entire ledger — every expense across all months. Use CSV for spreadsheets " +
                    "or a PDF report for a printable record. This doubles as your backup.",
                color = Muted, fontSize = 13.sp
            )

            Surface(color = PaperCard, border = BorderStroke(1.dp, PaperLine), shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Green)
                        Spacer(Modifier.width(10.dp))
                        Text("Loading your ledger…", fontSize = 13.sp, color = Muted)
                    } else {
                        Text(
                            "${allExpenses.size} entries across ${allMonths.size} month${if (allMonths.size == 1) "" else "s"} ready to export.",
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
                                val csv = Exporter.buildExpensesCsv(allExpenses, categories)
                                Exporter.writeTextToCache(context, "khaata-expenses.csv", csv)
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
                                val file = Exporter.buildLedgerPdf(context, allMonths, allExpenses, categories)
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

            if (working) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Green)
                    Spacer(Modifier.width(8.dp))
                    Text("Preparing your file…", fontSize = 12.sp, color = Muted)
                }
            }
            if (!loading && !hasData) {
                Text("No expenses to export yet.", color = Rust, fontSize = 12.sp)
            }
            exportError?.let { Text(it, color = Rust, fontSize = 12.sp) }
            Spacer(Modifier.height(4.dp))
        }
    }
}
