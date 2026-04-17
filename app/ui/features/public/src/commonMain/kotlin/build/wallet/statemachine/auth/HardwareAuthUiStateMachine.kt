package build.wallet.statemachine.auth

import bitkey.account.HardwareType
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HardwareDisplayValidation
import build.wallet.crypto.PublicKey
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.StateMachine
import uniffi.actionproof.Action

/**
 * Describes a privileged action to be verified via action proof.
 * Each variant encapsulates the correct [Action], value, and context bindings
 * so callers don't need to know the low-level payload structure.
 */
sealed interface ActionProofType {
  val action: Action
  val value: String? get() = null
  val extra: Map<String, String> get() = emptyMap()

  /** When true, the action proof header should contain only the HW signature (no app signature). */
  val hwSignatureOnly: Boolean get() = false

  companion object {
    /**
     * Returns the trimmed name if it's displayable on the hardware screen, or null.
     * Uses [HardwareDisplayValidation.isHwDisplayable] which checks for HW font
     * compatibility (Latin chars, digits, common punctuation, max 64 chars).
     * Legacy aliases with emoji, CJK, Cyrillic, excessive length, or blank names
     * fall back to null, causing the hardware to show a generic "Confirm action"
     * screen instead.
     */
    fun validatedNameOrNull(name: String?): String? {
      val trimmed = name?.trim() ?: return null
      if (trimmed.isEmpty()) return null
      return if (HardwareDisplayValidation.isHwDisplayable(trimmed)) trimmed else null
    }
  }

  // -- Mobile Pay / Spend without hardware --

  /** Set the mobile pay (spend without hardware) limit. */
  data class SetMobilePayLimit(val limit: String, val currency: String) : ActionProofType {
    override val action: Action = Action.SET_SPEND_WITHOUT_HARDWARE
    override val value: String = limit
    override val extra: Map<String, String> = mapOf("currency" to currency)
  }

  /** Disable mobile pay (spend without hardware). */
  data object DisableMobilePay : ActionProofType {
    override val action: Action = Action.DISABLE_SPEND_WITHOUT_HARDWARE
  }

  // -- Verification threshold --

  /** Set the verification threshold amount. */
  data class SetVerificationThreshold(val threshold: String) : ActionProofType {
    override val action: Action = Action.SET_VERIFICATION_THRESHOLD
    override val value: String = threshold
  }

  // -- Recovery email --

  /** Set a recovery email (whether new or replacing an existing one). */
  data class SetRecoveryEmail(val email: String, val touchpointId: String) : ActionProofType {
    override val action: Action = Action.SET_RECOVERY_EMAIL
    override val value: String = email
    override val extra: Map<String, String> = mapOf("eid" to touchpointId)
  }

  /** Disable (remove) an existing recovery email. */
  data class DisableRecoveryEmail(
    val currentEmail: String,
    val touchpointId: String,
  ) : ActionProofType {
    override val action: Action = Action.DISABLE_RECOVERY_EMAIL
    override val extra: Map<String, String> = mapOf("eid" to touchpointId)
  }

  // -- Recovery phone --

  /** Set a recovery phone number (whether new or replacing an existing one). */
  data class SetRecoveryPhone(val phone: String, val touchpointId: String) : ActionProofType {
    override val action: Action = Action.SET_RECOVERY_PHONE
    override val value: String = phone
    override val extra: Map<String, String> = mapOf("eid" to touchpointId)
  }

  /** Disable (remove) an existing recovery phone number. */
  data class DisableRecoveryPhone(
    val currentPhone: String,
    val touchpointId: String,
  ) : ActionProofType {
    override val action: Action = Action.DISABLE_RECOVERY_PHONE
    override val extra: Map<String, String> = mapOf("eid" to touchpointId)
  }

  // -- Push notifications --

  /** Disable recovery push notifications. */
  data object DisablePushNotifications : ActionProofType {
    override val action: Action = Action.DISABLE_RECOVERY_PUSH_NOTIFICATIONS
  }

  // -- Account deletion (cancel account creation) --

  /** Delete an account during onboarding cancellation. */
  data class DeleteAccount(val accountId: String) : ActionProofType {
    override val action: Action = Action.DELETE_ACCOUNT
    override val extra: Map<String, String> = mapOf("eid" to accountId)
  }

  // -- Recovery contacts (Trusted Contacts) --

  /** Create a new recovery contact invitation (entity ID not yet assigned). */
  data class CreateRecoveryContact(val name: String) : ActionProofType {
    override val action: Action = Action.ADD_RECOVERY_CONTACT
    override val value: String? = validatedNameOrNull(name)
  }

  /** Reinvite an existing recovery contact (entity ID is known). */
  data class ReinviteRecoveryContact(val name: String, val entityId: String) : ActionProofType {
    override val action: Action = Action.ADD_RECOVERY_CONTACT
    override val value: String? = validatedNameOrNull(name)
    override val extra: Map<String, String> = mapOf("eid" to entityId)
  }

  /** Customer removes a recovery contact from their wallet. */
  data class RemoveRecoveryContact(
    val entityId: String,
    val name: String? = null,
  ) : ActionProofType {
    override val action: Action = Action.REMOVE_RECOVERY_CONTACT
    override val value: String? = validatedNameOrNull(name)
    override val extra: Map<String, String> = mapOf("eid" to entityId)
  }

  /** Trusted contact removes themselves as someone's recovery contact. */
  data class RemoveRecoveryCustomer(
    val entityId: String,
    val name: String? = null,
  ) : ActionProofType {
    override val action: Action = Action.REMOVE_RECOVERY_CUSTOMER
    override val value: String? = validatedNameOrNull(name)
    override val extra: Map<String, String> = mapOf("eid" to entityId)
  }

  // -- Beneficiaries (Inheritance) --

  /** Create a new beneficiary invitation (entity ID not yet assigned). */
  data class CreateBeneficiary(val name: String) : ActionProofType {
    override val action: Action = Action.ADD_BENEFICIARY
    override val value: String? = validatedNameOrNull(name)
  }

  /** Reinvite an existing beneficiary (entity ID is known). */
  data class ReinviteBeneficiary(val name: String, val entityId: String) : ActionProofType {
    override val action: Action = Action.ADD_BENEFICIARY
    override val value: String? = validatedNameOrNull(name)
    override val extra: Map<String, String> = mapOf("eid" to entityId)
  }

  /** Customer removes a beneficiary from their wallet. */
  data class RemoveBeneficiary(val entityId: String, val name: String? = null) : ActionProofType {
    override val action: Action = Action.REMOVE_BENEFICIARY
    override val value: String? = validatedNameOrNull(name)
    override val extra: Map<String, String> = mapOf("eid" to entityId)
  }

  /** Trusted contact (beneficiary) removes themselves from a benefactor's wallet. */
  data class RemoveBenefactor(val entityId: String, val name: String? = null) : ActionProofType {
    override val action: Action = Action.REMOVE_BENEFACTOR
    override val value: String? = validatedNameOrNull(name)
    override val extra: Map<String, String> = mapOf("eid" to entityId)
  }

  // -- Cancel D&N Recovery --

  /** Cancel an in-progress lost app D&N recovery. */
  data object CancelLostAppRecovery : ActionProofType {
    override val action: Action = Action.CANCEL_LOST_APP_RECOVERY
    override val hwSignatureOnly: Boolean = true
  }

  /** Cancel a conflicting D&N recovery (started by another app instance). */
  data object CancelConflictingRecovery : ActionProofType {
    override val action: Action = Action.CANCEL_CONFLICTING_RECOVERY
    override val hwSignatureOnly: Boolean = true
  }

  // -- Auth key rotation --

  /** Rotate app auth keys (requires W3 hardware confirmation). */
  data object RotateAuthKeys : ActionProofType {
    override val action: Action = Action.ROTATE_APP_AUTH_KEYS
  }

  /** Authorize uploading descriptor backups. */
  data object UpdateDescriptorBackups : ActionProofType {
    override val action: Action = Action.UPDATE_DESCRIPTOR_BACKUPS
  }

  /** Authorize rotating the active spending keyset. */
  data class RotateSpendingKeyset(
    val keysetId: String,
  ) : ActionProofType {
    override val action: Action = Action.ROTATE_SPENDING_KEYSET
    override val extra: Map<String, String> = mapOf("eid" to keysetId)
  }
}

/**
 * Unified state machine for obtaining hardware authorization for privileged actions.
 *
 * Both paths begin by refreshing auth tokens, then branch based on hardware type:
 * - **W1**: Signs the access token via NFC,
 *   producing [PrivilegedActionProof.HwKeyProof].
 * - **W3**: Builds a structured action proof payload, app-signs it, then hardware-signs it via NFC,
 *   producing [PrivilegedActionProof.HwSignedAction].
 *
 * Callers don't need to know the hardware type — they just describe the action and get back a
 * [PrivilegedActionProof] on success.
 */
interface HardwareAuthUiStateMachine : StateMachine<HardwareAuthUiProps, ScreenModel>

/**
 * Props for [HardwareAuthUiStateMachine].
 *
 * @property fullAccountId The account ID for auth token refresh.
 * @property hardwareType The hardware type (W1 or W3) to determine the signing path.
 * @property appAuthKey The app global auth key used for app-signing the action proof (W3 path).
 * @property useRecoveryPubKey When true, NFC hardware verification checks against the recovery
 *   hardware key instead of the paired hardware key. Used during lost-app recovery flows.
 * @property actionProofType Describes the privileged action being authorized.
 *   Used by W3 to build the action proof payload. Ignored by W1 (which signs the access token).
 * @property segment App segment for error tracking.
 * @property actionDescription Description of the action for error tracking.
 * @property screenPresentationStyle Presentation style for screens.
 * @property onSuccess Called with the [PrivilegedActionProof] on success.
 * @property onBack Called when the user backs out of the flow.
 * @property onTokenRefresh Optional override: screen model to show while auth tokens
 *   are refreshing. When null, a default loading screen is shown.
 * @property onTokenRefreshError Optional override: screen model to show if auth token
 *   refresh fails. When null, a default error screen is shown.
 */
data class HardwareAuthUiProps(
  val fullAccountId: FullAccountId,
  val hardwareType: HardwareType,
  val appAuthKey: PublicKey<AppGlobalAuthKey>,
  val useRecoveryPubKey: Boolean = false,
  val actionProofType: ActionProofType,
  val segment: AppSegment,
  val actionDescription: String,
  val screenPresentationStyle: ScreenPresentationStyle,
  val onSuccess: (PrivilegedActionProof) -> Unit,
  val onBack: () -> Unit,
  val onTokenRefresh: (() -> ScreenModel)? = null,
  val onTokenRefreshError: (
    (
      isConnectivityError: Boolean,
      onRetry: () -> Unit,
    ) -> ScreenModel
  )? = null,
  /**
   * When true, the W3 device locks after showing the confirmation screen.
   * Set to false when this action proof is part of a larger multi-tap flow (e.g. onboarding)
   * where additional hardware taps will follow — the device will still show the confirmation
   * screen but return to the scan/ready state instead of locking.
   */
  val shouldLock: Boolean = true,
) {
  constructor(
    account: FullAccount,
    actionProofType: ActionProofType,
    segment: AppSegment,
    actionDescription: String,
    screenPresentationStyle: ScreenPresentationStyle,
    onSuccess: (PrivilegedActionProof) -> Unit,
    onBack: () -> Unit,
    onTokenRefresh: (() -> ScreenModel)? = null,
    onTokenRefreshError: (
      (
        isConnectivityError: Boolean,
        onRetry: () -> Unit,
      ) -> ScreenModel
    )? = null,
    shouldLock: Boolean = true,
  ) : this(
    fullAccountId = account.accountId,
    hardwareType = account.config.hardwareType,
    appAuthKey = account.keybox.activeAppKeyBundle.authKey,
    actionProofType = actionProofType,
    segment = segment,
    actionDescription = actionDescription,
    screenPresentationStyle = screenPresentationStyle,
    onSuccess = onSuccess,
    onBack = onBack,
    onTokenRefresh = onTokenRefresh,
    onTokenRefreshError = onTokenRefreshError,
    shouldLock = shouldLock
  )
}
