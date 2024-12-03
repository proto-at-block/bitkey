package build.wallet.di

import build.wallet.auth.FullAccountAuthKeyRotationService
import build.wallet.auth.LiteAccountCreator
import build.wallet.cloud.backup.CloudBackupHealthRepository
import build.wallet.cloud.backup.CloudBackupRepository
import build.wallet.cloud.backup.FullAccountCloudBackupCreator
import build.wallet.cloud.backup.LiteAccountCloudBackupCreator
import build.wallet.cloud.backup.csek.CsekGenerator
import build.wallet.cloud.backup.health.FullAccountCloudBackupRepairer
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.cloud.store.CloudFileStore
import build.wallet.cloud.store.CloudKeyValueStore
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.debug.AppDataDeleter
import build.wallet.debug.cloud.CloudBackupDeleter
import build.wallet.f8e.onboarding.CreateAccountKeysetF8eClient
import build.wallet.home.GettingStartedTaskDao
import build.wallet.nfc.NfcTransactor
import build.wallet.nfc.transaction.PairingTransactionProvider
import build.wallet.onboarding.CreateFullAccountService
import build.wallet.onboarding.CreateSoftwareWalletService
import build.wallet.onboarding.OnboardAccountService
import build.wallet.onboarding.OnboardingKeyboxHardwareKeysDao
import build.wallet.recovery.RecoverySyncer
import build.wallet.recovery.sweep.SweepService
import build.wallet.relationships.RelationshipsCryptoFake
import build.wallet.relationships.RelationshipsKeysRepository
import build.wallet.statemachine.biometric.BiometricPromptUiStateMachine
import build.wallet.statemachine.data.keybox.AccountDataStateMachine
import build.wallet.statemachine.data.keybox.TrustedContactCloudBackupRefresher
import build.wallet.statemachine.data.recovery.sweep.SweepDataStateMachine
import build.wallet.statemachine.partnerships.AddBitcoinUiStateMachine
import build.wallet.statemachine.partnerships.purchase.PartnershipsPurchaseUiStateMachine
import build.wallet.statemachine.partnerships.transfer.PartnershipsTransferUiStateMachine
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIStateMachine
import build.wallet.statemachine.recovery.lostapp.LostAppRecoveryUiStateMachine
import build.wallet.statemachine.recovery.losthardware.LostHardwareRecoveryUiStateMachine
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementUiStateMachine
import build.wallet.statemachine.recovery.socrec.add.AddingTrustedContactUiStateMachine
import build.wallet.statemachine.recovery.socrec.challenge.RecoveryChallengeUiStateMachine
import build.wallet.statemachine.root.AppUiStateMachine
import build.wallet.statemachine.settings.full.feedback.FeedbackUiStateMachine

/**
 * Object graph that provides dependencies which are still used by platform apps.
 * As more logic is moved into shared state machines, the footprint of this interface will shrink -
 * ultimate goal of this interface is to only provide [AppUiStateMachine] and perhaps few other
 * dependencies.
 *
 * Scoped to the application's lifecycle.
 */
interface ActivityComponent {
  val accountDataStateMachine: AccountDataStateMachine
  val addBitcoinUiStateMachine: AddBitcoinUiStateMachine
  val addingTcsUiStateMachine: AddingTrustedContactUiStateMachine
  val appDataDeleter: AppDataDeleter
  val appUiStateMachine: AppUiStateMachine
  val biometricPromptUiStateMachine: BiometricPromptUiStateMachine
  val cloudBackupDao: CloudBackupDao
  val cloudBackupDeleter: CloudBackupDeleter
  val cloudBackupHealthRepository: CloudBackupHealthRepository
  val cloudBackupRefresher: TrustedContactCloudBackupRefresher
  val cloudBackupRepairer: FullAccountCloudBackupRepairer
  val cloudBackupRepository: CloudBackupRepository
  val cloudFileStore: CloudFileStore
  val cloudKeyValueStore: CloudKeyValueStore
  val cloudStoreAccountRepository: CloudStoreAccountRepository
  val createAccountKeysetF8eClient: CreateAccountKeysetF8eClient
  val createFullAccountService: CreateFullAccountService
  val createSoftwareWalletService: CreateSoftwareWalletService
  val csekGenerator: CsekGenerator
  val feedbackUiStateMachine: FeedbackUiStateMachine
  val fullAccountAuthKeyRotationService: FullAccountAuthKeyRotationService
  val fullAccountCloudBackupCreator: FullAccountCloudBackupCreator
  val gettingStartedTaskDao: GettingStartedTaskDao
  val liteAccountCloudBackupCreator: LiteAccountCloudBackupCreator
  val liteAccountCreator: LiteAccountCreator
  val lostHardwareRecoveryUiStateMachine: LostHardwareRecoveryUiStateMachine
  val nfcTransactor: NfcTransactor
  val onboardAccountService: OnboardAccountService
  val onboardingKeyboxHwAuthPublicKeyDao: OnboardingKeyboxHardwareKeysDao
  val pairingTransactionProvider: PairingTransactionProvider
  val partnershipsPurchaseUiStateMachine: PartnershipsPurchaseUiStateMachine
  val partnershipsTransferUiStateMachine: PartnershipsTransferUiStateMachine
  val recoveringKeyboxUiStateMachine: LostAppRecoveryUiStateMachine
  val recoveryChallengeUiStateMachine: RecoveryChallengeUiStateMachine
  val recoverySyncer: RecoverySyncer
  val relationshipsCryptoFake: RelationshipsCryptoFake
  val relationshipsKeysRepository: RelationshipsKeysRepository
  val rotateAuthUIStateMachine: RotateAuthKeyUIStateMachine
  val sweepDataStateMachine: SweepDataStateMachine
  val sweepService: SweepService
  val trustedContactManagementUiStateMachine: TrustedContactManagementUiStateMachine
}
