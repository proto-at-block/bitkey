package build.wallet.molecule

import androidx.compose.runtime.MonotonicFrameClock
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A [CoroutineContext] that contains Frame Clock for Compose Runtime - used for synchronizing model
 * emission with the frame rate.
 *
 * - On Android and JVM this is set to [EmptyCoroutineContext] because we don't actually use
 * Molecule runtime on these platforms, we use Compose Runtime directly.
 * - On iOS this implements [MonotonicFrameClock] using iOS's [CADisplayLink].
 */
internal expect fun composeFrameClock(): CoroutineContext
