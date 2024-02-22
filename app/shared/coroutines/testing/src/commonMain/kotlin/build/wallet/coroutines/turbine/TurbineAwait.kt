package build.wallet.coroutines.turbine

import app.cash.turbine.ReceiveTurbine

/**
 * Assert that one of the next events received was an item matching [predicate], effectively
 * skipping intermediate non matching items.
 *
 * This function will suspend if no matching items have been received.
 */
suspend fun <T> ReceiveTurbine<out T>.awaitUntil(predicate: (T) -> Boolean): T {
  while (true) {
    val item = awaitItem()
    if (predicate(item)) return item
  }
}

/**
 * Assert that one of the next events received was an item of type [R], effectively
 * skipping intermediate non matching items.
 *
 * This function will suspend if no matching items have been received.
 */
suspend inline fun <reified R> ReceiveTurbine<out Any>.awaitUntil(): R =
  awaitUntil(predicate = { item -> item is R }) as R
