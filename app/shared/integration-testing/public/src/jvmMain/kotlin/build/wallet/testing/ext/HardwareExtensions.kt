@file:Suppress("TooManyFunctions")

package build.wallet.testing.ext

import bitkey.account.HardwareType
import bitkey.auth.AuthTokenScope
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.keybox.Keybox
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.signResult
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.nfc.FakeHwAuthKeypair
import build.wallet.nfc.TransactionFn
import build.wallet.nfc.platform.ActionProofAction
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.nfc.platform.requireW3
import build.wallet.nfc.platform.signAccessToken
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintEnrolled
import build.wallet.statemachine.auth.ActionProofType
import build.wallet.testing.AppTester
import build.wallet.testing.fakeTransact
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.toErrorIfNull
import io.kotest.matchers.shouldBe
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import kotlin.coroutines.cancellation.CancellationException

suspend fun AppTester.getActiveHwAuthKey(): FakeHwAuthKeypair {
  return fakeHardwareKeyStore.getAuthKeypair()
}

suspend fun AppTester.getActiveW3HwAuthKey(): FakeHwAuthKeypair {
  return w3FakeHardwareKeyStore.getAuthKeypair()
}

suspend fun AppTester.getHardwareFactorProofOfPossession(): HwFactorProofOfPossession {
  val account = getActiveFullAccount()
  require(account.config.hardwareType == HardwareType.W1) {
    "HwFactorProofOfPossession is only valid for W1 accounts. Use the concrete action flow for W3."
  }
  val accessToken =
    authTokensService
      .getTokens(account.accountId, AuthTokenScope.Global)
      .toErrorIfNull { IllegalStateException("Auth tokens missing.") }
      .getOrThrow()
      .accessToken
  val signResponse =
    nfcTransactor.fakeTransact(hardwareType = HardwareType.W1) { session, command ->
      command.signAccessToken(session, accessToken)
    }.getOrThrow()
  return HwFactorProofOfPossession(signResponse)
}

/**
 * Signs some challenge with the fake hardware's auth private key.
 */
suspend fun AppTester.signChallengeWithHardware(challenge: ByteString): String {
  val hardwareType = resolveFakeHardwareType()
  return nfcTransactor.fakeTransact(hardwareType = hardwareType) { session, commands ->
    commands.signChallenge(session, challenge)
  }.getOrThrow()
}

suspend fun AppTester.signChallengeWithHardware(challenge: String): String {
  return signChallengeWithHardware(challenge.encodeUtf8())
}

suspend fun AppTester.startAndCompleteFingerprintEnrolment(
  appAuthKey: PublicKey<AppGlobalAuthKey>,
  hardwareType: HardwareType? = null,
): FingerprintEnrolled {
  val resolvedHardwareType = resolveFakeHardwareType(hardwareType)
  // Start fingerprint enrollment, which is just a pairing attempt before fingerprint enrollment
  pairingTransactionProvider(
    appGlobalAuthPublicKey = appAuthKey,
    onSuccess = {},
    onCancel = {}
  ).let { transaction ->
    nfcTransactor.fakeTransact(
      hardwareType = resolvedHardwareType,
      transaction = transaction::session
    ).getOrThrow().also { transaction.onSuccess(it) }
  }

  // Generate hardware keys
  return pairingTransactionProvider(
    appGlobalAuthPublicKey = appAuthKey,
    onSuccess = {},
    onCancel = {}
  ).let { transaction ->
    nfcTransactor.fakeTransact(
      hardwareType = resolvedHardwareType,
      transaction = transaction::session
    ).getOrThrow().also { transaction.onSuccess(it) }
  } as FingerprintEnrolled
}

suspend fun <T> AppTester.hardwareTransaction(
  hardwareType: HardwareType? = null,
  transaction: TransactionFn<T>,
): T =
  nfcTransactor.fakeTransact(
    hardwareType = resolveFakeHardwareType(hardwareType),
    transaction = transaction
  ).getOrThrow()

suspend fun AppTester.assertActiveHardwareType(expected: HardwareType) {
  getActiveFullAccount().keybox.config.hardwareType.shouldBe(expected)
}

/**
 * Builds a W3 hardware-signed action proof without driving the UI flow.
 */
suspend fun AppTester.buildW3HardwareActionProof(
  actionProofType: ActionProofType,
  appAuthKey: PublicKey<AppGlobalAuthKey>,
  accountId: FullAccountId,
): PrivilegedActionProof.HwSignedAction {
  val signedPayload = actionProofService.buildAppSignedPayload(
    action = actionProofType.action,
    value = actionProofType.value,
    extra = actionProofType.extra,
    appAuthKey = appAuthKey,
    accountId = accountId
  ).getOrThrow()

  val initialInteraction =
    nfcTransactor.fakeTransact(hardwareType = HardwareType.W3) { session, commands ->
      commands.requireW3(session).signActionProof(
        session = session,
        version = 1u,
        action = ActionProofAction.from(actionProofType.action),
        value = actionProofType.value,
        bindings = signedPayload.bindings
      )
    }.getOrThrow()

  val hwSignature =
    when (initialInteraction) {
      is HardwareInteraction.Completed -> initialInteraction.result
      is HardwareInteraction.ConfirmWithEmulatedPrompt -> {
        initialInteraction.approve.onSelect?.invoke()

        nfcTransactor.fakeTransact(hardwareType = HardwareType.W3) { session, commands ->
          when (val finalInteraction = initialInteraction.approve.fetchResult(session, commands)) {
            is HardwareInteraction.Completed -> finalInteraction.result
            else ->
              error(
                "Expected Completed after W3 confirmation, got ${finalInteraction::class.simpleName}"
              )
          }
        }.getOrThrow()
      }
      else -> error("Unexpected interaction while signing action proof: ${initialInteraction::class.simpleName}")
    }.lowercase()

  val header = actionProofService.createActionProofHeader(
    signatures = listOf(signedPayload.appSignature, hwSignature),
    nonce = signedPayload.nonce
  ).getOrThrow()

  return PrivilegedActionProof.HwSignedAction(actionProof = header)
}

/**
 * Shortcut to sign a PSBT with the fake hardware. Returns the signed PSBT.
 * Automatically handles both W1 (single-tap) and W3 (two-tap with confirmation) flows.
 */
suspend fun AppTester.signPsbtWithHardware(psbt: Psbt): Psbt {
  val account = getActiveFullAccount()
  val hardwareType = account.config.hardwareType
  val initialInteraction =
    nfcTransactor.fakeTransact(hardwareType = hardwareType) { session, commands ->
      when (
        val interaction = commands.signTransaction(
          session = session,
          psbt = psbt,
          spendingKeyset = account.keybox.activeSpendingKeyset
        )
      ) {
        is HardwareInteraction.Completed -> interaction
        is HardwareInteraction.ConfirmWithEmulatedPrompt -> interaction
        is HardwareInteraction.RequiresTransfer -> interaction.transferAndFetch(session, commands) {}
        else -> error("Unexpected interaction type while signing PSBT: ${interaction::class.simpleName}")
      }
    }.getOrThrow()

  return when (initialInteraction) {
    is HardwareInteraction.Completed -> initialInteraction.result
    is HardwareInteraction.ConfirmWithEmulatedPrompt -> {
      initialInteraction.approve.onSelect?.invoke()

      nfcTransactor.fakeTransact(hardwareType = hardwareType) { session, commands ->
        when (val finalInteraction = initialInteraction.approve.fetchResult(session, commands)) {
          is HardwareInteraction.Completed -> finalInteraction.result
          else -> error("Expected Completed after W3 confirmation, got ${finalInteraction::class.simpleName}")
        }
      }.getOrThrow()
    }
    else -> error("Unexpected interaction after W3 transfer: ${initialInteraction::class.simpleName}")
  }
}

/**
 * Signs the app global auth key with the W3 fake hardware auth key and persists
 * the signature on the keybox. Returns the updated keybox.
 */
suspend fun AppTester.signW3AppGlobalAuthKeyHwSignature(
  keybox: Keybox,
  appAuthKey: PublicKey<AppGlobalAuthKey>,
): Keybox {
  val appAuthKeyMessage = (
    "BKRelationshipEndorsement".encodeUtf8().toByteArray() +
      appAuthKey.value.encodeUtf8().toByteArray()
  ).toByteString()
  val signature = messageSigner
    .signResult(appAuthKeyMessage, w3FakeHardwareKeyStore.getAuthKeypair().privateKey.key)
    .getOrThrow()
  return keyboxDao.updateAppGlobalAuthKeyHwSignature(
    keybox = keybox,
    signature = AppGlobalAuthKeyHwSignature(signature)
  ).getOrThrow()
}

private suspend fun AppTester.resolveFakeHardwareType(
  explicitHardwareType: HardwareType? = null,
): HardwareType {
  return explicitHardwareType
    ?: try {
      getActiveFullAccount().config.hardwareType
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      defaultAccountConfigService.defaultConfig().value.hardwareType ?: HardwareType.W1
    }
}
