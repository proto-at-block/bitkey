package build.wallet.auth

import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.auth.AppGlobalAuthKeypairMock
import build.wallet.bitkey.auth.AppRecoveryAuthKeypairMock
import build.wallet.encrypt.MessageSignerFake
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import okio.ByteString.Companion.encodeUtf8

class AppAuthKeyMessageSignerImplTests : FunSpec({
  val appPrivateKeyDao = AppPrivateKeyDaoFake()
  val messageSigner = MessageSignerFake()
  val appAuthKeyMessageSigner = AppAuthKeyMessageSignerImpl(appPrivateKeyDao, messageSigner)

  beforeTest {
    appPrivateKeyDao.reset()
  }

  listOf(
    AppGlobalAuthKeypairMock,
    AppRecoveryAuthKeypairMock
  ).forEach { authKeypair ->
    context(authKeypair.toString()) {
      test("successfully sign a message with existing private key") {
        appPrivateKeyDao.appAuthKeys[authKeypair.publicKey] = authKeypair.privateKey

        appAuthKeyMessageSigner
          .signMessage(
            publicKey = authKeypair.publicKey,
            message = "some-message".encodeUtf8()
          )
          .shouldBeOk("signed-some-message")
      }

      test("fail to sign a message - missing private key") {
        appAuthKeyMessageSigner
          .signMessage(
            publicKey = authKeypair.publicKey,
            message = "some-message".encodeUtf8()
          )
          .shouldBeErrOfType<AppAuthKeyMissingError>()
      }
    }
  }
})
