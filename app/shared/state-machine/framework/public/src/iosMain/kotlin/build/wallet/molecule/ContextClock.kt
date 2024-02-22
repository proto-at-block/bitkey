package build.wallet.molecule

import androidx.compose.runtime.MonotonicFrameClock
import app.cash.molecule.RecompositionClock.ContextClock
import platform.QuartzCore.CADisplayLink
import kotlin.coroutines.CoroutineContext

/**
 * On iOS implements [MonotonicFrameClock] using [CADisplayLink].
 *
 * TODO(W-1788): remove this in favor of Molecule's [ContextClock] implementation once [MonotonicFrameClock]
 *  is provided on iOS out of the box: https://github.com/cashapp/molecule/pull/170
 */
internal actual fun composeFrameClock(): CoroutineContext = DisplayLinkClock
