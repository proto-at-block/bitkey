package bitkey.ui.screens.recovery

import androidx.compose.runtime.*
import bitkey.auth.AccountAuthTokens
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.KeysetRepairEventTrackerScreenId
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.cloud.backup.csek.Sek
import build.wallet.cloud.backup.csek.SsekDao
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.nfc.platform.signAccessToken
import build.wallet.nfc.platform.unsealSymmetricKey
import build.wallet.recovery.keyset.KeysetRepairCachedData
import build.wallet.recovery.keyset.KeysetRepairError
import build.wallet.recovery.keyset.PrivateKeysetInfo
import build.wallet.recovery.keyset.SpendingKeysetRepairService
import build.wallet.recovery.sweep.SweepContext
import build.wallet.recovery.sweep.SweepService
import build.wallet.statemachine.auth.RefreshAuthTokensProps
import build.wallet.statemachine.auth.RefreshAuthTokensUiStateMachine
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.recovery.sweep.SweepUiProps
import build.wallet.statemachine.recovery.sweep.SweepUiStateMachine
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

/**
 * Screen for the spending keyset repair flow.
 *
 * This flow is triggered when a keyset mismatch is detected between local and server state,
 * which could happen e.g. after recovering from a stale cloud backup. The flow guides the user through:
 * 1. Understanding what happened
 * 2. Syncing keysets from server
 * 3. Updating cloud backup
 * 4. Sweeping funds from old keysets
 */
data class KeysetRepairScreen(
  val account: FullAccount,
  override val origin: Screen? = null,
) : Screen

@BitkeyInject(ActivityScope::class)
class SpendingKeysetRepairScreenPresenter(
  private val spendingKeysetRepairService: SpendingKeysetRepairService,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val sweepUiStateMachine: SweepUiStateMachine,
  private val sweepService: SweepService,
  private val ssekDao: SsekDao,
  private val refreshAuthTokensUiStateMachine: RefreshAuthTokensUiStateMachine,
) : ScreenPresenter<KeysetRepairScreen> {
  @Composable
  override fun model(
    navigator: Navigator,
    screen: KeysetRepairScreen,
  ): ScreenModel {
    var uiState: State by remember { mutableStateOf(State.CheckingPrivateKeysets) }

    return when (val currentState = uiState) {
      is State.CheckingPrivateKeysets -> {
        LaunchedEffect(Unit) {
          spendingKeysetRepairService.checkPrivateKeysets(screen.account)
            .onSuccess { info ->
              uiState = when (info) {
                is PrivateKeysetInfo.NeedsUnsealing -> State.ShowingExplanation(
                  sealedSsek = info.cachedResponseData.response.wrappedSsek,
                  cachedData = info.cachedResponseData
                )
                is PrivateKeysetInfo.None -> State.ShowingExplanation(
                  sealedSsek = null,
                  cachedData = info.cachedResponseData
                )
              }
            }
            .onFailure { error ->
              uiState = State.ShowingError(error, cachedData = null)
            }
        }

        LoadingBodyModel(
          id = KeysetRepairEventTrackerScreenId.KEYSET_REPAIR_CHECKING_KEYSETS,
          title = "Checking wallet data..."
        ).asModalScreen()
      }

      is State.ShowingExplanation -> {
        ExplanationFormBodyModel(
          needsHardware = currentState.sealedSsek != null,
          onContinue = {
            uiState = if (currentState.sealedSsek != null) {
              State.UnsealingSsek(
                sealedSsek = currentState.sealedSsek,
                cachedData = currentState.cachedData
              )
            } else {
              State.ExecutingRepair(
                cachedData = currentState.cachedData
              )
            }
          },
          onBackClick = { navigator.exit() }
        ).asModalScreen()
      }

      is State.UnsealingSsek -> {
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              val unsealedKey = commands.unsealSymmetricKey(session, currentState.sealedSsek)
              Sek(unsealedKey)
            },
            onSuccess = { unsealedSsek ->
              // Store the unsealed SSEK for later use
              ssekDao.set(currentState.sealedSsek, unsealedSsek)
                .onSuccess {
                  uiState = State.ExecutingRepair(
                    cachedData = currentState.cachedData
                  )
                }
                .onFailure {
                  uiState = State.ShowingError(
                    error = KeysetRepairError.DecryptKeysetsFailed(cause = it),
                    cachedData = currentState.cachedData
                  )
                }
            },
            onCancel = {
              uiState = State.ShowingExplanation(
                sealedSsek = currentState.sealedSsek,
                cachedData = currentState.cachedData
              )
            },
            hardwareVerification = NotRequired,
            screenPresentationStyle = ScreenPresentationStyle.Modal,
            eventTrackerContext = NfcEventTrackerScreenIdContext.UNSEAL_SSEK
          )
        )
      }

      is State.ExecutingRepair -> {
        LaunchedEffect(currentState) {
          spendingKeysetRepairService.attemptRepair(
            account = screen.account,
            cachedData = currentState.cachedData
          )
            .onSuccess { repair ->
              uiState = State.CheckingForSweep(repair.updatedKeybox)
            }
            .onFailure { error ->
              uiState = when (error) {
                is KeysetRepairError.MissingPrivateKeyForActiveKeyset -> {
                  State.ShowingKeyRegenerationExplanation(
                    updatedKeybox = error.updatedKeybox,
                    cachedData = currentState.cachedData
                  )
                }
                else -> State.ShowingError(
                  error = error,
                  cachedData = currentState.cachedData
                )
              }
            }
        }

        LoadingBodyModel(
          id = KeysetRepairEventTrackerScreenId.KEYSET_REPAIR_EXECUTING,
          title = "Repairing wallet...",
          description = "This may take a moment."
        ).asModalScreen()
      }

      is State.ShowingKeyRegenerationExplanation -> {
        KeyRegenerationExplanationFormBodyModel(
          onContinue = {
            uiState = State.RefreshingAuthTokens(
              updatedKeybox = currentState.updatedKeybox,
              cachedData = currentState.cachedData
            )
          },
          onBackClick = { navigator.exit() }
        ).asModalScreen()
      }

      is State.RefreshingAuthTokens -> {
        refreshAuthTokensUiStateMachine.model(
          RefreshAuthTokensProps(
            fullAccountId = screen.account.accountId,
            appAuthKey = screen.account.keybox.activeAppKeyBundle.authKey,
            onSuccess = { tokens ->
              uiState = State.GeneratingHardwareKey(
                updatedKeybox = currentState.updatedKeybox,
                cachedData = currentState.cachedData,
                authTokens = tokens
              )
            },
            onBack = {
              uiState = State.ShowingKeyRegenerationExplanation(
                updatedKeybox = currentState.updatedKeybox,
                cachedData = currentState.cachedData
              )
            },
            screenPresentationStyle = ScreenPresentationStyle.Modal
          )
        )
      }

      is State.GeneratingHardwareKey -> {
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              val existingHwKeys = currentState.updatedKeybox.keysets.map { it.hardwareKey }
              val newHwKey = commands.getNextSpendingKey(
                session = session,
                existingDescriptorPublicKeys = existingHwKeys,
                network = currentState.updatedKeybox.config.bitcoinNetworkType
              )
              val signedAccessToken = commands.signAccessToken(
                session = session,
                accessToken = currentState.authTokens.accessToken
              )
              HardwareKeyGenerationResult(
                hwSpendingKey = newHwKey,
                hwProofOfPossession = HwFactorProofOfPossession(signedAccessToken)
              )
            },
            onSuccess = { result ->
              uiState = State.GeneratingAppKey(
                updatedKeybox = currentState.updatedKeybox,
                cachedData = currentState.cachedData,
                hwSpendingKey = result.hwSpendingKey,
                hwProofOfPossession = result.hwProofOfPossession
              )
            },
            onCancel = {
              uiState = State.ShowingKeyRegenerationExplanation(
                updatedKeybox = currentState.updatedKeybox,
                cachedData = currentState.cachedData
              )
            },
            hardwareVerification = NotRequired,
            screenPresentationStyle = ScreenPresentationStyle.Modal,
            eventTrackerContext = NfcEventTrackerScreenIdContext.KEYSET_REPAIR_GENERATE_HW_KEY
          )
        )
      }

      is State.GeneratingAppKey -> {
        LaunchedEffect(currentState) {
          spendingKeysetRepairService.regenerateActiveKeyset(
            account = screen.account,
            updatedKeybox = currentState.updatedKeybox,
            hwSpendingKey = currentState.hwSpendingKey,
            hwProofOfPossession = currentState.hwProofOfPossession,
            cachedData = currentState.cachedData
          )
            .onSuccess { repair ->
              uiState = State.CheckingForSweep(repair.updatedKeybox)
            }
            .onFailure { error ->
              uiState = State.ShowingError(
                error = error,
                cachedData = currentState.cachedData
              )
            }
        }

        LoadingBodyModel(
          id = KeysetRepairEventTrackerScreenId.KEYSET_REPAIR_GENERATING_APP_KEY,
          title = "Recovering Wallet..."
        ).asModalScreen()
      }

      is State.CheckingForSweep -> {
        LaunchedEffect(currentState.keybox) {
          // Use SweepService to check if there are funds to sweep from inactive keysets
          sweepService.prepareSweep(currentState.keybox)
            .onSuccess { sweep ->
              uiState = if (sweep != null) {
                // There are funds to sweep - show sweep UI
                State.PerformingSweep(currentState.keybox)
              } else {
                // No funds to sweep - go to success
                State.ShowingSuccess
              }
            }
            .onFailure {
              // If sweep check fails, we can still consider repair successful
              // since the keyset sync is complete. Just skip sweep; any funds on
              // older keysets will show up later.
              uiState = State.ShowingSuccess
            }
        }

        LoadingBodyModel(
          id = KeysetRepairEventTrackerScreenId.KEYSET_REPAIR_CHECKING_FOR_SWEEP,
          title = "Checking for funds..."
        ).asModalScreen()
      }

      is State.PerformingSweep -> sweepUiStateMachine.model(
        SweepUiProps(
          hasAttemptedSweep = false,
          keybox = currentState.keybox,
          sweepContext = SweepContext.InactiveWallet,
          presentationStyle = ScreenPresentationStyle.Modal,
          onExit = null,
          onSuccess = { uiState = State.ShowingSuccess },
          onAttemptSweep = {} // no-op
        )
      )

      is State.ShowingSuccess -> SuccessFormBodyModel(
        onDone = { navigator.exit() }
      ).asModalScreen()

      is State.ShowingError -> KeysetRepairErrorFormBodyModel(
        error = currentState.error,
        onRetry = { uiState = State.CheckingPrivateKeysets },
        onBackClick = { navigator.exit() }
      ).asModalScreen()
    }
  }

  private sealed interface State {
    /** Checking if there are private keysets that need SSEK unsealing. */
    data object CheckingPrivateKeysets : State

    /** Showing explanation of what keyset repair does. */
    data class ShowingExplanation(
      val sealedSsek: SealedSsek?,
      val cachedData: KeysetRepairCachedData,
    ) : State

    /** Unsealing the SSEK via NFC for private keyset decryption. */
    data class UnsealingSsek(
      val sealedSsek: SealedSsek,
      val cachedData: KeysetRepairCachedData,
    ) : State

    /** Executing the repair process. */
    data class ExecutingRepair(
      val cachedData: KeysetRepairCachedData,
    ) : State

    /** Showing explanation for key regeneration recovery. */
    data class ShowingKeyRegenerationExplanation(
      val updatedKeybox: Keybox,
      val cachedData: KeysetRepairCachedData,
    ) : State

    /** Refreshing auth tokens before NFC tap for hardware proof of possession. */
    data class RefreshingAuthTokens(
      val updatedKeybox: Keybox,
      val cachedData: KeysetRepairCachedData,
    ) : State

    /** Generating new hardware spending key via NFC. */
    data class GeneratingHardwareKey(
      val updatedKeybox: Keybox,
      val cachedData: KeysetRepairCachedData,
      val authTokens: AccountAuthTokens,
    ) : State

    /** Generating new app spending key and creating keyset on server. */
    data class GeneratingAppKey(
      val updatedKeybox: Keybox,
      val cachedData: KeysetRepairCachedData,
      val hwSpendingKey: HwSpendingPublicKey,
      val hwProofOfPossession: HwFactorProofOfPossession,
    ) : State

    /** Checking if there are funds to sweep from old keysets. */
    data class CheckingForSweep(val keybox: Keybox) : State

    /** Performing sweep of funds from old keysets. */
    data class PerformingSweep(val keybox: Keybox) : State

    /** Repair completed successfully. */
    data object ShowingSuccess : State

    /** Error occurred during repair. */
    data class ShowingError(
      val error: KeysetRepairError,
      val cachedData: KeysetRepairCachedData?,
    ) : State
  }
}

data class ExplanationFormBodyModel(
  val needsHardware: Boolean,
  val onContinue: () -> Unit,
  val onBackClick: () -> Unit,
) : FormBodyModel(
    id = KeysetRepairEventTrackerScreenId.KEYSET_REPAIR_EXPLANATION,
    onBack = onBackClick,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onClick = onBackClick)),
    header = FormHeaderModel(
      icon = Icon.LargeIconWarningFilled,
      headline = "Wallet repair needed",
      subline = buildString {
        append("Your wallet data is out of sync with our servers. We will re-sync your wallet data and update your cloud backup.")
        if (needsHardware) {
          append("\n\nYou will need your Bitkey device to continue.")
        }
      }
    ),
    primaryButton = ButtonModel(
      text = "Continue",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onContinue)
    ),
    secondaryButton = ButtonModel(
      text = "Cancel",
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onBackClick)
    )
  )

/**
 * Result from NFC session for generating new hardware keys.
 */
private data class HardwareKeyGenerationResult(
  val hwSpendingKey: HwSpendingPublicKey,
  val hwProofOfPossession: HwFactorProofOfPossession,
)

/**
 * Explanation screen shown when we need to regenerate keys because the
 * private key for the active keyset is missing.
 */
internal data class KeyRegenerationExplanationFormBodyModel(
  val onContinue: () -> Unit,
  val onBackClick: () -> Unit,
) : FormBodyModel(
    id = KeysetRepairEventTrackerScreenId.KEYSET_REPAIR_KEY_REGENERATION_EXPLANATION,
    onBack = onBackClick,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onClick = onBackClick)),
    header = FormHeaderModel(
      icon = Icon.LargeIconWarningFilled,
      headline = "Recovery Required",
      subline = "Your wallet needs to be recovered. Funds will be transferred to your new wallet."
    ),
    primaryButton = ButtonModel(
      text = "Continue",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.BitkeyInteraction,
      onClick = StandardClick(onContinue)
    ),
    secondaryButton = ButtonModel(
      text = "Cancel",
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onBackClick)
    )
  )

private data class SuccessFormBodyModel(
  val onDone: () -> Unit,
) : FormBodyModel(
    id = KeysetRepairEventTrackerScreenId.KEYSET_REPAIR_SUCCESS,
    onBack = null,
    toolbar = null,
    header = FormHeaderModel(
      icon = Icon.LargeIconCheckFilled,
      headline = "Wallet repaired",
      subline = "Your wallet data has been synced and your backup has been updated."
    ),
    primaryButton = ButtonModel(
      text = "Done",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onDone)
    )
  )

private fun KeysetRepairErrorFormBodyModel(
  error: KeysetRepairError,
  onRetry: () -> Unit,
  onBackClick: () -> Unit,
): FormBodyModel {
  return ErrorFormBodyModel(
    title = "Repair failed",
    subline = "An error occurred. Please try again.",
    primaryButton = ButtonDataModel(
      text = "Retry",
      onClick = onRetry
    ),
    secondaryButton = ButtonDataModel(
      text = "Cancel",
      onClick = onBackClick
    ),
    eventTrackerScreenId = KeysetRepairEventTrackerScreenId.KEYSET_REPAIR_FAILED,
    errorData = ErrorData(
      segment = RecoverySegment.KeysetRepair.Repair,
      cause = error.cause,
      actionDescription = "Keyset repair"
    ),
    onBack = onBackClick
  )
}
