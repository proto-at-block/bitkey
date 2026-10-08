package build.wallet.platform.sharing

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

/**
 * The receiver is declared in the manifest, so the system builds it with a no-arg constructor and
 * the pending completion has to live in a static field. That field previously held the
 * activity-scoped `SharingManager`, which retained a destroyed `MainActivity` whenever the share
 * sheet was dismissed rather than acted on. These cover the release behavior that replaced it.
 */
class SharingManagerBroadcastReceiverTests : FunSpec({

  beforeTest {
    SharingManagerBroadcastReceiver.setPendingCompletion(null)
  }

  afterTest {
    SharingManagerBroadcastReceiver.setPendingCompletion(null)
  }

  test("taking the completion hands back the registered callback") {
    var wasCalledWith: Boolean? = null
    SharingManagerBroadcastReceiver.setPendingCompletion { wasCalledWith = it }

    SharingManagerBroadcastReceiver.takePendingCompletion()?.invoke(true)

    wasCalledWith.shouldBe(true)
  }

  test("taking the completion clears it, so a share is only reported once") {
    var callCount = 0
    SharingManagerBroadcastReceiver.setPendingCompletion { callCount++ }

    SharingManagerBroadcastReceiver.takePendingCompletion()?.invoke(true)
    SharingManagerBroadcastReceiver.takePendingCompletion()?.invoke(true)

    callCount.shouldBe(1)
  }

  test("nothing is retained once the completion has been taken") {
    SharingManagerBroadcastReceiver.setPendingCompletion { }

    SharingManagerBroadcastReceiver.takePendingCompletion()

    // The static field is the leak path: after a completed share it must hold nothing.
    SharingManagerBroadcastReceiver.takePendingCompletion().shouldBe(null)
  }

  test("clearing the completion drops it without invoking it") {
    var wasCalled = false
    SharingManagerBroadcastReceiver.setPendingCompletion { wasCalled = true }

    // Stands in for the activity being destroyed, or the chooser failing to open, while a share is
    // outstanding. Neither sends a broadcast, so the callback has to be dropped rather than run.
    SharingManagerBroadcastReceiver.setPendingCompletion(null)

    wasCalled.shouldBe(false)
    SharingManagerBroadcastReceiver.takePendingCompletion().shouldBe(null)
  }

  test("a new share replaces the previous completion") {
    var firstWasCalled = false
    var secondWasCalled = false
    SharingManagerBroadcastReceiver.setPendingCompletion { firstWasCalled = true }
    SharingManagerBroadcastReceiver.setPendingCompletion { secondWasCalled = true }

    SharingManagerBroadcastReceiver.takePendingCompletion()?.invoke(true)

    // Only the most recent chooser can still be on screen, so only its callback should run.
    firstWasCalled.shouldBe(false)
    secondWasCalled.shouldBeTrue()
  }

  test("taking a completion that was never set is a no-op") {
    SharingManagerBroadcastReceiver.takePendingCompletion().shouldBe(null)
  }
})
