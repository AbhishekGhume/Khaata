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

/**
 * Formats a stored money amount back into an editable field string. Uses
 * BigDecimal.toPlainString so we never hand the field `Double.toString`'s artifacts:
 * a trailing ".0" ("50000.0") or, for large values, scientific notation ("1.0E8")
 * — the latter of which `isMoneyInputAllowed`/`MONEY_REGEX` would reject, trapping
 * the user until they clear the field. An amount of 0 prefills as blank so the
 * placeholder shows through.
 */
fun moneyToInput(amount: Double): String =
    if (amount == 0.0) "" else java.math.BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString()
