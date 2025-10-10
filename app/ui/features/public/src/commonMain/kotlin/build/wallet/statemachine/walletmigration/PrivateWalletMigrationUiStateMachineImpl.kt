package build.wallet.statemachine.walletmigration

import androidx.compose.runtime.*
import bitkey.auth.AccountAuthTokens
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.nfc.platform.signAccessToken
import build.wallet.platform.random.UuidGenerator
import build.wallet.statemachine.auth.RefreshAuthTokensProps
import build.wallet.statemachine.auth.RefreshAuthTokensUiStateMachine
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.walletmigration.PrivateWalletMigrationUiState.CreatingKeyset
import build.wallet.statemachine.walletmigration.PrivateWalletMigrationUiState.HardwareInitiation
import build.wallet.statemachine.walletmigration.PrivateWalletMigrationUiState.Introduction

@BitkeyInject(ActivityScope::class)
class PrivateWalletMigrationUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val refreshAuthTokensUiStateMachine: RefreshAuthTokensUiStateMachine,
  private val uuidGenerator: UuidGenerator,
) : PrivateWalletMigrationUiStateMachine {
  @Composable
  override fun model(props: PrivateWalletMigrationUiProps): ScreenModel {
    var uiState by remember {
      mutableStateOf<PrivateWalletMigrationUiState>(Introduction)
    }

    return when (val current = uiState) {
      is Introduction -> {
        ScreenModel(
          body = PrivateWalletMigrationIntroBodyModel(
            onBack = props.onExit,
            onContinue = {
              uiState = PrivateWalletMigrationUiState.RefreshingAuthTokens
            },
            onLearnMore = {
              // TODO: Implement learn more navigation when ready
            }
          )
        )
      }

      is PrivateWalletMigrationUiState.RefreshingAuthTokens -> {
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
              uiState = Introduction
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
              val newKey = commands.getNextSpendingKey(session, existingKeys, props.account.keybox.config.bitcoinNetworkType)

              KeysetInitiationNfcResult(
                proofOfPossession = proofOfPossession,
                newHwKeys = HwKeyBundle(
                  localId = uuidGenerator.random(),
                  spendingKey = newKey,
                  authKey = commands.getAuthenticationKey(session),
                  networkType = props.account.keybox.config.bitcoinNetworkType
                )
              )
            },
            onSuccess = {
              uiState = CreatingKeyset(it)
            },
            onCancel = {
              uiState = Introduction
            },
            screenPresentationStyle = ScreenPresentationStyle.Root,
            eventTrackerContext = NfcEventTrackerScreenIdContext.HW_PROOF_OF_POSSESSION
          )
        )
      }

      is CreatingKeyset -> {
        ScreenModel(
          body = LoadingSuccessBodyModel(
            state = LoadingSuccessBodyModel.State.Loading,
            message = "Creating your private wallet...",
            id = null,
            primaryButton = null,
            secondaryButton = null
          )
        )
      }

      is PrivateWalletMigrationUiState.PreparingSweep -> {
        ScreenModel(
          body = LoadingSuccessBodyModel(
            state = LoadingSuccessBodyModel.State.Loading,
            message = "Preparing to move your funds...",
            id = null,
            primaryButton = null,
            secondaryButton = null
          )
        )
      }

      is PrivateWalletMigrationUiState.BroadcastingTransaction -> {
        ScreenModel(
          body = LoadingSuccessBodyModel(
            state = LoadingSuccessBodyModel.State.Loading,
            message = "Broadcasting transaction...",
            id = null,
            primaryButton = null,
            secondaryButton = null
          )
        )
      }

      is PrivateWalletMigrationUiState.WaitingForConfirmations -> {
        val waitingState = uiState as PrivateWalletMigrationUiState.WaitingForConfirmations
        ScreenModel(
          body = LoadingSuccessBodyModel(
            state = LoadingSuccessBodyModel.State.Loading,
            message = "Waiting for confirmations... (${waitingState.confirmations}/${waitingState.requiredConfirmations})",
            id = null,
            primaryButton = null,
            secondaryButton = null
          )
        )
      }

      is PrivateWalletMigrationUiState.Finalizing -> {
        ScreenModel(
          body = LoadingSuccessBodyModel(
            state = LoadingSuccessBodyModel.State.Loading,
            message = "Finalizing your private wallet...",
            id = null,
            primaryButton = null,
            secondaryButton = null
          )
        )
      }

      is PrivateWalletMigrationUiState.Success -> {
        ScreenModel(
          body = PrivateWalletMigrationCompleteBodyModel(
            onBack = props.onExit,
            onComplete = props.onExit
          )
        )
      }

      is PrivateWalletMigrationUiState.Error -> {
        ScreenModel(
          body = ErrorFormBodyModel(
            title = "Migration Error",
            subline = "There was an error migrating your wallet. Please try again.",
            primaryButton = ButtonDataModel(
              text = "Retry",
              onClick = {
                uiState = Introduction
              }
            ),
            secondaryButton = ButtonDataModel(
              text = "Cancel",
              onClick = props.onExit
            ),
            eventTrackerScreenId = null
          )
        )
      }
    }
  }
}

private sealed interface PrivateWalletMigrationUiState {
  /**
   * Initial introduction screen explaining the migration.
   */
  data object Introduction : PrivateWalletMigrationUiState

  /**
   * Refreshing auth tokens before initiating Hardware proof-of-possession.
   */
  data object RefreshingAuthTokens : PrivateWalletMigrationUiState

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
    val proofOfPossession: HwFactorProofOfPossession,
    val newHwKeys: HwKeyBundle,
  ) : PrivateWalletMigrationUiState {
    constructor(args: KeysetInitiationNfcResult) : this(
      proofOfPossession = args.proofOfPossession,
      newHwKeys = args.newHwKeys
    )
  }

  /**
   * Preparing sweep transaction.
   */
  data object PreparingSweep : PrivateWalletMigrationUiState

  /**
   * Broadcasting sweep transaction.
   */
  data class BroadcastingTransaction(val txid: String) : PrivateWalletMigrationUiState

  /**
   * Waiting for transaction confirmations.
   */
  data class WaitingForConfirmations(
    val txid: String,
    val confirmations: Int,
    val requiredConfirmations: Int,
  ) : PrivateWalletMigrationUiState

  /**
   * Finalizing migration.
   */
  data object Finalizing : PrivateWalletMigrationUiState

  /**
   * Migration completed successfully.
   */
  data object Success : PrivateWalletMigrationUiState

  /**
   * Migration failed with error.
   */
  data object Error : PrivateWalletMigrationUiState
}
