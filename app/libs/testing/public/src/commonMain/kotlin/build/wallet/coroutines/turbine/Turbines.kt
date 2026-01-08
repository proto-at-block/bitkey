package build.wallet.coroutines.turbine

import app.cash.turbine.ReceiveTurbine
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Assert that one of the next events received was an item matching [predicate], effectively
 * skipping intermediate non matching items.
 *
 * Will suspend if no matching items have been received and throw after turbine timeout.
 */
suspend fun <T> ReceiveTurbine<out T>.awaitUntil(predicate: (T) -> Boolean): T {
  while (true) {
    val item = awaitItem()
    if (predicate(item)) return item
  }
}

/**
 * Asserts that one of the next received items was an exact [item], skipping intermediate non matching items.
 *
 * Will suspend if no matching items have been received and throw after turbine timeout.
 */
suspend fun <T> ReceiveTurbine<out T>.awaitUntil(item: T): T = awaitUntil { it == item }

/**
 * Asserts that one of the next received items was not nullable, skipping intermediate null items.
 *
 * Will suspend if no matching items have been received and throw after turbine timeout.
 */
suspend fun <T> ReceiveTurbine<out T?>.awaitUntilNotNull(): T = awaitUntil { it != null }!!

/**
 * Assert that one of the next events received was an item of type [R], effectively
 * skipping intermediate non matching items.
 *
 * This function will suspend if no matching items have been received.
 */
suspend inline fun <reified R> ReceiveTurbine<out Any>.awaitUntil(): R =
  awaitUntil(predicate = { item -> item is R }) as R

/**
 * Suspends for the specified [timeout] and then checks that no events have been emitted by this
 * [ReceiveTurbine].
 *
 * Using [awaitNoEvents] is preferable over [ReceiveTurbine.expectNoEvents], since it allows
 * the coroutine a small window to emit any leftover events before verifying that none have arrived.
 *
 * However, this method remains partially non-deterministic because we can't guarantee no additional
 * events will come in after the arbitrary delay. It also makes tests run slightly longer.
 *
 * Whenever possible, prefer using more deterministic testing strategy, like waiting for a state change
 * or an event (as opposed to absence of it) using [ReceiveTurbine.awaitItem].
 *
 * @param timeout how long to wait for until calling non-suspending [timeout]. The default delay value
 * is rather arbitrary.
 */
@DelicateCoroutinesApi
suspend inline fun <T> ReceiveTurbine<out T>.awaitNoEvents(timeout: Duration = 50.milliseconds) {
  delay(timeout)
  expectNoEvents()
}

/**
 * Suspends briefly to receive an item if one is emitted within [timeout], returning null otherwise.
 *
 * **IMPORTANT: This is a non-deterministic API.** It introduces timing-based behavior that goes
 * against our coroutine testing practices which emphasize determinism. Only use this when you
 * have no other option.
 *
 * ## When to use
 *
 * Use this ONLY when there's a genuine race condition in the implementation that cannot be
 * controlled in tests. The most common case is Compose's `produceState` pattern:
 *
 * ```kotlin
 * val state by produceState(initialValue = Loading) {
 *   value = suspendFunction() // Race between this completing and recomposition
 * }
 * ```
 *
 * In this pattern, there's a race between:
 * 1. The suspend function completing and updating state
 * 2. Compose recomposing and rendering the initial `Loading` state
 *
 * If the suspend function completes before recomposition, the `Loading` state (and any
 * associated analytics events) may never be rendered. This is inherent to Compose's
 * timing and cannot be controlled in tests.
 *
 * ## When NOT to use
 *
 * - For events that should ALWAYS be emitted - use [awaitItem] instead
 * - For events that should NEVER be emitted - don't call this at all
 * - When you can restructure the test to be deterministic
 *
 * ## Best practices
 *
 * - Always document WHY the non-determinism exists at the call site
 * - Keep [timeout] small to avoid slowing down tests
 * - If you find yourself using this frequently, consider if the implementation can be improved
 *
 * @param timeout How long to wait for an item before returning null. Default is very short
 *   to minimize test slowdown.
 * @return The received item, or null if no item was emitted within [timeout].
 */
@DelicateCoroutinesApi
suspend fun <T> ReceiveTurbine<out T>.awaitItemMaybe(timeout: Duration = 10.milliseconds): T? {
  val pollInterval = 2.milliseconds
  val mark = TimeSource.Monotonic.markNow()

  // Poll for items until timeout
  while (mark.elapsedNow() < timeout) {
    try {
      // expectMostRecentItem is non-blocking - returns immediately if items available
      return expectMostRecentItem()
    } catch (_: AssertionError) {
      // No items yet, wait briefly and try again
      delay(pollInterval)
    }
  }

  // Final check after timeout
  return try {
    expectMostRecentItem()
  } catch (_: AssertionError) {
    null
  }
}
