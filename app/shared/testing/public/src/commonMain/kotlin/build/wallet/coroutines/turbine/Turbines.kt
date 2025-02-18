package build.wallet.coroutines.turbine

import app.cash.turbine.ReceiveTurbine
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

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
suspend fun <T> ReceiveTurbine<out T>.awaitNoEvents(timeout: Duration = 50.milliseconds) {
  delay(timeout)
  expectNoEvents()
}
