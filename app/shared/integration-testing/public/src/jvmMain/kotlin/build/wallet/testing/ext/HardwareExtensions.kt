package build.wallet.testing.ext

import build.wallet.auth.AuthTokenScope
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.crypto.PublicKey
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.nfc.FakeHwAuthKeypair
import build.wallet.nfc.TransactionFn
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
    networkType = initialBitcoinNetworkType,
    appGlobalAuthPublicKey = appAuthKey,
    onSuccess = {},
    onCancel = {},
    isHardwareFake = true
  ).let { transaction ->
    nfcTransactor.fakeTransact(
      transaction = transaction::session
    ).getOrThrow().also { transaction.onSuccess(it) }
  }

  // Generate hardware keys
  return pairingTransactionProvider(
    networkType = initialBitcoinNetworkType,
    appGlobalAuthPublicKey = appAuthKey,
    onSuccess = {},
    onCancel = {},
    isHardwareFake = true
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
 */
suspend fun AppTester.signPsbtWithHardware(psbt: Psbt): Psbt {
  return hardwareTransaction { session, commands ->
    commands.signTransaction(
      session = session,
      psbt = psbt,
      spendingKeyset = getActiveFullAccount().keybox.activeSpendingKeyset
    )
  }
}
