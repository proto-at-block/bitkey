package build.wallet.molecule

import androidx.compose.runtime.MonotonicFrameClock
import app.cash.molecule.RecompositionClock.ContextClock
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A [CoroutineContext] that contains Frame Clock for Compose Runtime - used for synchronizing model
 * emission with the frame rate.
 *
 * - On Android and JVM this is set to [EmptyCoroutineContext] because we don't actually use
 * Molecule runtime on these platforms, we use Compose Runtime directly.
 * - On iOS this implements [MonotonicFrameClock] using iOS's [CADisplayLink].
 *
 * TODO(W-1788): remove this in favor of Molecule's [ContextClock] implementation once [MonotonicFrameClock]
 *  is provided on iOS out of the box: https://github.com/cashapp/molecule/pull/170
 */
internal expect fun composeFrameClock(): CoroutineContext
