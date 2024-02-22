package build.wallet.auth

import app.cash.turbine.test
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.bitkey.auth.HwAuthSecp256k1PublicKeyMock
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.sqldelight.inMemorySqlDriver
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AuthKeyRotationAttemptDaoImplTests : FunSpec({

  val sqlDriver = inMemorySqlDriver()

  lateinit var dao: AuthKeyRotationAttemptDaoImpl

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    dao = AuthKeyRotationAttemptDaoImpl(databaseProvider)
  }

  test("setAuthKeysWritten() should set the AuthKeyRotationAttemptDaoState to AuthKeysWritten") {
    val keys = AppAuthPublicKeys(
      appGlobalAuthPublicKey = AppGlobalAuthPublicKeyMock,
      appRecoveryAuthPublicKey = AppRecoveryAuthPublicKeyMock
    )
    dao.getAuthKeyRotationAttemptState().test {
      dao.setAuthKeysWritten(
        appAuthPublicKeys = keys,
        hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock
      )

      dao.setServerRotationAttemptComplete()

      dao.setAuthKeysWritten(
        appAuthPublicKeys = keys,
        hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock
      )

      awaitItem().shouldBe(Ok(AuthKeyRotationAttemptDaoState.NoAttemptInProgress))
      awaitItem().shouldBe(Ok(AuthKeyRotationAttemptDaoState.AuthKeysWritten(keys, HwAuthSecp256k1PublicKeyMock)))
      awaitItem().shouldBe(Ok(AuthKeyRotationAttemptDaoState.ServerRotationAttemptComplete(keys)))
    }
  }
})
