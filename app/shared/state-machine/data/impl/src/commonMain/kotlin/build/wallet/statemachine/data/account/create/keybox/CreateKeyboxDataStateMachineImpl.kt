package build.wallet.statemachine.data.account.create.keybox

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.*
import build.wallet.auth.AccountCreationError
import build.wallet.auth.FullAccountCreator
import build.wallet.auth.LiteToFullAccountUpgrader
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.keybox.KeyCrossDraft.WithAppKeys
import build.wallet.bitkey.keybox.KeyCrossDraft.WithAppKeysAndHardwareKeys
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.debug.DebugOptionsService
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.CreateAccountClientErrorCode
import build.wallet.keybox.keys.AppKeysGenerator
import build.wallet.keybox.keys.OnboardingAppKeyKeystore
import build.wallet.logging.log
import build.wallet.onboarding.OnboardingKeyboxHardwareKeys
import build.wallet.onboarding.OnboardingKeyboxHardwareKeysDao
import build.wallet.onboarding.OnboardingKeyboxSealedCsekDao
import build.wallet.platform.random.UuidGenerator
import build.wallet.statemachine.data.account.CreateFullAccountData
import build.wallet.statemachine.data.account.CreateFullAccountData.CreateKeyboxData.*
import build.wallet.statemachine.data.account.create.CreateFullAccountContext.LiteToFullAccountUpgrade
import build.wallet.statemachine.data.account.create.CreateFullAccountContext.NewFullAccount
import build.wallet.statemachine.data.account.create.keybox.State.*
import com.github.michaelbull.result.*
import kotlinx.coroutines.flow.first

class CreateKeyboxDataStateMachineImpl(
  private val fullAccountCreator: FullAccountCreator,
  private val appKeysGenerator: AppKeysGenerator,
  private val onboardingKeyboxSealedCsekDao: OnboardingKeyboxSealedCsekDao,
  private val onboardingKeyboxHardwareKeysDao: OnboardingKeyboxHardwareKeysDao,
  private val uuidGenerator: UuidGenerator,
  private val onboardingAppKeyKeystore: OnboardingAppKeyKeystore,
  private val liteToFullAccountUpgrader: LiteToFullAccountUpgrader,
  private val debugOptionsService: DebugOptionsService,
) : CreateKeyboxDataStateMachine {
  @Composable
  override fun model(props: CreateKeyboxDataProps): CreateFullAccountData.CreateKeyboxData {
    var state: State by remember { mutableStateOf(CreatingAppKeysState) }

    return state.let { s ->
      when (s) {
        is CreatingAppKeysState -> {
          LaunchedEffect("generate-app-keys") {
            // Reuse keybox configuration used for ongoing onboarding,
            // otherwise fall back on debug options
            val accountConfig = props.onboardingKeybox?.config
              ?: debugOptionsService.options().first().toFullAccountConfig()

            generateAppKeys(accountConfig.bitcoinNetworkType)
              .onSuccess { appKeyBundle ->
                // once we successfully generate app keys, persist them in the case onboarding is
                // interrupted so we can resume account creation with the same keys
                onboardingAppKeyKeystore.persistAppKeys(
                  spendingKey = appKeyBundle.spendingKey,
                  globalAuthKey = appKeyBundle.authKey,
                  recoveryAuthKey = requireNotNull(appKeyBundle.recoveryAuthKey) {
                    "AppKeyBundle is missing PublicKey<AppRecoveryAuthKey>."
                  },
                  bitcoinNetworkType = accountConfig.bitcoinNetworkType
                )
                state = HasAppKeysState(
                  keyCrossDraft = WithAppKeys(
                    appKeyBundle = appKeyBundle,
                    config = accountConfig
                  )
                )
              }
              .onFailure {
                state = CreateAppKeysErrorState
              }
          }
          CreatingAppKeysData(rollback = props.rollback)
        }

        is CreateAppKeysErrorState ->
          CreateKeyboxErrorData(
            onBack = props.rollback,
            subline = "We are looking into this. Please try again later.",
            primaryButton = CreateKeyboxErrorData.Button("Done", props.rollback),
            eventTrackerScreenId = APP_KEYS_CREATION_FAILURE
          )

        is HasAppKeysState ->
          HasAppKeysData(
            appKeys = s.keyCrossDraft,
            rollback = props.rollback,
            fullAccountConfig = s.keyCrossDraft.config,
            onPairHardwareComplete = { newHardwareActivation ->
              state = HasAppAndHardwareKeysState(
                keyCrossDraft = WithAppKeysAndHardwareKeys(
                  appKeyBundle = s.keyCrossDraft.appKeyBundle,
                  hardwareKeyBundle = newHardwareActivation.keyBundle,
                  config = s.keyCrossDraft.config,
                  appGlobalAuthKeyHwSignature = newHardwareActivation.appGlobalAuthKeyHwSignature
                ),
                sealedCsek = newHardwareActivation.sealedCsek
              )
            }
          )

        is HasAppAndHardwareKeysState -> {
          LaunchedEffect("store-sealed-csek") {
            // Set the sealed CSEK so we have it available once the keybox is created
            // and the data state machine transitions
            onboardingKeyboxSealedCsekDao
              .set(s.sealedCsek)
              .andThen {
                // Save the hw auth public key in case we find a lite account backup and need to
                // go through lite => full account upgrade instead. Saving this pub key will allow
                // us to save a tap later.
                onboardingKeyboxHardwareKeysDao.set(
                  OnboardingKeyboxHardwareKeys(
                    hwAuthPublicKey = s.keyCrossDraft.hardwareKeyBundle.authKey,
                    appGlobalAuthKeyHwSignature = s.keyCrossDraft.appGlobalAuthKeyHwSignature
                  )
                )
              }
              .onSuccess {
                state =
                  PairingWithServerState(
                    keyCrossDraft = s.keyCrossDraft
                  )
              }
              .onFailure {
                state =
                  ErrorStoringSealedCsek(
                    keyCrossDraft =
                      WithAppKeys(
                        appKeyBundle = s.keyCrossDraft.appKeyBundle,
                        config = s.keyCrossDraft.config
                      )
                  )
              }
          }
          HasAppAndHardwareKeysData(
            rollback = {
              state =
                HasAppKeysState(
                  keyCrossDraft =
                    WithAppKeys(
                      appKeyBundle = s.keyCrossDraft.appKeyBundle,
                      config = s.keyCrossDraft.config
                    )
                )
            }
          )
        }

        is PairingWithServerState -> {
          LaunchedEffect("create-account") {
            createF8eAccount(props, s)
              .onFailure { error ->
                when (error.createAccountClientErrorCode()) {
                  null ->
                    state =
                      PairWithServerErrorState(
                        keyCrossDraft = s.keyCrossDraft,
                        error = error
                      )

                  CreateAccountClientErrorCode.HW_AUTH_PUBKEY_IN_USE ->
                    state =
                      PairWithServerErrorHardwareKeyAlreadyInUseState(
                        keyCrossDraft = s.keyCrossDraft
                      )

                  CreateAccountClientErrorCode.APP_AUTH_PUBKEY_IN_USE -> {
                    // When the error is that the app key is already tied to an account, clear
                    // the key and generate new keys before showing the customer error messaging
                    // asking them to retry
                    onboardingAppKeyKeystore.clear()
                    generateAppKeys(s.keyCrossDraft.config.bitcoinNetworkType)
                      .onSuccess { newAppKeyBundle ->
                        state =
                          PairWithServerErrorAppKeyAlreadyInUseState(
                            keyCrossDraft = s.keyCrossDraft.copy(appKeyBundle = newAppKeyBundle)
                          )
                      }
                      .onFailure {
                        state = CreateAppKeysErrorState
                      }
                  }
                }
              }
          }
          PairingWithServerData
        }

        is PairWithServerErrorHardwareKeyAlreadyInUseState ->
          CreateKeyboxErrorData(
            onBack = props.rollback,
            subline = "The Bitkey device is already being used by an existing wallet. If it belongs to you, you can use the device to restore your wallet.",
            primaryButton = CreateKeyboxErrorData.Button("Got it", props.rollback),
            eventTrackerScreenId = NEW_ACCOUNT_CREATION_FAILURE_HW_KEY_ALREADY_IN_USE
          )

        is PairWithServerErrorAppKeyAlreadyInUseState ->
          CreateKeyboxErrorData(
            onBack = props.rollback,
            subline = "Please try again.",
            primaryButton =
              CreateKeyboxErrorData.Button("Retry") {
                state = PairingWithServerState(keyCrossDraft = s.keyCrossDraft)
              },
            secondaryButton = CreateKeyboxErrorData.Button("Back", props.rollback),
            eventTrackerScreenId = NEW_ACCOUNT_CREATION_FAILURE_APP_KEY_ALREADY_IN_USE
          )

        is PairWithServerErrorState ->
          CreateKeyboxErrorData(
            onBack = props.rollback,
            subline =
              when {
                s.error.isConnectivityError() -> "Make sure you are connected to the internet and try again."
                else -> "We are looking into this. Please try again later."
              },
            primaryButton =
              CreateKeyboxErrorData.Button("Retry") {
                state = PairingWithServerState(keyCrossDraft = s.keyCrossDraft)
              },
            secondaryButton = CreateKeyboxErrorData.Button("Back", props.rollback),
            eventTrackerScreenId = NEW_ACCOUNT_CREATION_FAILURE
          )

        is ErrorStoringSealedCsek -> {
          CreateKeyboxErrorData(
            onBack = props.rollback,
            subline = "There was an issue with setting up your Bitkey device. Please retry to set up your device.",
            primaryButton =
              CreateKeyboxErrorData.Button("Retry") {
                state =
                  HasAppKeysState(
                    keyCrossDraft = s.keyCrossDraft
                  )
              },
            eventTrackerScreenId = NEW_ACCOUNT_CREATION_HW_FAILURE
          )
        }
      }
    }
  }

  private suspend fun generateAppKeys(
    networkType: BitcoinNetworkType,
  ): Result<AppKeyBundle, Throwable> {
    // if we have previously persisted app keys we restore those and return them (to keep account
    // creation idempotent). Otherwise we create a new app key bundle for account creation
    return when (
      val appKeys =
        onboardingAppKeyKeystore.getAppKeyBundle(
          localId = uuidGenerator.random(),
          network = networkType
        )
    ) {
      null -> {
        log { "Generating new app key bundle" }
        appKeysGenerator.generateKeyBundle(networkType)
      }
      else -> {
        log { "Using existing app key bundle " }
        Ok(appKeys)
      }
    }
  }

  private suspend fun createF8eAccount(
    props: CreateKeyboxDataProps,
    state: PairingWithServerState,
  ): Result<FullAccount, AccountCreationError> {
    return when (props.context) {
      is NewFullAccount ->
        fullAccountCreator.createAccount(
          keyCrossDraft = WithAppKeysAndHardwareKeys(
            appKeyBundle = state.keyCrossDraft.appKeyBundle,
            hardwareKeyBundle = state.keyCrossDraft.hardwareKeyBundle,
            config = state.keyCrossDraft.config,
            appGlobalAuthKeyHwSignature = state.keyCrossDraft.appGlobalAuthKeyHwSignature
          )
        )

      is LiteToFullAccountUpgrade ->
        liteToFullAccountUpgrader.upgradeAccount(
          liteAccount = props.context.liteAccount,
          keyCrossDraft = WithAppKeysAndHardwareKeys(
            appKeyBundle = state.keyCrossDraft.appKeyBundle,
            hardwareKeyBundle = state.keyCrossDraft.hardwareKeyBundle,
            config = state.keyCrossDraft.config,
            appGlobalAuthKeyHwSignature = state.keyCrossDraft.appGlobalAuthKeyHwSignature
          )
        )
    }
  }
}

private sealed interface State {
  data object CreatingAppKeysState : State

  data object CreateAppKeysErrorState : State

  /**
   * App keys generated. We can now pair with hardware, UI state machine is taking care of this.
   */
  data class HasAppKeysState(
    val keyCrossDraft: WithAppKeys,
  ) : State

  /**
   * Received an error when storing the Sealed Csek into dao
   *
   * @property keyCrossDraft - The keycross draft with app keys necessary for re-attempting csek
   * storage
   */
  data class ErrorStoringSealedCsek(
    val keyCrossDraft: WithAppKeys,
  ) : State

  /**
   * App and hardware keys ready. At this point we are reading to start within with server.
   */
  data class HasAppAndHardwareKeysState(
    val keyCrossDraft: WithAppKeysAndHardwareKeys,
    val sealedCsek: SealedCsek,
  ) : State

  /**
   * Pairing with server, creating Account.
   */
  data class PairingWithServerState(
    val keyCrossDraft: WithAppKeysAndHardwareKeys,
  ) : State

  /**
   * There was an error when pairing with the server due to the HW key already being paired.
   */
  data class PairWithServerErrorHardwareKeyAlreadyInUseState(
    val keyCrossDraft: WithAppKeysAndHardwareKeys,
  ) : State

  /**
   * There was an error when pairing with the server due to the app key already being paired.
   */
  data class PairWithServerErrorAppKeyAlreadyInUseState(
    val keyCrossDraft: WithAppKeysAndHardwareKeys,
  ) : State

  /**
   * There was a generic error when pairing with the server.
   */
  data class PairWithServerErrorState(
    val keyCrossDraft: WithAppKeysAndHardwareKeys,
    val error: AccountCreationError,
  ) : State
}

private fun AccountCreationError.isConnectivityError(): Boolean {
  val f8eError = this as? AccountCreationError.AccountCreationF8eError
  return f8eError?.f8eError is F8eError.ConnectivityError
}

private fun AccountCreationError.createAccountClientErrorCode(): CreateAccountClientErrorCode? {
  val f8eError = this as? AccountCreationError.AccountCreationF8eError
  val clientError = f8eError?.f8eError as? F8eError.SpecificClientError
  return clientError?.errorCode
}
