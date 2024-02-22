package build.wallet.platform.haptics

import build.wallet.platform.PlatformContext

expect class HapticsImpl constructor(
  platformContext: PlatformContext,
  hapticsPolicy: HapticsPolicy,
) : Haptics
