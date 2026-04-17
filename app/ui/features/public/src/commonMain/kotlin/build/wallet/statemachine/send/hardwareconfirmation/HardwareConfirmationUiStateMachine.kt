package build.wallet.statemachine.send.hardwareconfirmation

import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.analytics.events.screen.id.HardwareConfirmationEventTrackerScreenId
import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.bitcoin.address.BitcoinAddress
import dev.zacsweers.redacted.annotations.Redacted
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

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
      helpContent = HardwareConfirmationHelpContent.TransactionReview
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
      canceledScreenId = HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_CANCELED_FWUP
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
  }
}

data class HardwareConfirmationHelpContent(
  val headline: String,
  val statements: List<Statement>,
) {
  data class Statement(
    val title: String,
    val body: String,
  )

  companion object {
    val TransactionReview = HardwareConfirmationHelpContent(
      headline = "How it works",
      statements = listOf(
        Statement(
          title = "CHECK THE ADDRESS",
          body = "Compare the address shown on your Bitkey to the source where the recipient address was obtained, not to what’s shown in the Bitkey app."
        ),
        Statement(
          title = "CHECK THE AMOUNT",
          body = "Ensure the amount (including the fee) is what you meant to send."
        ),
        Statement(
          title = "FINISH ON YOUR PHONE",
          body = "Once you’ve reviewed the information on your Bitkey, come back here to send the transaction or cancel if something doesn’t look right."
        )
      )
    )
  }
}
