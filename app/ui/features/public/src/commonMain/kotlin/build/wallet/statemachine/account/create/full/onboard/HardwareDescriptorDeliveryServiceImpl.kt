package build.wallet.statemachine.account.create.full.onboard

import bitkey.serialization.hex.decodeHexWithResult
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.keys.extractAccountIndex
import build.wallet.bitkey.account.FullAccount
import build.wallet.chaincode.delegation.ChaincodeExtractor
import build.wallet.crypto.WsmVerifier
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ensureNotNull
import build.wallet.f8e.onboarding.OnboardingF8eClient
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.requireW3
import build.wallet.onboarding.HardwareDescriptorDeliveryService
import build.wallet.statemachine.nfc.verifyPublicKeysOrLog
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError

@BitkeyInject(AppScope::class)
class HardwareDescriptorDeliveryServiceImpl(
  private val onboardingF8eClient: OnboardingF8eClient,
  private val chaincodeExtractor: ChaincodeExtractor,
  private val wsmVerifier: WsmVerifier,
) : HardwareDescriptorDeliveryService {
  override suspend fun fetchSignatureAndPrepareNfcSession(
    account: FullAccount,
  ): Result<suspend (NfcSession, NfcCommands) -> String, Error> =
    coroutineBinding {
      val activeSpendingKeyset = account.keybox.activeSpendingKeyset

      // Step 1: Call completeOnboardingV2 to get the WSM signature
      val response = onboardingF8eClient
        .completeOnboardingV2(
          f8eEnvironment = account.config.f8eEnvironment,
          fullAccountId = account.accountId,
        )
        .mapError { Error("Failed to get WSM signature from server", it) }
        .bind()

      // Verify the WSM signature over the 5 public keys before presenting them to hardware.
      wsmVerifier.verifyPublicKeysOrLog(
        appAuthPubHex = response.appAuthPub,
        hardwareAuthPubHex = response.hardwareAuthPub,
        appSpendingPubHex = response.appSpendingPub,
        hardwareSpendingPubHex = response.hardwareSpendingPub,
        serverSpendingPubHex = response.serverSpendingPub,
        signature = response.signature,
        f8eEnvironment = account.config.f8eEnvironment,
        context = "onboarding build hardware descriptor"
      )

      // Step 2: Extract keys from the requested spending keyset
      val appSpendingKey = response.appSpendingPub.decodeHexWithResult()
        .mapError { Error("Invalid app spending key hex from server", it) }
        .bind()

      val appSpendingKeyChaincode = chaincodeExtractor
        .extractChaincode(activeSpendingKeyset.appKey.key.xpub)
        .result
        .mapError { Error("Failed to extract app spending key chaincode", it) }
        .bind()

      val networkMainnet = account.keybox.config.bitcoinNetworkType == BitcoinNetworkType.BITCOIN

      val appAuthKey = response.appAuthPub.decodeHexWithResult()
        .mapError { Error("Invalid app auth key hex from server", it) }
        .bind()

      val serverSpendingXpub = ensureNotNull(activeSpendingKeyset.f8eSpendingKeyset.privateWalletRootXpub) {
        Error("Server spending xpub is required for private wallets")
      }

      val serverSpendingKeyChaincode = chaincodeExtractor
        .extractChaincode(serverSpendingXpub)
        .result
        .mapError { Error("Failed to extract server spending key chaincode", it) }
        .bind()

      val serverSpendingKey = response.serverSpendingPub.decodeHexWithResult()
        .mapError { Error("Invalid server spending key hex from server", it) }
        .bind()

      val wsmSignature = response.signature.decodeHexWithResult()
        .mapError { Error("Invalid WSM signature hex from server", it) }
        .bind()

      // Extract account index from hardware key's derivation path for Lost App Recovery support
      val accountIndex = activeSpendingKeyset.hardwareKey.key.extractAccountIndex()

      // Step 3: Return the NFC session lambda
      val nfcSession: suspend (NfcSession, NfcCommands) -> String = { session, commands ->
        commands.requireW3(session).verifyKeysAndBuildDescriptor(
          session = session,
          appSpendingKey = appSpendingKey,
          appSpendingKeyChaincode = appSpendingKeyChaincode,
          networkMainnet = networkMainnet,
          appAuthKey = appAuthKey,
          serverSpendingKey = serverSpendingKey,
          serverSpendingKeyChaincode = serverSpendingKeyChaincode,
          wsmSignature = wsmSignature,
          accountIndex = accountIndex,
        )
      }
      nfcSession
    }
}
