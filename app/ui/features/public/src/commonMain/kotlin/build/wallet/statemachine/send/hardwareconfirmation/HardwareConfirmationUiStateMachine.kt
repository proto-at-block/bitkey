package build.wallet.statemachine.send.hardwareconfirmation

import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.analytics.events.screen.id.FwupEventTrackerScreenId
import build.wallet.analytics.events.screen.id.HardwareConfirmationEventTrackerScreenId
import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.theme.Theme
import dev.zacsweers.redacted.annotations.Redacted

/**
 * State machine for the hardware confirmation screen shown during W3 two-tap NFC flows.
 * This screen prompts the user to confirm or cancel the action on their Bitkey device.
 *
 * Different NFC commands can customize the copy shown on this screen by providing
 * different [HardwareConfirmationContent] values via [HardwareConfirmationUiProps.content].
 */
interface HardwareConfirmationUiStateMachine : StateMachine<HardwareConfirmationUiProps, ScreenModel>

/**
 * @property onBack - Handler for navigating back to the previous screen (back button / system back)
 * @property onCancel - Handler for explicit cancellation (cancel button). Shows cancellation screen
 *   before invoking. Defaults to [onBack] for backward compatibility.
 * @property onConfirm - Handler for when user confirms they want to proceed
 * @property content - Copy content to display on the confirmation and cancellation screens
 */
data class HardwareConfirmationUiProps(
  val onBack: () -> Unit,
  val onCancel: () -> Unit = onBack,
  val onConfirm: () -> Unit,
  val content: HardwareConfirmationContent = HardwareConfirmationContent.SignTransaction,
  val isHardwareFake: Boolean = false,
)

/**
 * Content configuration for the hardware confirmation screen.
 * Allows different NFC commands (transaction signing, firmware update, etc.)
 * to provide their own copy for the confirmation and cancellation screens.
 */
data class HardwareConfirmationContent(
  /** Title shown on the confirmation screen (e.g. "Review on your Bitkey") */
  val title: String,
  /** Body text shown on the confirmation screen */
  val body: String,
  /** Text for the confirm/proceed button (e.g. "Continue") */
  val confirmButtonText: String,
  /** Text for the cancel button (e.g. "Cancel") */
  val cancelButtonText: String,
  /** Title shown on the cancellation screen (e.g. "Transaction canceled") */
  val canceledTitle: String,
  /** Body text shown on the cancellation screen */
  val canceledBody: String,
  /** Screen ID for the confirmation screen in analytics */
  val screenId: EventTrackerScreenId = HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION,
  /** Screen ID for the cancellation screen in analytics */
  val canceledScreenId: EventTrackerScreenId = HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_CANCELED,
  /** Optional help content shown from the confirmation screen */
  val helpContent: HardwareConfirmationHelpContent? = null,
  /**
   * Optional recipient address to display on the confirmation screen.
   * When provided, shows a collapsible destination address section.
   */
  @Redacted val recipientAddress: BitcoinAddress? = null,
) {
  companion object {
    /** Content for transaction signing confirmation screens. */
    val SignTransaction = HardwareConfirmationContent(
      title = "Review on your Bitkey",
      body = "Confirm action on device, then continue.",
      confirmButtonText = "Continue",
      cancelButtonText = "Cancel",
      canceledTitle = "Transaction canceled",
      canceledBody = "Make sure you've also canceled the transaction on your Bitkey.",
      screenId = HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_SIGN_TRANSACTION,
      canceledScreenId = HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_CANCELED_SIGN_TRANSACTION,
      helpContent = HardwareConfirmationHelpContent.TransactionReview
    )

    /** Content for send transaction confirmation screens. */
    val SendTransaction = HardwareConfirmationContent(
      title = "Review on your Bitkey",
      body = "Confirm action on device, then continue.",
      confirmButtonText = "Send transaction",
      cancelButtonText = "Cancel",
      canceledTitle = "Transaction canceled",
      canceledBody = "Make sure you've also canceled the transaction on your Bitkey.",
      screenId = SendEventTrackerScreenId.SEND_HARDWARE_CONFIRMATION,
      canceledScreenId = SendEventTrackerScreenId.SEND_HARDWARE_CONFIRMATION_CANCELED,
      helpContent = HardwareConfirmationHelpContent.TransactionReview
    )

    /** Content for UTXO consolidation confirmation screens. */
    val ConsolidateUtxos = HardwareConfirmationContent(
      title = "Review on your Bitkey",
      body = "Confirm action on device, then continue.",
      confirmButtonText = "Consolidate UTXOs",
      cancelButtonText = "Cancel",
      canceledTitle = "Transaction canceled",
      canceledBody = "Make sure you've also canceled the transaction on your Bitkey.",
      screenId = HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_CONSOLIDATE_UTXOS,
      canceledScreenId = HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_CANCELED_CONSOLIDATE_UTXOS,
      helpContent = HardwareConfirmationHelpContent.TapBitkey
    )

    /** Content for firmware update confirmation screens. */
    val FirmwareUpdate = HardwareConfirmationContent(
      title = "Review on your Bitkey",
      body = "Confirm action on device, then continue.",
      confirmButtonText = "Continue",
      cancelButtonText = "Cancel",
      canceledTitle = "Update canceled",
      canceledBody = "Make sure you've also canceled the update on your Bitkey.",
      screenId = HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_FWUP,
      canceledScreenId = HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_CANCELED_FWUP,
      helpContent = HardwareConfirmationHelpContent.FirmwareUpdate
    )

    /** Content for action proof signing confirmation screens. */
    val SignActionProof = HardwareConfirmationContent(
      title = "Review on your Bitkey",
      body = "Confirm action on device, then continue.",
      confirmButtonText = "Continue",
      cancelButtonText = "Cancel",
      canceledTitle = "Action canceled",
      canceledBody = "Make sure you've also canceled the action on your Bitkey.",
      screenId = HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_SIGN_ACTION_PROOF,
      canceledScreenId = HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_CANCELED_SIGN_ACTION_PROOF
    )

    /** Content for lost app recovery confirmation screens. */
    val LostAppRecovery = HardwareConfirmationContent(
      title = "Review on your Bitkey",
      body = "Confirm action on device, then continue.",
      confirmButtonText = "Continue",
      cancelButtonText = "Cancel",
      canceledTitle = "Recovery canceled",
      canceledBody = "Make sure you\u2019ve also canceled recovery on your Bitkey.",
      screenId = HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_RECOVERY,
      canceledScreenId = HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_CANCELED_RECOVERY
    )

    /** Content for device wipe confirmation screens. */
    val WipeDevice = HardwareConfirmationContent(
      title = "Review on your Bitkey",
      body = "Confirm action on device, then continue.",
      confirmButtonText = "Continue",
      cancelButtonText = "Cancel",
      canceledTitle = "Wipe canceled",
      canceledBody = "Make sure you've also canceled the wipe on your Bitkey.",
      screenId = HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_WIPE_DEVICE,
      canceledScreenId = HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_CANCELED_WIPE_DEVICE
    )

    /** Content for lost app recovery sign challenge confirmation screens. */
    val LostAppRecoverySignChallenge = HardwareConfirmationContent(
      title = "Review on your Bitkey",
      body = "Confirm action on device, then continue.",
      confirmButtonText = "Continue",
      cancelButtonText = "Cancel",
      canceledTitle = "Confirmation canceled",
      canceledBody = "Make sure you\u2019ve also canceled on your Bitkey.",
      screenId = HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_RECOVERY_SIGN_CHALLENGE,
      canceledScreenId = HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_CANCELED_RECOVERY_SIGN_CHALLENGE
    )

    /** Content for EEK restoration unseal confirmation screens. */
    val EekRestorationUnseal = HardwareConfirmationContent(
      title = "Review on your Bitkey",
      body = "Confirm action on device, then continue.",
      confirmButtonText = "Continue",
      cancelButtonText = "Cancel",
      canceledTitle = "Decryption canceled",
      canceledBody = "Make sure you\u2019ve also canceled the action on your Bitkey.",
      screenId = HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_EEK_RESTORATION,
      canceledScreenId = HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_CANCELED_EEK_RESTORATION
    )

    /** Content for cloud backup restoration confirmation screens. */
    val CloudBackupRestoration = HardwareConfirmationContent(
      title = "Review on your Bitkey",
      body = "Confirm action on device, then continue.",
      confirmButtonText = "Continue",
      cancelButtonText = "Cancel",
      canceledTitle = "Restoration canceled",
      canceledBody = "Make sure you\u2019ve also canceled restoration on your Bitkey.",
      screenId = HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_CLOUD_BACKUP_RESTORATION,
      canceledScreenId = HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_CANCELED_CLOUD_BACKUP_RESTORATION
    )

    /** Content for stale-keyset-repair unseal confirmation screens. */
    val KeysetRepairUnseal = HardwareConfirmationContent(
      title = "Review on your Bitkey",
      body = "Confirm action on device, then continue.",
      confirmButtonText = "Continue",
      cancelButtonText = "Cancel",
      canceledTitle = "Repair canceled",
      canceledBody = "Make sure you\u2019ve also canceled the action on your Bitkey.",
      screenId = HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_KEYSET_REPAIR_UNSEAL,
      canceledScreenId = HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_CANCELED_KEYSET_REPAIR_UNSEAL
    )

    /** Content for stale-keyset-repair rotate composite confirmation screens. */
    val KeysetRepairRotateHwKey = HardwareConfirmationContent(
      title = "Review on your Bitkey",
      body = "Confirm action on device, then continue.",
      confirmButtonText = "Continue",
      cancelButtonText = "Cancel",
      canceledTitle = "Repair canceled",
      canceledBody = "Make sure you\u2019ve also canceled the action on your Bitkey.",
      screenId = HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_KEYSET_REPAIR_ROTATE_HW_KEY,
      canceledScreenId = HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_CANCELED_KEYSET_REPAIR_ROTATE_HW_KEY
    )
  }
}

data class HardwareConfirmationHelpContent(
  val headline: String,
  private val androidStatements: List<Statement>,
  private val iosStatements: List<Statement> = androidStatements,
  private val androidVideo: ThemedVideo? = null,
  private val iosVideo: ThemedVideo? = androidVideo,
  // Keep this non-null so FormBodyModel.key stays stable even when analytics are suppressed.
  val eventTrackerScreenId: EventTrackerScreenId = SendEventTrackerScreenId.SEND_HARDWARE_CONFIRMATION_HELP,
  val eventTrackerShouldTrack: Boolean = true,
) {
  data class Statement(
    val title: String,
    val body: String,
  )

  data class ThemedVideo(
    val lightResourceName: String,
    val darkResourceName: String,
  ) {
    fun resourceName(theme: Theme): String =
      when (theme) {
        Theme.DARK -> darkResourceName
        Theme.LIGHT -> lightResourceName
      }
  }

  fun statements(devicePlatform: DevicePlatform): List<Statement> =
    when (devicePlatform) {
      DevicePlatform.IOS -> iosStatements
      DevicePlatform.Android,
      DevicePlatform.Jvm,
      -> androidStatements
    }

  fun videoResourceName(
    devicePlatform: DevicePlatform,
    theme: Theme,
  ): String? =
    when (devicePlatform) {
      DevicePlatform.IOS -> iosVideo
      DevicePlatform.Android,
      DevicePlatform.Jvm,
      -> androidVideo
    }?.resourceName(theme)

  companion object {
    private const val MAKE_FULL_CONTACT_BODY =
      "Make sure to bring your Bitkey and phone close enough together to make complete contact. You may need to remove your phone case or any accessories that could obstruct the connection."
    private val SEND_TRANSACTION_REVIEW_STATEMENTS =
      listOf(
        Statement(
          title = "CHECK THE ADDRESS",
          body = "Compare the address shown on your Bitkey to the source where the recipient address was obtained, not to what's shown in the Bitkey app."
        ),
        Statement(
          title = "CHECK THE AMOUNT",
          body = "Ensure the amount (including the fee) is what you meant to send."
        ),
        Statement(
          title = "FINISH ON YOUR PHONE",
          body = "Once you've reviewed the information on your Bitkey, come back here to send the transaction or cancel if something doesn't look right."
        )
      )
    private val TAP_BITKEY_ANDROID_STATEMENTS =
      listOf(
        Statement(
          title = "FIND THE RIGHT SPOT",
          body = "Tap your Bitkey with the screen side of the device facing the back of your phone. If you can't make a connection, try the most common connection points one at a time. Tap near the top third of your phone. If that doesn't work, pull the device away and tap halfway down your phone. If the connection still hasn't been made, pull the device away and tap near the bottom third of your phone."
        ),
        Statement(
          title = "MAKE FULL CONTACT",
          body = MAKE_FULL_CONTACT_BODY
        ),
      )
    private val TAP_BITKEY_IOS_STATEMENTS =
      listOf(
        Statement(
          title = "TAP ALONG THE TOP EDGE OF YOUR PHONE",
          body = "Tap your Bitkey along the top edge of your phone with the screen side of the device facing the back of your phone."
        ),
        Statement(
          title = "MAKE FULL CONTACT",
          body = MAKE_FULL_CONTACT_BODY
        )
      )
    private val TAP_BITKEY_ANDROID_VIDEO = ThemedVideo(
      lightResourceName = "coil_placement_android_top_light",
      darkResourceName = "coil_placement_android_top_dark"
    )
    private val TAP_BITKEY_IOS_VIDEO = ThemedVideo(
      lightResourceName = "coil_placement_ios_light",
      darkResourceName = "coil_placement_ios_dark"
    )

    val TransactionReview = HardwareConfirmationHelpContent(
      headline = "How it works",
      androidStatements = SEND_TRANSACTION_REVIEW_STATEMENTS
    )

    val TapBitkey = HardwareConfirmationHelpContent(
      headline = "How it works",
      androidStatements = TAP_BITKEY_ANDROID_STATEMENTS,
      iosStatements = TAP_BITKEY_IOS_STATEMENTS,
      androidVideo = TAP_BITKEY_ANDROID_VIDEO,
      iosVideo = TAP_BITKEY_IOS_VIDEO,
      eventTrackerShouldTrack = false
    )

    val FirmwareUpdate = HardwareConfirmationHelpContent(
      headline = "How it works",
      androidStatements = TAP_BITKEY_ANDROID_STATEMENTS,
      iosStatements = TAP_BITKEY_IOS_STATEMENTS,
      androidVideo = TAP_BITKEY_ANDROID_VIDEO,
      iosVideo = TAP_BITKEY_IOS_VIDEO,
      eventTrackerScreenId = FwupEventTrackerScreenId.FWUP_HELP
    )
  }
}
