package build.wallet.statemachine.data.keybox

import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.cloud.backup.CloudBackup
import build.wallet.statemachine.data.account.CreateFullAccountData
import build.wallet.statemachine.data.account.OnboardConfigData
import build.wallet.statemachine.data.keybox.address.KeyboxAddressData
import build.wallet.statemachine.data.keybox.config.TemplateKeyboxConfigData.LoadedTemplateKeyboxConfigData
import build.wallet.statemachine.data.keybox.transactions.FullAccountTransactionsData.FullAccountTransactionsLoadedData
import build.wallet.statemachine.data.mobilepay.MobilePayData
import build.wallet.statemachine.data.notifications.NotificationTouchpointData
import build.wallet.statemachine.data.recovery.conflict.NoLongerRecoveringData
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData

/**
 * Describes Account state in the app. This could be Customer or Trusted Contact
 * account.
 */
sealed interface AccountData {
  /**
   * Checking to see if we have an active account.
   */
  data object CheckingActiveAccountData : AccountData

  /**
   * Indicates that there is no active account.
   */
  sealed interface NoActiveAccountData : AccountData {
    /**
     * We are checking if there is any persisted recovery or onboarding at app launch.
     */
    data object CheckingRecoveryOrOnboarding : NoActiveAccountData

    /**
     * Indicates that there is no active account and no onboarding or recovery in progress.
     *
     * @property [templateKeyboxConfigData] template [KeyboxConfig] to be used for creating or
     * recovering Keybox matching that config.
     */
    data class GettingStartedData(
      val newAccountOnboardConfigData: OnboardConfigData,
      val templateKeyboxConfigData: LoadedTemplateKeyboxConfigData,
      val startFullAccountCreation: () -> Unit,
      val startLiteAccountCreation: () -> Unit,
      val startRecovery: () -> Unit,
      val startEmergencyAccessRecovery: () -> Unit,
      val isNavigatingBack: Boolean,
    ) : NoActiveAccountData

    /**
     * Indicates that there is an ongoing "Lost App" recovery in progress for a [FullAccount].
     *
     * @property [templateKeyboxConfig] template [KeyboxConfig] to be used to recover Keybox
     * matching that config. Note that we are not passing [LoadedTemplateKeyboxConfigData]
     * at this point because we should not allow downstream state machines to change config
     * template at this point.
     */
    data class RecoveringAccountData(
      val templateKeyboxConfig: KeyboxConfig,
      val lostAppRecoveryData: LostAppRecoveryData,
    ) : NoActiveAccountData

    /**
     * Indicates that there is an ongoing "Lost App" recovery in progress for a [LiteAccount].
     */
    data class RecoveringLiteAccountData(
      val cloudBackup: CloudBackup,
      val onAccountCreated: (Account) -> Unit,
      val onExit: () -> Unit,
    ) : NoActiveAccountData

    /**
     * Indicates that the account is attempting recovery with the Emergency Access Kit.
     */
    data class RecoveringAccountWithEmergencyAccessKit(
      val templateKeyboxConfig: KeyboxConfig,
      val onExit: () -> Unit,
    ) : NoActiveAccountData

    /**
     * Indicates that there is an ongoing brand new Full Account creation in progress
     *
     * @property templateKeyboxConfig template [KeyboxConfig] to be used for creating new keybox.
     * Note that we are not passing [LoadedTemplateKeyboxConfigData] at this point because
     * we should not allow downstream state machines to change config template at this point.
     * @property createFullAccountData provides data for the state of account creation.
     */
    data class CreatingFullAccountData(
      val templateKeyboxConfig: KeyboxConfig,
      val createFullAccountData: CreateFullAccountData,
    ) : NoActiveAccountData

    /**
     * Indicates that there is an ongoing new lite account creation in progress
     *
     * @property onRollback Callback to rollback the creation of the lite account.
     * @property templateKeyboxConfig template [KeyboxConfig] to be used for creating new keybox.
     * @property inviteCode The invite code used to create the lite account. Present when coming from
     * a trusted contact invite deep link.
     * @property onAccountCreated Invoked when the account creation is complete.
     */
    data class CreatingLiteAccountData(
      val onRollback: () -> Unit,
      val templateKeyboxConfig: KeyboxConfig,
      val inviteCode: String?,
      val onAccountCreated: (LiteAccount) -> Unit,
    ) : NoActiveAccountData

    /**
     * Indicates that the application is loading cloud backup data to determine how to proceed.
     *
     * @property intent Indicator for the user's intended action when starting onboarding or recovery. Note that this
     *   may not determine the final destination of the experience, depending on the type of cloud backup data found.
     * @property onStartLiteAccountCreation Invoked when the application should proceed to create a new lite account.
     * @property onStartLiteAccountRecovery Invoked when the application should start recovery for an existing lite
     * account from the backup provided.
     * @property onStartCloudRecovery Invoked when the application should start recovery for an existing full account
     * from the backup provided.
     * @property onStartLostAppRecovery Invoked when the application should start recovery for an existing account
     * using hardware recovery.
     * @property onExit Invoked to return back to the previous screen.
     */
    data class CheckingCloudBackupData(
      val intent: StartIntent,
      val onStartLiteAccountCreation: () -> Unit,
      val onStartLiteAccountRecovery: (CloudBackup) -> Unit,
      val onStartCloudRecovery: (CloudBackup) -> Unit,
      val onStartLostAppRecovery: () -> Unit,
      val onImportEmergencyAccessKit: () -> Unit,
      val onExit: () -> Unit,
    ) : NoActiveAccountData
  }

  /**
   * Navigation intent used to inform the destination of the getting
   * started experience.
   */
  enum class StartIntent {
    /**
     * Indicates that the user started onboarding with the intent to
     * restore an existing Bitkey account.
     */
    RestoreBitkey,

    /**
     * Indicates that the user started onboarding with the intent to
     * become a trusted contact.
     */
    BeTrustedContact,
  }

  /**
   * An active Full Account is present.
   */
  sealed interface HasActiveFullAccountData : AccountData {
    /**
     *
     * A keybox associated with the Full Account.
     */
    val account: FullAccount

    /**
     * Active Full Account found, loading its data.
     */
    data class LoadingActiveFullAccountData(
      override val account: FullAccount,
    ) : HasActiveFullAccountData

    /**
     * An active Full Account is present.
     *
     * @property transactionsData provides data for balance and transactions for the active spending
     * keyset for current keybox.
     * @property mobilePayData provides data for Mobile Pay for the active account.
     * @property lostHardwareRecoveryData provides Hardware Recovery data for the active account.
     * @property notificationTouchpointData provides data for notifications for the active account.
     */
    data class ActiveFullAccountLoadedData(
      override val account: FullAccount,
      val spendingWallet: SpendingWallet,
      val addressData: KeyboxAddressData,
      val transactionsData: FullAccountTransactionsLoadedData,
      val mobilePayData: MobilePayData,
      val lostHardwareRecoveryData: LostHardwareRecoveryData,
      val notificationTouchpointData: NotificationTouchpointData,
      val isCompletingSocialRecovery: Boolean,
    ) : HasActiveFullAccountData
  }

  /**
   * An active Lite Account is present.
   *
   * @param accountUpgradeOnboardConfigData: Configuration for the onboarding flow for a Lite
   *  account to upgrade to a Full account.
   * @param accountUpgradeTemplateKeyboxConfigData: Configuration for the keybox for when the Lite
   *  account upgrades to a Full account (and creates a keybox).
   * @property onUpgradeAccount Callback for when the Lite Account wants to start the upgrade
   *  process to become a full account. Currently, this needs to happen all within the context
   *  of the data state machines because they are so reactive to account / keybox state. See
   *  BKR-643.
   */
  data class HasActiveLiteAccountData(
    val account: LiteAccount,
    val accountUpgradeOnboardConfigData: OnboardConfigData.LoadedOnboardConfigData,
    val accountUpgradeTemplateKeyboxConfigData: LoadedTemplateKeyboxConfigData,
    val onUpgradeAccount: () -> Unit,
  ) : AccountData

  /**
   * The keybox was recovering but it was canceled elsewhere, notify customer
   */
  data class NoLongerRecoveringFullAccountData(
    val data: NoLongerRecoveringData,
  ) : AccountData

  /**
   * The current Full Account has a recovery happening elsewhere, notifying customer.
   */
  data class SomeoneElseIsRecoveringFullAccountData(
    val data: SomeoneElseIsRecoveringData,
    val keyboxConfig: KeyboxConfig,
    val fullAccountId: FullAccountId,
  ) : AccountData
}
