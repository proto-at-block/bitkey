package build.wallet.molecule

import kotlin.coroutines.CoroutineContext

/**
 * On JVM we use Compose Runtime directly, this shouldn't be used.
 */
internal actual fun composeFrameClock(): CoroutineContext = error("Shouldn't be used on JVM.")
