package com.khaata.app.util

/**
 * Money input helpers shared by every amount field (income, expense, budget cap,
 * goal target, contribution). Keeps the fields to a sane decimal shape so the
 * `KeyboardType.Decimal` keypad can't produce something `toDoubleOrNull()` will
 * silently reject — up to 9 integer digits and 2 decimal places.
 */
private val MONEY_REGEX = Regex("^\\d{0,9}(\\.\\d{0,2})?$")

/** True if [next] is a partial-or-complete money string we should let the user keep typing. */
fun isMoneyInputAllowed(next: String): Boolean = next.isEmpty() || next.matches(MONEY_REGEX)

/**
 * Parses a money field for submission. Returns the positive amount, or null if the
 * field is blank / non-numeric / not greater than zero — callers surface an inline
 * error rather than silently coercing junk to 0.
 */
fun parsePositiveAmount(raw: String): Double? = raw.trim().toDoubleOrNull()?.takeIf { it > 0.0 }
