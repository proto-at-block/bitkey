package build.wallet.testing.ext

import bitkey.auth.AuthTokenScope
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.crypto.PublicKey
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.nfc.FakeHwAuthKeypair
import build.wallet.nfc.TransactionFn
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.nfc.platform.signAccessToken
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintEnrolled
import build.wallet.testing.AppTester
import build.wallet.testing.fakeTransact
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.toErrorIfNull
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

suspend fun AppTester.getActiveHwAuthKey(): FakeHwAuthKeypair {
  return fakeHardwareKeyStore.getAuthKeypair()
}

suspend fun AppTester.getHardwareFactorProofOfPossession(): HwFactorProofOfPossession {
  val account = getActiveFullAccount()
  val accessToken =
    authTokensService
      .getTokens(account.accountId, AuthTokenScope.Global)
      .toErrorIfNull { IllegalStateException("Auth tokens missing.") }
      .getOrThrow()
      .accessToken
  val signResponse =
    nfcTransactor.fakeTransact { session, command ->
      command.signAccessToken(session, accessToken)
    }.getOrThrow()
  return HwFactorProofOfPossession(signResponse)
}

/**
 * Signs some challenge with the fake hardware's auth private key.
 */
suspend fun AppTester.signChallengeWithHardware(challenge: ByteString): String {
  return nfcTransactor.fakeTransact { session, commands ->
    commands.signChallenge(session, challenge)
  }.getOrThrow()
}

suspend fun AppTester.signChallengeWithHardware(challenge: String): String {
  return signChallengeWithHardware(challenge.encodeUtf8())
}

suspend fun AppTester.startAndCompleteFingerprintEnrolment(
  appAuthKey: PublicKey<AppGlobalAuthKey>,
): FingerprintEnrolled {
  // Start fingerprint enrollment, which is just a pairing attempt before fingerprint enrollment
  pairingTransactionProvider(
    appGlobalAuthPublicKey = appAuthKey,
    onSuccess = {},
    onCancel = {}
  ).let { transaction ->
    nfcTransactor.fakeTransact(
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
      transaction = transaction::session
    ).getOrThrow().also { transaction.onSuccess(it) }
  } as FingerprintEnrolled
}

suspend fun <T> AppTester.hardwareTransaction(transaction: TransactionFn<T>): T =
  nfcTransactor.fakeTransact(transaction).getOrThrow()

/**
 * Shortcut to sign a PSBT with the fake hardware. Returns the signed PSBT.
 * Automatically handles both W1 (single-tap) and W3 (two-tap with confirmation) flows.
 */
suspend fun AppTester.signPsbtWithHardware(psbt: Psbt): Psbt {
  return hardwareTransaction { session, commands ->
    val account = getActiveFullAccount()
    val interaction = commands.signTransaction(
      session = session,
      psbt = psbt,
      spendingKeyset = account.keybox.activeSpendingKeyset
    )

    when (interaction) {
      is HardwareInteraction.Completed -> {
        // W1 path: immediate completion
        interaction.result
      }

      is HardwareInteraction.RequiresTransfer -> {
        // W3 path: chunked transfer required
        val nextInteraction = interaction.transferAndFetch(session, commands) {}

        when (nextInteraction) {
          is HardwareInteraction.ConfirmWithEmulatedPrompt -> {
            // Fake W3 hardware: automatically approve
            val approveOption = nextInteraction.options.first { it.name == "Approve" }
            approveOption.onSelect?.invoke()

            // Fetch the result in a second NFC session
            hardwareTransaction { session2, commands2 ->
              val finalInteraction = approveOption.fetchResult(session2, commands2)
              when (finalInteraction) {
                is HardwareInteraction.Completed -> finalInteraction.result
                else -> error("Expected Completed after W3 confirmation, got ${finalInteraction::class.simpleName}")
              }
            }
          }
          is HardwareInteraction.Completed -> {
            // Already completed after transfer
            nextInteraction.result
          }
          else -> error("Unexpected interaction after W3 transfer: ${nextInteraction::class.simpleName}")
        }
      }

      else -> error("Unexpected interaction type while signing PSBT: ${interaction::class.simpleName}")
    }
  }
}
