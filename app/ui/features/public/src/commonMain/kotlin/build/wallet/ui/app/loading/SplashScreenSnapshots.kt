package build.wallet.ui.app.loading

import bitkey.ui.Snapshot
import bitkey.ui.SnapshotHost
import build.wallet.statemachine.core.SplashBodyModel
import kotlin.time.Duration.Companion.ZERO

@Snapshot
val SnapshotHost.splashScreen
  get() = SplashBodyModel(
    bitkeyWordMarkAnimationDelay = ZERO,
    bitkeyWordMarkAnimationDuration = ZERO
  )
