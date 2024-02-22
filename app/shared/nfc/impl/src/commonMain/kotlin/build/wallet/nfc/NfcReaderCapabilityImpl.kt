package build.wallet.nfc

import build.wallet.platform.PlatformContext

expect class NfcReaderCapabilityImpl constructor(
  platformContext: PlatformContext,
  isHardwareFake: Boolean,
) : NfcReaderCapability
