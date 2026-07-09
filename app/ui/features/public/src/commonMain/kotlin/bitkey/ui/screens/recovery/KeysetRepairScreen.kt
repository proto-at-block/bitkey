package bitkey.ui.screens.recovery

import androidx.compose.runtime.*
import bitkey.account.HardwareType
import bitkey.auth.AccountAuthTokens
import bitkey.serialization.hex.decodeHexWithResult
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.KeysetRepairEventTrackerScreenId
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.keys.extractAccountIndex
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwSpendingKeyProof
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.Keybox
import build.wallet.chaincode.delegation.ChaincodeExtractor
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.cloud.backup.csek.Sek
import build.wallet.cloud.backup.csek.SsekDao
import build.wallet.crypto.WsmVerifier
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.f8e.recovery.SignedKeysetVerificationResponse
import build.wallet.keybox.KeyboxDao
import build.wallet.logging.logFailure
import build.wallet.nfc.platform.KeysetRepairRotateHwKeyParams
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.requireW3
import build.wallet.nfc.NfcSession
import build.wallet.recovery.keyset.PreparedRegeneratedKeyset
import build.wallet.recovery.keyset.KeysetRepairCachedData
import build.wallet.recovery.keyset.KeysetRepairError
import build.wallet.recovery.keyset.PrivateKeysetInfo
import build.wallet.recovery.keyset.SpendingKeysetRepairService
import build.wallet.recovery.sweep.SweepContext
import build.wallet.recovery.sweep.SweepService
import build.wallet.statemachine.auth.RefreshAuthTokensProps
import build.wallet.statemachine.auth.RefreshAuthTokensUiStateMachine
import build.wallet.statemachine.auth.ActionProofType
import build.wallet.statemachine.auth.HardwareAuthUiProps
import build.wallet.statemachine.auth.HardwareAuthUiStateMachine
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.nfc.NfcConfirmableSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcConfirmableSessionUiStateMachine
import build.wallet.statemachine.nfc.NfcSessionConfig
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.Required
import build.wallet.statemachine.nfc.verifyPublicKeysOrLog
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationContent
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.recovery.sweep.SweepUiProps
import build.wallet.statemachine.recovery.sweep.SweepUiStateMachine
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.mapError
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
  private val nfcConfirmableSessionUiStateMachine: NfcConfirmableSessionUiStateMachine,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val hardwareAuthUiStateMachine: HardwareAuthUiStateMachine,
  private val sweepUiStateMachine: SweepUiStateMachine,
  private val sweepService: SweepService,
  private val ssekDao: SsekDao,
  private val refreshAuthTokensUiStateMachine: RefreshAuthTokensUiStateMachine,
  private val keyboxDao: KeyboxDao,
  private val chaincodeExtractor: ChaincodeExtractor,
  private val wsmVerifier: WsmVerifier,
) : ScreenPresenter<KeysetRepairScreen> {
  @Suppress("CyclomaticComplexMethod")
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
          useBitkeyInteraction = currentState.useBitkeyInteraction(),
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
        nfcConfirmableSessionUiStateMachine.model(
          NfcConfirmableSessionUIStateMachineProps(
            session = { session, commands ->
              commands.keysetRepairUnsealSymmetricKey(
                session = session,
                sealedKey = currentState.sealedSsek
              )
            },
            onSuccess = { unsealedKey ->
              ssekDao.set(currentState.sealedSsek, Sek(unsealedKey))
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
            config = NfcSessionConfig(
              onCancel = {
                uiState = State.ShowingExplanation(
                  sealedSsek = currentState.sealedSsek,
                  cachedData = currentState.cachedData
                )
              },
              hardwareVerification = NotRequired,
              shouldLock = screen.account.config.hardwareType != HardwareType.W3,
              screenPresentationStyle = ScreenPresentationStyle.Modal,
              eventTrackerContext = NfcEventTrackerScreenIdContext.UNSEAL_SSEK
            ),
            confirmationContent = HardwareConfirmationContent.KeysetRepairUnseal
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
        nfcConfirmableSessionUiStateMachine.model(
          NfcConfirmableSessionUIStateMachineProps(
            session = { session, commands ->
              commands.keysetRepairRotateHwKey(
                session = session,
                params = KeysetRepairRotateHwKeyParams(
                  accessToken = currentState.authTokens.accessToken,
                  existingHwSpendingKeys = currentState.updatedKeybox.keysets.map { it.hardwareKey },
                  network = currentState.updatedKeybox.config.bitcoinNetworkType
                )
              )
            },
            onSuccess = { result ->
              uiState = State.GeneratingAppKey(
                updatedKeybox = currentState.updatedKeybox,
                cachedData = currentState.cachedData,
                hwSpendingKey = result.hwSpendingKey,
                hwProofOfPossession = HwFactorProofOfPossession(result.signedAccessToken),
                hwSpendingKeyProof = result.hwSpendingKeyProof
              )
            },
            config = NfcSessionConfig(
              onCancel = {
                uiState = State.ShowingKeyRegenerationExplanation(
                  updatedKeybox = currentState.updatedKeybox,
                  cachedData = currentState.cachedData
                )
              },
              hardwareVerification = NotRequired,
              shouldLock = screen.account.config.hardwareType != HardwareType.W3,
              screenPresentationStyle = ScreenPresentationStyle.Modal,
              eventTrackerContext = NfcEventTrackerScreenIdContext.KEYSET_REPAIR_GENERATE_HW_KEY
            ),
            confirmationContent = HardwareConfirmationContent.KeysetRepairRotateHwKey
          )
        )
      }

      is State.GeneratingAppKey -> {
        LaunchedEffect(currentState) {
          when (screen.account.config.hardwareType) {
            HardwareType.W1 -> {
              spendingKeysetRepairService.regenerateActiveKeyset(
                account = screen.account,
                updatedKeybox = currentState.updatedKeybox,
                hwSpendingKey = currentState.hwSpendingKey,
                hwProofOfPossession = currentState.hwProofOfPossession,
                cachedData = currentState.cachedData,
                hwSpendingKeyProof = currentState.hwSpendingKeyProof
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
            HardwareType.W3 -> {
              spendingKeysetRepairService.prepareRegeneratedActiveKeyset(
                account = screen.account,
                updatedKeybox = currentState.updatedKeybox,
                hwSpendingKey = currentState.hwSpendingKey,
                hwSpendingKeyProof = currentState.hwSpendingKeyProof
              )
                .onSuccess { prepared ->
                  uiState = if (currentState.cachedData.response.wrappedSsek != null) {
                    State.AuthorizingDescriptorBackup(
                      preparedRegeneratedKeyset = prepared,
                      cachedData = currentState.cachedData
                    )
                  } else {
                    State.AuthorizingKeysetActivation(
                      preparedRegeneratedKeyset = prepared,
                      descriptorBackupProof = null,
                      cachedData = currentState.cachedData
                    )
                  }
                }
                .onFailure { error ->
                  uiState = State.ShowingError(
                    error = error,
                    cachedData = currentState.cachedData
                  )
                }
            }
          }
        }

        LoadingBodyModel(
          id = KeysetRepairEventTrackerScreenId.KEYSET_REPAIR_GENERATING_APP_KEY,
          title = "Recovering Wallet..."
        ).asModalScreen()
      }

      is State.AuthorizingDescriptorBackup -> {
        hardwareAuthUiStateMachine.model(
          keysetRepairHardwareAuthProps(
            account = screen.account,
            actionProofType = ActionProofType.UpdateDescriptorBackups,
            actionDescription = "Authorize descriptor backup update",
            shouldLock = false,
            onSuccess = { proof ->
              uiState = State.AuthorizingKeysetActivation(
                preparedRegeneratedKeyset = currentState.preparedRegeneratedKeyset,
                descriptorBackupProof = proof,
                cachedData = currentState.cachedData
              )
            },
            onBack = {
              uiState = State.ShowingKeyRegenerationExplanation(
                updatedKeybox = currentState.preparedRegeneratedKeyset.keybox,
                cachedData = currentState.cachedData
              )
            }
          )
        )
      }

      is State.AuthorizingKeysetActivation -> {
        hardwareAuthUiStateMachine.model(
          keysetRepairHardwareAuthProps(
            account = screen.account,
            actionProofType = ActionProofType.RotateSpendingKeyset(
              keysetId = currentState.preparedRegeneratedKeyset.newKeyset.f8eSpendingKeyset.keysetId
            ),
            actionDescription = "Authorize keyset activation",
            refreshAuthTokens = currentState.descriptorBackupProof == null,
            shouldLock = false,
            onSuccess = { proof ->
              uiState = State.CompletingRegeneratedKeyset(
                preparedRegeneratedKeyset = currentState.preparedRegeneratedKeyset,
                descriptorBackupProof = currentState.descriptorBackupProof,
                keysetActivationProof = proof,
                cachedData = currentState.cachedData
              )
            },
            onBack = {
              uiState = State.ShowingKeyRegenerationExplanation(
                updatedKeybox = currentState.preparedRegeneratedKeyset.keybox,
                cachedData = currentState.cachedData
              )
            }
          )
        )
      }

      is State.CompletingRegeneratedKeyset -> {
        LaunchedEffect(currentState) {
          spendingKeysetRepairService.completeRegeneratedActiveKeyset(
            account = screen.account,
            preparedRegeneratedKeyset = currentState.preparedRegeneratedKeyset,
            descriptorBackupProof = currentState.descriptorBackupProof,
            keysetActivationProof = currentState.keysetActivationProof,
            cachedData = currentState.cachedData
          )
            .onSuccess { repair ->
              uiState = repair.signedKeysetVerification?.let { signedKeysetVerification ->
                State.ProvisioningHardwareDescriptor(
                  keybox = repair.updatedKeybox,
                  signedKeysetVerification = signedKeysetVerification
                )
              } ?: State.CheckingForSweep(repair.updatedKeybox)
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

      is State.ProvisioningHardwareDescriptor -> {
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              prepareHardwareDescriptorProvisioning(
                session = session,
                commands = commands,
                account = screen.account,
                keybox = currentState.keybox,
                signedKeysetVerification = currentState.signedKeysetVerification
              )
            },
            onSuccess = { result ->
              val signature = result.get()
              if (signature == null) {
                uiState = State.ShowingHardwareDescriptorProvisioningError(
                  keybox = currentState.keybox,
                  signedKeysetVerification = currentState.signedKeysetVerification,
                  cause = result.getError()
                    ?: Error("Hardware descriptor verification failed")
                )
                return@NfcSessionUIStateMachineProps
              }

              keyboxDao.updateAppGlobalAuthKeyHwSignature(
                keybox = currentState.keybox,
                signature = signature
              )
                .onSuccess { updatedKeybox ->
                  uiState = State.CheckingForSweep(updatedKeybox)
                }
                .onFailure { error ->
                  uiState = State.ShowingHardwareDescriptorProvisioningError(
                    keybox = currentState.keybox,
                    signedKeysetVerification = currentState.signedKeysetVerification,
                    cause = error
                  )
                }
            },
            onCancel = {
              uiState = State.ShowingHardwareDescriptorProvisioningError(
                keybox = currentState.keybox,
                signedKeysetVerification = currentState.signedKeysetVerification,
                cause = Error("Hardware descriptor validation cancelled")
              )
            },
            screenPresentationStyle = ScreenPresentationStyle.Modal,
            eventTrackerContext = NfcEventTrackerScreenIdContext.VERIFY_KEYS_AND_BUILD_HARDWARE_DESCRIPTOR,
            hardwareVerification = Required(),
            hardwareTypeOverride = HardwareType.W3,
            showDeviceConfirmation = true
          )
        )
      }

      is State.ShowingHardwareDescriptorProvisioningError -> ErrorFormBodyModel(
        title = "Repair failed",
        subline = "An error occurred. Please try again.",
        primaryButton = ButtonDataModel(
          text = "Retry",
          onClick = {
            uiState = State.ProvisioningHardwareDescriptor(
              keybox = currentState.keybox,
              signedKeysetVerification = currentState.signedKeysetVerification
            )
          }
        ),
        secondaryButton = ButtonDataModel(
          text = "Cancel",
          onClick = { navigator.exit() }
        ),
        eventTrackerScreenId = KeysetRepairEventTrackerScreenId.KEYSET_REPAIR_FAILED,
        errorData = ErrorData(
          segment = RecoverySegment.KeysetRepair.Repair,
          cause = currentState.cause,
          actionDescription = "Provisioning W3 hardware descriptor"
        ),
        onBack = { navigator.exit() }
      ).asModalScreen()

      is State.CheckingForSweep -> {
        LaunchedEffect(currentState.keybox) {
          // Use SweepService to check if there are funds to sweep from inactive keysets
          sweepService.prepareSweep(currentState.keybox, SweepContext.InactiveWallet)
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
          account = screen.account.copy(keybox = currentState.keybox),
          hasAttemptedSweep = false,
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

  private fun keysetRepairHardwareAuthProps(
    account: FullAccount,
    actionProofType: ActionProofType,
    actionDescription: String,
    refreshAuthTokens: Boolean = true,
    shouldLock: Boolean = true,
    onSuccess: (PrivilegedActionProof) -> Unit,
    onBack: () -> Unit,
  ) = HardwareAuthUiProps(
    account = account,
    actionProofType = actionProofType,
    segment = RecoverySegment.KeysetRepair.Repair,
    actionDescription = actionDescription,
    screenPresentationStyle = ScreenPresentationStyle.Modal,
    onSuccess = onSuccess,
    onBack = onBack,
    refreshAuthTokens = refreshAuthTokens,
    shouldLock = shouldLock
  )

  private suspend fun prepareHardwareDescriptorProvisioning(
    session: NfcSession,
    commands: NfcCommands,
    account: FullAccount,
    keybox: Keybox,
    signedKeysetVerification: SignedKeysetVerificationResponse,
  ): Result<AppGlobalAuthKeyHwSignature, Throwable> =
    coroutineBinding {
      val newKeyset = keybox.activeSpendingKeyset

      wsmVerifier.verifyPublicKeysOrLog(
        appAuthPubHex = signedKeysetVerification.appAuthPub,
        hardwareAuthPubHex = signedKeysetVerification.hardwareAuthPub,
        appSpendingPubHex = signedKeysetVerification.appSpendingPub,
        hardwareSpendingPubHex = signedKeysetVerification.hardwareSpendingPub,
        serverSpendingPubHex = signedKeysetVerification.serverSpendingPub,
        signature = signedKeysetVerification.signature,
        f8eEnvironment = account.config.f8eEnvironment,
        context = "W3 keyset repair build hardware descriptor"
      )

      val appSpendingKey = signedKeysetVerification.appSpendingPub
        .decodeHexWithResult()
        .mapError { Error("Invalid app spending key hex from server", it) }
        .bind()
      val appAuthKey = signedKeysetVerification.appAuthPub
        .decodeHexWithResult()
        .mapError { Error("Invalid app auth key hex from server", it) }
        .bind()
      val serverSpendingKey = signedKeysetVerification.serverSpendingPub
        .decodeHexWithResult()
        .mapError { Error("Invalid server spending key hex from server", it) }
        .bind()
      val wsmSignature = signedKeysetVerification.signature
        .decodeHexWithResult()
        .mapError { Error("Invalid WSM signature hex from server", it) }
        .bind()

      val appSpendingKeyChaincode = chaincodeExtractor
        .extractChaincode(newKeyset.appKey.key.xpub)
        .result
        .mapError { Error("Failed to extract app spending key chaincode", it) }
        .bind()

      val serverSpendingXpub = newKeyset.f8eSpendingKeyset.privateWalletRootXpub
        ?: Err(IllegalStateException("Server spending xpub is required for W3 keyset repair"))
          .bind()

      val serverSpendingKeyChaincode = chaincodeExtractor
        .extractChaincode(serverSpendingXpub)
        .result
        .mapError { Error("Failed to extract server spending key chaincode", it) }
        .bind()

      val networkMainnet = keybox.config.bitcoinNetworkType == BitcoinNetworkType.BITCOIN
      val accountIndex = newKeyset.hardwareKey.key.extractAccountIndex()

      val signature = commands.requireW3(session).verifyKeysAndBuildDescriptor(
        session = session,
        appSpendingKey = appSpendingKey,
        appSpendingKeyChaincode = appSpendingKeyChaincode,
        networkMainnet = networkMainnet,
        appAuthKey = appAuthKey,
        serverSpendingKey = serverSpendingKey,
        serverSpendingKeyChaincode = serverSpendingKeyChaincode,
        wsmSignature = wsmSignature,
        accountIndex = accountIndex
      )

      AppGlobalAuthKeyHwSignature(signature)
    }.logFailure { "Failed to prepare W3 keyset repair hardware descriptor" }

  private sealed interface State {
    /** Checking if there are private keysets that need SSEK unsealing. */
    data object CheckingPrivateKeysets : State

    /** Showing explanation of what keyset repair does. */
    data class ShowingExplanation(
      val sealedSsek: SealedSsek?,
      val cachedData: KeysetRepairCachedData,
    ) : State {
      fun useBitkeyInteraction(): Boolean = sealedSsek != null
    }

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
      val hwSpendingKeyProof: HwSpendingKeyProof? = null,
    ) : State

    /** Collecting W3 action proof for descriptor backup upload. */
    data class AuthorizingDescriptorBackup(
      val preparedRegeneratedKeyset: PreparedRegeneratedKeyset,
      val cachedData: KeysetRepairCachedData,
    ) : State

    /** Collecting W3 action proof for activating the regenerated keyset. */
    data class AuthorizingKeysetActivation(
      val preparedRegeneratedKeyset: PreparedRegeneratedKeyset,
      val descriptorBackupProof: PrivilegedActionProof?,
      val cachedData: KeysetRepairCachedData,
    ) : State

    /** Uploading backups and activating the regenerated keyset after authorization. */
    data class CompletingRegeneratedKeyset(
      val preparedRegeneratedKeyset: PreparedRegeneratedKeyset,
      val descriptorBackupProof: PrivilegedActionProof?,
      val keysetActivationProof: PrivilegedActionProof,
      val cachedData: KeysetRepairCachedData,
    ) : State

    /** Provisioning the regenerated W3 hardware descriptor after server activation. */
    data class ProvisioningHardwareDescriptor(
      val keybox: Keybox,
      val signedKeysetVerification: SignedKeysetVerificationResponse,
    ) : State

    /** Failed to provision the regenerated W3 hardware descriptor. */
    data class ShowingHardwareDescriptorProvisioningError(
      val keybox: Keybox,
      val signedKeysetVerification: SignedKeysetVerificationResponse,
      val cause: Throwable,
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
  val useBitkeyInteraction: Boolean,
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
      requiresBitkeyInteraction = useBitkeyInteraction,
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = onContinue
    ),
    secondaryButton = ButtonModel(
      text = "Cancel",
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onBackClick)
    )
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
