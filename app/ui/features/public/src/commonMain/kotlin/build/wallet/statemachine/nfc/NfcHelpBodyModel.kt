package build.wallet.statemachine.nfc

import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationHelpBodyModel
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationHelpContent.Companion.TapBitkey

class NfcHelpBodyModel(
  onBack: () -> Unit,
  devicePlatform: DevicePlatform,
) : HardwareConfirmationHelpBodyModel(
    onBack = onBack,
    content = TapBitkey,
    devicePlatform = devicePlatform,
    eventTrackerScreenIdOverride = NfcEventTrackerScreenId.NFC_HELP,
    eventTrackerShouldTrackOverride = true
  )
