package build.wallet.molecule

import androidx.compose.runtime.MonotonicFrameClock
import app.cash.molecule.DisplayLinkClock
import platform.QuartzCore.CADisplayLink
import kotlin.coroutines.CoroutineContext

/**
 * On iOS implements [MonotonicFrameClock] using [CADisplayLink].
 */
internal actual fun composeFrameClock(): CoroutineContext = DisplayLinkClock
