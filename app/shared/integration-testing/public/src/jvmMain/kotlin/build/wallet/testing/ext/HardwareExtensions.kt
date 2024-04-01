package build.wallet.testing.ext

import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.keybox.Keybox
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.nfc.FakeHwAuthKeypair
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

suspend fun AppTester.getHardwareFactorProofOfPossession(
  keybox: Keybox,
): HwFactorProofOfPossession {
  val accessToken =
    app.appComponent.authTokensRepository
      .getAuthTokens(keybox.fullAccountId, AuthTokenScope.Global)
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
