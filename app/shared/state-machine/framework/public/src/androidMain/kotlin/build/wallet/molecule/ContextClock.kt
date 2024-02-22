package build.wallet.molecule

import kotlin.coroutines.CoroutineContext

/**
 * On Android we use Compose Runtime directly, this shouldn't be used.
 */
internal actual fun composeFrameClock(): CoroutineContext = error("Shouldn't be used on Android.")
