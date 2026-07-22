package com.khaata.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset

/**
 * One place for Khaata's animation feel, so everything reads as "designed" rather
 * than "animated". Two families, used consistently:
 *
 *  - **Value reveals** (counting numbers, ring sweeps, bar fills): a medium
 *    ease-out [tween]. Deliberate, never bouncy — a passbook figure settling in.
 *  - **Interactive motion** (row placement, expanding sections): a [spring], so
 *    things that respond to a tap feel physical.
 */

/** Duration for a number counting up, a ring sweeping, or a bar filling. */
const val VALUE_ANIM_MS = 650

/** Per-item stagger for a list of bars/rows revealing together (capped so long lists don't crawl). */
const val STAGGER_STEP_MS = 45
const val STAGGER_MAX_MS = 270

/** Ease-out tween for any value reveal; [delayMillis] drives the stagger. */
fun <T> valueReveal(delayMillis: Int = 0): FiniteAnimationSpec<T> =
    tween(durationMillis = VALUE_ANIM_MS, delayMillis = delayMillis, easing = FastOutSlowInEasing)

/** How much of a row's index-based stagger delay to apply, capped. */
fun staggerDelay(index: Int): Int = (index * STAGGER_STEP_MS).coerceAtMost(STAGGER_MAX_MS)

// ── LazyColumn item enter/leave/reorder (Modifier.animateItem) ──────────────────
// New rows fade in, deleted rows fade out, and the gap closes with a gentle spring
// so a delete visibly "settles" instead of blinking away.

fun listItemFadeIn(): FiniteAnimationSpec<Float> = tween(durationMillis = 240, easing = FastOutSlowInEasing)

fun listItemFadeOut(): FiniteAnimationSpec<Float> = tween(durationMillis = 160)

fun listItemPlacement(): FiniteAnimationSpec<IntOffset> =
    spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)

/** Shorthand for the standard Khaata list-item enter/leave/reorder animation. */
fun LazyItemScope.animatedListItem(): Modifier =
    Modifier.animateItem(
        fadeInSpec = listItemFadeIn(),
        fadeOutSpec = listItemFadeOut(),
        placementSpec = listItemPlacement()
    )
