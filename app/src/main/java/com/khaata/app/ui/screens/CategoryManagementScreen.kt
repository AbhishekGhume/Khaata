package com.khaata.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khaata.app.util.CATEGORY_ICONS
import com.khaata.app.util.CategoryMeta
import com.khaata.app.util.iconForKey
import com.khaata.app.ui.theme.Ink
import com.khaata.app.ui.theme.Muted
import com.khaata.app.ui.theme.Paper
import com.khaata.app.ui.theme.PaperCard
import com.khaata.app.ui.theme.PaperLine
import com.khaata.app.ui.theme.Rust
import com.khaata.app.viewmodel.FinanceViewModel

/** The palette users pick a category color from. */
private val CATEGORY_COLORS = listOf(
    Color(0xFF2F6F4E), Color(0xFF3B6EA5), Color(0xFF7A4FA3), Color(0xFFC18A2D),
    Color(0xFFB5482F), Color(0xFF1B8A8A), Color(0xFF6B6357), Color(0xFFA23B5E),
    Color(0xFF4E7A3B), Color(0xFF2D6FC1), Color(0xFFB59B2F), Color(0xFF5E4EA2),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(viewModel: FinanceViewModel, onBack: () -> Unit) {
    val categories by viewModel.categories.collectAsState()
    var editing by remember { mutableStateOf<CategoryMeta?>(null) }
    var creatingNew by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<CategoryMeta?>(null) }

    Column(Modifier.fillMaxSize().background(Paper)) {
        TopAppBar(
            title = { Text("Manage categories") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Paper)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink, titleContentColor = Paper)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Add your own categories or rename, recolor, and re-icon any category. " +
                        "Use the arrows to set the order they appear in everywhere (Add Entry, budgets, split). " +
                        "Deleting one reassigns its past entries and any recurring expenses to \"Other\".",
                    color = Muted, fontSize = 13.sp
                )
            }
            item {
                Button(
                    onClick = { creatingNew = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New category")
                }
            }
            itemsIndexed(categories, key = { _, it -> it.key }) { index, cat ->
                Surface(color = PaperCard, border = BorderStroke(1.dp, PaperLine), shape = RoundedCornerShape(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Reorder arrows — disabled at the ends of the list.
                        Column {
                            IconButton(
                                onClick = { viewModel.moveCategory(cat.key, up = true) },
                                enabled = index > 0,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowUp, contentDescription = "Move up",
                                    tint = if (index > 0) Ink else PaperLine, modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.moveCategory(cat.key, up = false) },
                                enabled = index < categories.lastIndex,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowDown, contentDescription = "Move down",
                                    tint = if (index < categories.lastIndex) Ink else PaperLine, modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.size(34.dp).background(cat.color, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(iconForKey(cat.iconKey), contentDescription = null, tint = Paper, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(cat.label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { editing = cat }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = Muted, modifier = Modifier.size(18.dp))
                        }
                        if (cat.key != "other") {
                            IconButton(onClick = { toDelete = cat }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Rust, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (creatingNew) {
        CategoryEditDialog(
            existing = null,
            takenKeys = categories.map { it.key }.toSet(),
            onDismiss = { creatingNew = false },
            onSave = { key, label, color, iconKey ->
                viewModel.saveCategory(key, label, color, iconKey)
                creatingNew = false
            }
        )
    }

    editing?.let { cat ->
        CategoryEditDialog(
            existing = cat,
            takenKeys = categories.map { it.key }.toSet(),
            onDismiss = { editing = null },
            onSave = { key, label, color, iconKey ->
                viewModel.saveCategory(key, label, color, iconKey)
                editing = null
            }
        )
    }

    toDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete \"${cat.label}\"?") },
            text = { Text("Past entries and any recurring expenses in this category will move to \"Other\". This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCategory(cat.key)
                    toDelete = null
                }) { Text("Delete", color = Rust) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditDialog(
    existing: CategoryMeta?,
    takenKeys: Set<String>,
    onDismiss: () -> Unit,
    onSave: (key: String, label: String, color: Color, iconKey: String) -> Unit,
) {
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var color by remember { mutableStateOf(existing?.color ?: CATEGORY_COLORS.first()) }
    var iconKey by remember { mutableStateOf(existing?.iconKey ?: "category") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New category" else "Edit category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it; error = null },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = Rust, fontSize = 11.sp) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Color", fontSize = 12.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORY_COLORS.forEach { swatch ->
                        val selected = swatch == color
                        Box(
                            Modifier
                                .size(30.dp)
                                .background(swatch, CircleShape)
                                .border(if (selected) 3.dp else 0.dp, Ink, CircleShape)
                                .clickable { color = swatch }
                        )
                    }
                }
                Text("Icon", fontSize = 12.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORY_ICONS.forEach { (key, icon) ->
                        val selected = key == iconKey
                        Box(
                            Modifier
                                .size(38.dp)
                                .background(if (selected) color else PaperCard, RoundedCornerShape(9.dp))
                                .border(1.dp, if (selected) color else PaperLine, RoundedCornerShape(9.dp))
                                .clickable { iconKey = key },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, contentDescription = key, tint = if (selected) Paper else Ink, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = label.trim()
                if (trimmed.isEmpty()) {
                    error = "Enter a category name."
                    return@TextButton
                }
                // Existing categories keep their key; new ones derive a stable key
                // from the name, de-duplicated against taken keys.
                val key = existing?.key ?: run {
                    val base = trimmed.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "cat" }
                    var candidate = base
                    var n = 2
                    while (takenKeys.contains(candidate)) { candidate = "${base}_$n"; n++ }
                    candidate
                }
                onSave(key, trimmed, color, iconKey)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
