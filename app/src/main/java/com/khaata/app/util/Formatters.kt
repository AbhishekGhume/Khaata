package com.khaata.app.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Savings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

data class CategoryMeta(
    val key: String,
    val label: String,
    val color: Color,
    val iconKey: String = "category"
)

/** Seeded on first run and used as the fallback when the live list is unavailable. */
val DEFAULT_CATEGORIES = listOf(
    CategoryMeta("food", "Food & Groceries", Color(0xFF2F6F4E), "restaurant"),
    CategoryMeta("transport", "Transport", Color(0xFF3B6EA5), "bus"),
    CategoryMeta("rent", "Rent / PG", Color(0xFF7A4FA3), "home"),
    CategoryMeta("bills", "Bills & Recharge", Color(0xFFC18A2D), "receipt"),
    CategoryMeta("entertainment", "Entertainment", Color(0xFFB5482F), "movie"),
    CategoryMeta("shopping", "Shopping", Color(0xFF1B8A8A), "cart"),
    CategoryMeta("other", "Other", Color(0xFF6B6357), "category"),
)

/** Retained name for existing callers; the static default list. */
val CATEGORIES = DEFAULT_CATEGORIES

/** The curated icon set users can choose from, keyed by the string stored in Firestore. */
val CATEGORY_ICONS: Map<String, ImageVector> = mapOf(
    "restaurant" to Icons.Filled.Restaurant,
    "fastfood" to Icons.Filled.Fastfood,
    "bus" to Icons.Filled.DirectionsBus,
    "home" to Icons.Filled.Home,
    "receipt" to Icons.Filled.Receipt,
    "movie" to Icons.Filled.Movie,
    "cart" to Icons.Filled.ShoppingCart,
    "phone" to Icons.Filled.Smartphone,
    "health" to Icons.Filled.LocalHospital,
    "school" to Icons.Filled.School,
    "fitness" to Icons.Filled.FitnessCenter,
    "travel" to Icons.Filled.Flight,
    "gift" to Icons.Filled.CardGiftcard,
    "pets" to Icons.Filled.Pets,
    "savings" to Icons.Filled.Savings,
    "category" to Icons.Filled.Category,
)

fun iconForKey(iconKey: String): ImageVector = CATEGORY_ICONS[iconKey] ?: Icons.Filled.Category

/** Fallback lookup over the built-in defaults (for callers without the live list). */
fun categoryMeta(key: String): CategoryMeta = DEFAULT_CATEGORIES.find { it.key == key } ?: DEFAULT_CATEGORIES.last()

/** Lookup over the live category list, falling back to defaults then "Other". */
fun categoryMeta(key: String, categories: List<CategoryMeta>): CategoryMeta =
    categories.find { it.key == key } ?: categoryMeta(key)

// NumberFormat/DecimalFormat are NOT thread-safe, and formatINR is called both from
// the Compose main thread (during recomposition) and from Dispatchers.IO (PDF export
// in Exporter.buildLedgerPdf). A single shared instance would corrupt its internal
// DigitList under concurrent format() calls — garbled amounts or an AIOOBE from inside
// DecimalFormat. A ThreadLocal gives each thread its own formatter, keeping the Indian
// grouping without the shared-state hazard.
private val inrFormat: ThreadLocal<NumberFormat> =
    ThreadLocal.withInitial { NumberFormat.getNumberInstance(Locale("en", "IN")) }

/** ₹ formatted with Indian digit grouping, e.g. ₹1,25,000 */
fun formatINR(amount: Double): String {
    val rounded = amount.roundToLong()
    return "\u20B9${inrFormat.get().format(rounded)}"
}
