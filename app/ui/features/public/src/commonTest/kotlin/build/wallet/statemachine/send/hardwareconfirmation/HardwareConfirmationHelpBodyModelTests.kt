package build.wallet.statemachine.send.hardwareconfirmation

import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.platform.device.DevicePlatform.Android
import build.wallet.platform.device.DevicePlatform.IOS
import build.wallet.platform.device.DevicePlatform.Jvm
import build.wallet.analytics.events.screen.id.FwupEventTrackerScreenId
import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationHelpContent.Companion.FirmwareUpdate
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationHelpContent.Companion.TapBitkey
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationHelpContent.Companion.TransactionReview
import build.wallet.ui.theme.Theme.DARK
import build.wallet.ui.theme.Theme.LIGHT
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class HardwareConfirmationHelpBodyModelTests : FunSpec({

  test("transaction review help keeps a stable key while tracking send analytics") {
    val model = HardwareConfirmationHelpBodyModel(
      onBack = {},
      content = TransactionReview,
      devicePlatform = Android
    )
    val recomposedModel = HardwareConfirmationHelpBodyModel(
      onBack = {},
      content = TransactionReview,
      devicePlatform = Android
    )
    model.eventTrackerShouldTrack.shouldBe(true)
    model.id.shouldBe(SendEventTrackerScreenId.SEND_HARDWARE_CONFIRMATION_HELP)
    model.key.shouldBe(recomposedModel.key)

    val screenInfo = requireNotNull(model.eventTrackerScreenInfo)
    screenInfo.eventTrackerScreenId.shouldBe(SendEventTrackerScreenId.SEND_HARDWARE_CONFIRMATION_HELP)
    screenInfo.eventTrackerShouldTrack.shouldBe(true)
  }

  test("shared tap help content keeps a stable key while suppressing analytics") {
    val model = HardwareConfirmationHelpBodyModel(
      onBack = {},
      content = TapBitkey,
      devicePlatform = Android
    )
    val recomposedModel = HardwareConfirmationHelpBodyModel(
      onBack = {},
      content = TapBitkey,
      devicePlatform = Android
    )
    model.eventTrackerShouldTrack.shouldBe(false)
    model.id.shouldBe(SendEventTrackerScreenId.SEND_HARDWARE_CONFIRMATION_HELP)
    model.key.shouldBe(recomposedModel.key)

    val screenInfo = requireNotNull(model.eventTrackerScreenInfo)
    screenInfo.eventTrackerScreenId.shouldBe(SendEventTrackerScreenId.SEND_HARDWARE_CONFIRMATION_HELP)
    screenInfo.eventTrackerShouldTrack.shouldBe(false)
  }

  test("eventTrackerShouldTrack is true for non-TransactionReview content") {
    val otherContent = HardwareConfirmationHelpContent(
      headline = "Other help",
      androidStatements = listOf(
        HardwareConfirmationHelpContent.Statement(title = "Step 1", body = "Do this"),
        HardwareConfirmationHelpContent.Statement(title = "Step 2", body = "Do that"),
        HardwareConfirmationHelpContent.Statement(title = "Step 3", body = "Done")
      )
    )
    val model = HardwareConfirmationHelpBodyModel(
      onBack = {},
      content = otherContent,
      devicePlatform = Android
    )
    model.eventTrackerShouldTrack.shouldBe(true)
    model.id.shouldBe(SendEventTrackerScreenId.SEND_HARDWARE_CONFIRMATION_HELP)
  }

  test("firmware update help uses firmware analytics id") {
    val model = HardwareConfirmationHelpBodyModel(
      onBack = {},
      content = FirmwareUpdate,
      devicePlatform = Android
    )

    model.eventTrackerShouldTrack.shouldBe(true)
    model.id.shouldBe(FwupEventTrackerScreenId.FWUP_HELP)
  }

  test("explicit analytics overrides allow NFC help to reuse the shared model") {
    val model = HardwareConfirmationHelpBodyModel(
      onBack = {},
      content = TapBitkey,
      devicePlatform = Android,
      eventTrackerScreenIdOverride = NfcEventTrackerScreenId.NFC_HELP,
      eventTrackerContext = NfcEventTrackerScreenIdContext.PAIR_NEW_HW_FINGERPRINT,
      eventTrackerShouldTrackOverride = true
    )

    model.eventTrackerShouldTrack.shouldBe(true)
    model.id.shouldBe(NfcEventTrackerScreenId.NFC_HELP)

    val screenInfo = requireNotNull(model.eventTrackerScreenInfo)
    screenInfo.eventTrackerScreenId.shouldBe(NfcEventTrackerScreenId.NFC_HELP)
    screenInfo.eventTrackerContext.shouldBe(NfcEventTrackerScreenIdContext.PAIR_NEW_HW_FINGERPRINT)
    screenInfo.eventTrackerShouldTrack.shouldBe(true)
  }

  test("transaction review uses dedicated send copy") {
    TransactionReview.statements(Android).map { it.title }.shouldBe(
      listOf("CHECK THE ADDRESS", "CHECK THE AMOUNT", "FINISH ON YOUR PHONE")
    )
    TransactionReview.statements(Jvm).map { it.title }.shouldBe(
      listOf("CHECK THE ADDRESS", "CHECK THE AMOUNT", "FINISH ON YOUR PHONE")
    )
    TransactionReview.statements(IOS).map { it.title }.shouldBe(
      listOf("CHECK THE ADDRESS", "CHECK THE AMOUNT", "FINISH ON YOUR PHONE")
    )
  }

  test("transaction review does not return a video") {
    TransactionReview.videoResourceName(Android, LIGHT).shouldBe(null)
    TransactionReview.videoResourceName(Android, DARK).shouldBe(null)
    TransactionReview.videoResourceName(IOS, LIGHT).shouldBe(null)
    TransactionReview.videoResourceName(IOS, DARK).shouldBe(null)
  }

  test("tap bitkey help keeps platform-specific copy and uses coil placement videos") {
    TapBitkey.statements(Android).map { it.title }.shouldBe(
      listOf("FIND THE RIGHT SPOT", "MAKE FULL CONTACT")
    )
    TapBitkey.statements(Jvm).map { it.title }.shouldBe(
      listOf("FIND THE RIGHT SPOT", "MAKE FULL CONTACT")
    )
    TapBitkey.statements(IOS).map { it.title }.shouldBe(
      listOf("TAP ALONG THE TOP EDGE OF YOUR PHONE", "MAKE FULL CONTACT")
    )

    TapBitkey.videoResourceName(Android, LIGHT).shouldBe("coil_placement_android_top_light")
    TapBitkey.videoResourceName(Android, DARK).shouldBe("coil_placement_android_top_dark")
    TapBitkey.videoResourceName(Jvm, LIGHT).shouldBe("coil_placement_android_top_light")
    TapBitkey.videoResourceName(Jvm, DARK).shouldBe("coil_placement_android_top_dark")
    TapBitkey.videoResourceName(IOS, LIGHT).shouldBe("coil_placement_ios_light")
    TapBitkey.videoResourceName(IOS, DARK).shouldBe("coil_placement_ios_dark")
  }

  test("firmware update help uses coil placement videos") {
    FirmwareUpdate.videoResourceName(Android, LIGHT).shouldBe("coil_placement_android_top_light")
    FirmwareUpdate.videoResourceName(Android, DARK).shouldBe("coil_placement_android_top_dark")
    FirmwareUpdate.videoResourceName(IOS, LIGHT).shouldBe("coil_placement_ios_light")
    FirmwareUpdate.videoResourceName(IOS, DARK).shouldBe("coil_placement_ios_dark")
  }
})
