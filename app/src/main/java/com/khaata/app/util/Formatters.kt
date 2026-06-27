package com.khaata.app.util

import androidx.compose.ui.graphics.Color
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

data class CategoryMeta(val key: String, val label: String, val color: Color)

val CATEGORIES = listOf(
    CategoryMeta("food", "Food & Groceries", Color(0xFF2F6F4E)),
    CategoryMeta("transport", "Transport", Color(0xFF3B6EA5)),
    CategoryMeta("rent", "Rent / PG", Color(0xFF7A4FA3)),
    CategoryMeta("bills", "Bills & Recharge", Color(0xFFC18A2D)),
    CategoryMeta("entertainment", "Entertainment", Color(0xFFB5482F)),
    CategoryMeta("shopping", "Shopping", Color(0xFF1B8A8A)),
    CategoryMeta("other", "Other", Color(0xFF6B6357)),
)

fun categoryMeta(key: String): CategoryMeta = CATEGORIES.find { it.key == key } ?: CATEGORIES.last()

private val inrFormat: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN"))

/** ₹ formatted with Indian digit grouping, e.g. ₹1,25,000 */
fun formatINR(amount: Double): String {
    val rounded = amount.roundToLong()
    return "\u20B9${inrFormat.format(rounded)}"
}
