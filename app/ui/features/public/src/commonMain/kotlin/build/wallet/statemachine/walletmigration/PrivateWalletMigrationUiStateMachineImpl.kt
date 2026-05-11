package build.wallet.statemachine.walletmigration

import androidx.compose.runtime.*
import bitkey.auth.AccountAuthTokens
import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.WalletMigrationEventTrackerScreenId
import build.wallet.analytics.v1.Action
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.bitcoin.transactions.getTransactionData
import build.wallet.bitcoin.utxo.UtxoConsolidationContext
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.CloudBackupV3
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.csek.SekGenerator
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.feature.flags.UtxoMaxConsolidationCountFeatureFlag
import build.wallet.money.BitcoinMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.nfc.platform.sealSymmetricKey
import build.wallet.nfc.platform.signAccessToken
import build.wallet.platform.random.UuidGenerator
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.recovery.sweep.SweepContext
import build.wallet.statemachine.auth.RefreshAuthTokensProps
import build.wallet.statemachine.auth.RefreshAuthTokensUiStateMachine
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupProps
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupUiStateMachine
import build.wallet.statemachine.core.*
import build.wallet.statemachine.money.amount.MoneyAmountUiProps
import build.wallet.statemachine.money.amount.MoneyAmountUiStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.recovery.sweep.SweepUiProps
import build.wallet.statemachine.recovery.sweep.SweepUiStateMachine
import build.wallet.statemachine.send.NetworkFeesInfoSheetModel
import build.wallet.statemachine.utxo.UtxoConsolidationProps
import build.wallet.statemachine.utxo.UtxoConsolidationUiStateMachine
import build.wallet.statemachine.walletmigration.PrivateWalletMigrationUiState.*
import build.wallet.wallet.migration.MigrationError
import build.wallet.wallet.migration.MigrationProgress
import build.wallet.wallet.migration.MigrationService
import build.wallet.wallet.migration.MigrationType
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.launch

@BitkeyInject(ActivityScope::class)
class PrivateWalletMigrationUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val refreshAuthTokensUiStateMachine: RefreshAuthTokensUiStateMachine,
  private val sweepUiStateMachine: SweepUiStateMachine,
  private val utxoConsolidationUiStateMachine: UtxoConsolidationUiStateMachine,
  private val uuidGenerator: UuidGenerator,
  private val migrationService: MigrationService,
  private val moneyAmountUiStateMachine: MoneyAmountUiStateMachine,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val eventTracker: EventTracker,
  private val sekGenerator: SekGenerator,
  private val bitcoinWalletService: BitcoinWalletService,
  private val utxoMaxConsolidationCountFeatureFlag: UtxoMaxConsolidationCountFeatureFlag,
  private val fullAccountCloudSignInAndBackupUiStateMachine:
    FullAccountCloudSignInAndBackupUiStateMachine,
  private val cloudBackupDao: CloudBackupDao,
  private val accountService: AccountService,
) : PrivateWalletMigrationUiStateMachine {
  @Suppress("CyclomaticComplexMethod")
  @Composable
  override fun model(props: PrivateWalletMigrationUiProps): ScreenModel {
    var uiState by remember {
      mutableStateOf<PrivateWalletMigrationUiState>(ShowingIntroduction)
    }
    val scope = rememberStableCoroutineScope()
    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()

    var resolvedResumeProgress by remember { mutableStateOf(props.resumeProgress) }
    LaunchedEffect(props.resumeProgress) {
      val resumeProgress = props.resumeProgress
      resolvedResumeProgress = resumeProgress
      if (resumeProgress != null) return@LaunchedEffect

      migrationService.resume(MigrationType.PrivateWalletMigration)
        .onSuccess { progress ->
          resolvedResumeProgress = progress
        }
    }
    val isMigrationInProgress = resolvedResumeProgress?.isInProgress() == true
    val onBack = props.onExit.takeUnless { isMigrationInProgress }

    return when (val current = uiState) {
      is ShowingIntroduction -> {
        ScreenModel(
          body = PrivateWalletMigrationIntroBodyModel(
            onBack = onBack,
            onContinue = {
              when (val progress = resolvedResumeProgress) {
                is MigrationProgress.CloudBackup -> {
                  scope.launch {
                    uiState = cloudBackupUiState(props.account, progress)
                  }
                }
                is MigrationProgress.LocalKeyboxActivation -> {
                  uiState = Sweeping(
                    keybox = progress.currentKeybox,
                    migrationProgress = progress
                  )
                }
                is MigrationProgress.Completed -> {
                  uiState = Success
                }
                else -> {
                  uiState = EstimatingFees
                }
              }
            },
            onLearnHow = {
              uiState = LearnHow
            }
          )
        )
      }

      is EstimatingFees -> {
        LaunchedEffect("check-pending-transactions-utxos-and-estimate-fees") {
          val transactionData = bitcoinWalletService.getTransactionData()

          // First check for unconfirmed UTXOs - block if any exist
          val hasUnconfirmedUtxos = transactionData.utxos.unconfirmed.isNotEmpty()

          if (hasUnconfirmedUtxos) {
            uiState = ShowingPendingTransactionsWarning
          } else {
            val utxoCount = transactionData.utxos.confirmed.size
            val maxUtxos = utxoMaxConsolidationCountFeatureFlag.flagValue().value.value.toInt()

            // If there are too many UTXOs to reasonably sign in a single nfc transaction,
            // show the consolidation required sheet
            if (maxUtxos in 1..<utxoCount) {
              uiState = ShowingUtxoConsolidationRequired(
                utxoCount = utxoCount
              )
            } else {
              // Otherwise, estimate fees and show fee estimate sheet
              migrationService.estimateMigrationFees(props.account)
                .onSuccess { fee ->
                  uiState = ShowingFeeEstimate(fee)
                }
                .onFailure { error ->
                  uiState =
                    if (error is MigrationError.InsufficientFundsForMigration) {
                      ShowingInsufficientFundsWarning
                    } else {
                      Error
                    }
                }
            }
          }
        }

        ScreenModel(
          body = PrivateWalletMigrationIntroBodyModel(
            onBack = onBack,
            onContinue = {},
            onLearnHow = {}
          ),
          bottomSheetModel = SheetModel(
            onClosed = { uiState = ShowingIntroduction },
            body = PrivateWalletMigrationFeeEstimateSheetModel(
              onBack = { uiState = ShowingIntroduction },
              onConfirm = { uiState = RefreshingAuthTokens },
              feeEstimateData = FeeEstimateData.Loading
            )
          )
        )
      }

      is ShowingPendingTransactionsWarning -> {
        ScreenModel(
          body = PrivateWalletMigrationIntroBodyModel(
            onBack = onBack,
            onContinue = {},
            onLearnHow = {}
          ),
          bottomSheetModel = SheetModel(
            onClosed = { uiState = ShowingIntroduction },
            body = PrivateWalletMigrationPendingTransactionsWarningSheetModel(
              onBack = { uiState = ShowingIntroduction },
              onGotIt = { uiState = ShowingIntroduction }
            )
          )
        )
      }

      is ShowingUtxoConsolidationRequired -> {
        ScreenModel(
          body = PrivateWalletMigrationIntroBodyModel(
            onBack = onBack,
            onContinue = {},
            onLearnHow = {}
          ),
          bottomSheetModel = SheetModel(
            onClosed = { uiState = ShowingIntroduction },
            body = PrivateWalletMigrationUtxoConsolidationRequiredSheetModel(
              onBack = { uiState = ShowingIntroduction },
              onContinue = {
                uiState = UtxoConsolidation
              }
            )
          )
        )
      }

      is ShowingFeeEstimate -> {
        val feeAmountFormatted = moneyAmountUiStateMachine.model(
          MoneyAmountUiProps(
            primaryMoney = current.fee,
            secondaryAmountCurrency = fiatCurrency
          )
        )

        ScreenModel(
          body = PrivateWalletMigrationIntroBodyModel(
            onBack = onBack,
            onContinue = {},
            onLearnHow = {}
          ),
          bottomSheetModel = SheetModel(
            onClosed = { uiState = ShowingIntroduction },
            body = PrivateWalletMigrationFeeEstimateSheetModel(
              onBack = { uiState = ShowingIntroduction },
              onConfirm = { uiState = RefreshingAuthTokens },
              feeEstimateData = FeeEstimateData.Loaded(
                estimatedFee = feeAmountFormatted.secondaryAmount,
                estimatedFeeSats = feeAmountFormatted.primaryAmount,
                onNetworkFeesExplainerClick = { uiState = ShowingNetworkFeesInfo(current.fee) }
              )
            )
          )
        )
      }

      is ShowingInsufficientFundsWarning -> {
        ScreenModel(
          body = PrivateWalletMigrationIntroBodyModel(
            onBack = onBack,
            onContinue = {},
            onLearnHow = {}
          ),
          bottomSheetModel = SheetModel(
            onClosed = { uiState = ShowingIntroduction },
            body = PrivateWalletMigrationFeeEstimateSheetModel(
              onBack = { uiState = ShowingIntroduction },
              onConfirm = { uiState = RefreshingAuthTokens },
              feeEstimateData = FeeEstimateData.InsufficientFunds
            )
          )
        )
      }

      is LearnHow -> {
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = "https://bitkey.world/hc/enhanced-wallet-privacy",
              onClose = {
                uiState = ShowingIntroduction
              }
            )
          }
        ).asModalFullScreen()
      }

      is ShowingNetworkFeesInfo -> {
        ScreenModel(
          body = PrivateWalletMigrationIntroBodyModel(
            onBack = onBack,
            onContinue = {},
            onLearnHow = {}
          ),
          bottomSheetModel = SheetModel(
            onClosed = {
              uiState = ShowingIntroduction
            },
            body = NetworkFeesInfoSheetModel(
              onBack = { uiState = ShowingFeeEstimate(current.fee) },
              fromSheet = true
            )
          )
        )
      }

      is UtxoConsolidation -> {
        utxoConsolidationUiStateMachine.model(
          UtxoConsolidationProps(
            account = props.account,
            onConsolidationSuccess = {
              // After consolidation, go back to introduction to try again
              uiState = ShowingIntroduction
            },
            onBack = {
              uiState = ShowingIntroduction
            },
            context = UtxoConsolidationContext.PrivateWalletMigration
          )
        )
      }

      is RefreshingAuthTokens -> {
        refreshAuthTokensUiStateMachine.model(
          RefreshAuthTokensProps(
            fullAccountId = props.account.accountId,
            appAuthKey = props.account.keybox.activeAppKeyBundle.authKey,
            onSuccess = { tokens ->
              uiState = HardwareInitiation(
                tokens = tokens
              )
            },
            onBack = {
              uiState = ShowingIntroduction
            },
            screenPresentationStyle = ScreenPresentationStyle.Root
          )
        )
      }

      is HardwareInitiation -> {
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              val proofOfPossession = HwFactorProofOfPossession(
                commands.signAccessToken(session, current.tokens.accessToken)
              )
              // Use getNextSpendingKey to generate a new hardware key for privacy.
              // Although the server knows the old hardware xpub, hardened derivation at the account level
              // prevents the server from predicting the next key without the parent private key.
              val existingKeys = listOf(props.account.keybox.activeSpendingKeyset.hardwareKey)
              val newKey = commands.getNextSpendingKey(
                session,
                existingKeys,
                props.account.keybox.config.bitcoinNetworkType
              )

              val unsealedSsek = sekGenerator.generate()
              val sealedSsek = commands.sealSymmetricKey(
                session = session,
                key = unsealedSsek.key
              )

              KeysetInitiationNfcResult(
                proofOfPossession = proofOfPossession,
                newHwKeys = HwKeyBundle(
                  localId = uuidGenerator.random(),
                  spendingKey = newKey,
                  authKey = commands.getAuthenticationKey(session),
                  networkType = props.account.keybox.config.bitcoinNetworkType
                ),
                ssek = unsealedSsek,
                sealedSsek = sealedSsek
              )
            },
            onSuccess = {
              uiState = CreatingKeyset(it)
            },
            onCancel = {
              uiState = ShowingIntroduction
            },
            screenPresentationStyle = ScreenPresentationStyle.Root,
            eventTrackerContext = NfcEventTrackerScreenIdContext.HW_PROOF_OF_POSSESSION
          )
        )
      }

      is CreatingKeyset -> {
        LaunchedEffect(Unit) {
          // Start with NotStarted and add the credentials
          val initialState = MigrationProgress.NotStarted(MigrationType.PrivateWalletMigration)
            .next(
              currentKeybox = props.account.keybox,
              newHwSpendingKey = current.nfcData.newHwKeys.spendingKey,
              hwProofOfPossession = current.nfcData.proofOfPossession,
              ssek = current.nfcData.ssek,
              sealedSsek = current.nfcData.sealedSsek
            )

          // Save the hardware key to the DAO for state persistence
          // This is needed before calling proceed() since the DAO expects the HW key to be saved
          var currentState: MigrationProgress = initialState

          // Loop through states until we reach CloudBackup, LocalKeyboxActivation, or Completed
          // CloudBackup requires UI interaction for cloud sign-in
          // LocalKeyboxActivation means we need to sweep (skipping CloudBackup)
          while (currentState !is MigrationProgress.CloudBackup &&
            currentState !is MigrationProgress.LocalKeyboxActivation &&
            currentState !is MigrationProgress.Completed
          ) {
            val result = migrationService.proceed(currentState)
            result.onFailure {
              uiState = Error
              return@LaunchedEffect
            }
            result.onSuccess { nextState ->
              currentState = nextState
            }
          }

          // Transition to the appropriate UI state based on the migration progress
          val terminalState = currentState
          when (terminalState) {
            is MigrationProgress.CloudBackup -> {
              uiState = cloudBackupUiState(
                account = props.account,
                progress = terminalState
              )
            }
            is MigrationProgress.LocalKeyboxActivation -> {
              // Cloud backup was skipped, go directly to sweeping
              uiState = Sweeping(
                keybox = terminalState.currentKeybox,
                migrationProgress = terminalState
              )
            }
            is MigrationProgress.Completed -> {
              // Migration completed without needing cloud backup or sweep UI
              uiState = Success
            }
            else -> {
              // Unexpected state
              uiState = Error
            }
          }
        }

        ScreenModel(
          body = LoadingSuccessBodyModel(
            state = LoadingSuccessBodyModel.State.Loading,
            message = "Creating your private wallet...",
            id = WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_CREATING_KEYSET,
            primaryButton = null,
            secondaryButton = null
          )
        )
      }

      is CloudBackup -> {
        fullAccountCloudSignInAndBackupUiStateMachine.model(
          FullAccountCloudSignInAndBackupProps(
            sealedCsek = current.sealedCsek,
            keybox = current.keybox,
            onBackupFailed = { uiState = Error },
            onBackupSaved = {
              scope.launch {
                // Proceed past the CloudBackup state
                val cloudBackupProgress = current.migrationProgress
                if (cloudBackupProgress != null) {
                  migrationService.proceed(cloudBackupProgress)
                    .onSuccess { nextState ->
                      // After CloudBackup, we expect LocalKeyboxActivation (for PrivateWalletMigration)
                      when (nextState) {
                        is MigrationProgress.LocalKeyboxActivation -> {
                          uiState = Sweeping(
                            keybox = current.keybox,
                            migrationProgress = nextState
                          )
                        }
                        else -> {
                          // For other migration types that might have more steps
                          uiState = Sweeping(keybox = current.keybox)
                        }
                      }
                    }
                    .onFailure {
                      uiState = Error
                    }
                } else {
                  // Fallback for legacy compatibility - no progress tracking
                  uiState = Sweeping(keybox = current.keybox)
                }
              }
            },
            presentationStyle = ScreenPresentationStyle.FullScreen,
            requireAuthRefreshForCloudBackup = false,
            isSkipCloudBackupInstructions = true
          )
        )
      }

      is Sweeping -> {
        sweepUiStateMachine.model(
          SweepUiProps(
            account = FullAccount(current.keybox.fullAccountId, current.keybox),
            sweepContext = SweepContext.PrivateWalletMigration,
            presentationStyle = ScreenPresentationStyle.Root,
            onExit = null,
            onSuccess = {
              // After sweep completes, proceed to finalize the migration
              scope.launch {
                val migrationProgress = current.migrationProgress
                if (migrationProgress != null) {
                  migrationService.proceed(migrationProgress)
                    .onSuccess {
                      uiState = Success
                    }
                    .onFailure {
                      uiState = Error
                    }
                } else {
                  // Fallback for legacy compatibility
                  uiState = Success
                }
              }
            },
            // Don't include these fields which only matter in recovery
            hasAttemptedSweep = false,
            onAttemptSweep = {}
          )
        )
      }

      is Success -> {
        ScreenModel(
          body = PrivateWalletMigrationCompleteBodyModel(
            onBack = {
              scope.launch {
                eventTracker.track(
                  Action.ACTION_APP_PRIVATE_WALLET_MANUAL_UPGRADE
                )
                // Fetch the updated active account so downstream screens use
                // the migrated keybox instead of the stale pre-migration one.
                val updatedAccount = accountService.getAccount<FullAccount>()
                  .get() ?: props.account
                props.onMigrationComplete(updatedAccount)
              }
            }
          )
        )
      }

      is Error -> {
        ScreenModel(
          body = ErrorFormBodyModel(
            title = "Migration Error",
            subline = "There was an error migrating your wallet. Please try again.",
            primaryButton = ButtonDataModel(
              text = "Retry",
              onClick = {
                uiState = ShowingIntroduction
              }
            ),
            secondaryButton = ButtonDataModel(
              text = "Cancel",
              onClick = props.onExit
            ).takeUnless { isMigrationInProgress },
            eventTrackerScreenId = WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_ERROR
          )
        )
      }
    }
  }

  private suspend fun cloudBackupUiState(
    account: FullAccount,
    progress: MigrationProgress.CloudBackup,
  ): PrivateWalletMigrationUiState {
    val existingSealedCsek = cloudBackupDao
      .get(account.accountId.serverId)
      .get()
      ?.let { backup ->
        when (backup) {
          is CloudBackupV2 -> backup.fullAccountFields?.sealedHwEncryptionKey
          is CloudBackupV3 -> backup.fullAccountFields?.sealedHwEncryptionKey
          else -> null
        }
      }

    return CloudBackup(
      sealedCsek = existingSealedCsek,
      keybox = progress.currentKeybox,
      migrationProgress = progress
    )
  }
}

private sealed interface PrivateWalletMigrationUiState {
  /**
   * Initial introduction screen explaining the migration.
   */
  data object ShowingIntroduction : PrivateWalletMigrationUiState

  /**
   * Estimating migration fees, showing loading sheet.
   */
  data object EstimatingFees : PrivateWalletMigrationUiState

  /**
   * Showing fee estimate sheet with calculated fees.
   */
  data class ShowingFeeEstimate(val fee: BitcoinMoney) : PrivateWalletMigrationUiState

  /**
   * Showing insufficient funds warning sheet.
   */
  data object ShowingInsufficientFundsWarning : PrivateWalletMigrationUiState

  /**
   * Opens the browser to the Enhanced Wallet Privacy help center article.
   */
  data object LearnHow : PrivateWalletMigrationUiState

  /**
   * Refreshing auth tokens before initiating Hardware proof-of-possession.
   */
  data object RefreshingAuthTokens : PrivateWalletMigrationUiState

  /**
   * Sheet showing more information about network fees.
   */
  data class ShowingNetworkFeesInfo(val fee: BitcoinMoney) : PrivateWalletMigrationUiState

  /**
   * Initial Hardware tap to initiate new keys and get a proof-of-possession.
   */
  data class HardwareInitiation(
    val tokens: AccountAuthTokens,
  ) : PrivateWalletMigrationUiState

  /**
   * Creating new private keyset.
   */
  data class CreatingKeyset(
    val nfcData: KeysetInitiationNfcResult,
  ) : PrivateWalletMigrationUiState

  /**
   * Sweeping funds from old wallet to new private wallet.
   */
  data class Sweeping(
    val keybox: Keybox,
    val migrationProgress: MigrationProgress.LocalKeyboxActivation? = null,
  ) : PrivateWalletMigrationUiState

  /**
   * Running a cloud backup
   */
  data class CloudBackup(
    val sealedCsek: SealedCsek?,
    val keybox: Keybox,
    val migrationProgress: MigrationProgress.CloudBackup? = null,
  ) : PrivateWalletMigrationUiState

  /**
   * Half sheet telling the user they must consolidate utxos first
   */
  data class ShowingUtxoConsolidationRequired(val utxoCount: Int) : PrivateWalletMigrationUiState

  /**
   * UTXO consolidation required before migrating.
   */
  data object UtxoConsolidation : PrivateWalletMigrationUiState

  /**
   * Showing pending transactions warning
   */
  data object ShowingPendingTransactionsWarning : PrivateWalletMigrationUiState

  /**
   * Migration completed successfully.
   */
  data object Success : PrivateWalletMigrationUiState

  /**
   * Migration failed with error.
   */
  data object Error : PrivateWalletMigrationUiState
}
