@file:OptIn(ExperimentalForeignApi::class)

package build.wallet.molecule

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ExportObjCClass
import kotlinx.cinterop.ObjCAction
import platform.Foundation.NSDefaultRunLoopMode
import platform.Foundation.NSRunLoop
import platform.Foundation.NSSelectorFromString
import platform.QuartzCore.CADisplayLink

/**
 * Provides a time source for display frames and the ability to perform an action on the next frame.
 * This may be used for matching timing with the refresh rate of a display or otherwise
 * synchronizing work with a desired frame rate.
 *
 * Borrowed from https://github.com/cashapp/molecule/pull/170.
 * TODO(W-1788): remove once Molecule ships this implementation out of the box.
 */
@ExportObjCClass
object DisplayLinkClock : MonotonicFrameClock {
  @Suppress("unused") // This registers a DisplayLink listener.
  private val displayLink: CADisplayLink by lazy {
    CADisplayLink.displayLinkWithTarget(
      target = this,
      selector = NSSelectorFromString(this::tickClock.name)
    )
  }

  private val clock by lazy {
    BroadcastFrameClock {
      // We only want to listen to the DisplayLink run loop if we have frame awaiters.
      displayLink.addToRunLoop(NSRunLoop.currentRunLoop, NSDefaultRunLoopMode)
    }
  }

  override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
    return clock.withFrameNanos(onFrame)
  }

  // The following function must remain public to be a valid candidate for the call to
  // NSSelectorString above.
  @ObjCAction
  @Suppress("RedundantVisibilityModifier")
  public fun tickClock() {
    clock.sendFrame(0L)

    // Remove the DisplayLink from the run loop. It will get added again if new frame awaiters
    // appear.
    displayLink.removeFromRunLoop(NSRunLoop.currentRunLoop, NSDefaultRunLoopMode)
  }
}
