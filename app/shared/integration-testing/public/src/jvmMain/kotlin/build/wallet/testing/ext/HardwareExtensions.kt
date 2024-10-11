package build.wallet.testing.ext

import build.wallet.auth.AuthTokenScope
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.nfc.FakeHwAuthKeypair
import build.wallet.nfc.TransactionFn
import build.wallet.nfc.platform.signAccessToken
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
    app.appComponent.authTokensRepository
      .getAuthTokens(account.accountId, AuthTokenScope.Global)
      .toErrorIfNull { IllegalStateException("Auth tokens missing.") }
      .getOrThrow()
      .accessToken
  val signResponse =
    app.nfcTransactor.fakeTransact { session, command ->
      command.signAccessToken(session, accessToken)
    }.getOrThrow()
  return HwFactorProofOfPossession(signResponse)
}

/**
 * Signs some challenge with the fake hardware's auth private key.
 */
suspend fun AppTester.signChallengeWithHardware(challenge: ByteString): String {
  return app.nfcTransactor.fakeTransact { session, commands ->
    commands.signChallenge(session, challenge)
  }.getOrThrow()
}

suspend fun AppTester.signChallengeWithHardware(challenge: String): String {
  return signChallengeWithHardware(challenge.encodeUtf8())
}

suspend fun <T> AppTester.hardwareTransaction(transaction: TransactionFn<T>): T =
  app.nfcTransactor.fakeTransact(transaction).getOrThrow()

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
