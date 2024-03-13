package build.wallet.auth

import app.cash.turbine.test
import build.wallet.bitkey.auth.AppAuthPublicKeysMock
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
    val keys = AppAuthPublicKeysMock
    dao.observeAuthKeyRotationAttemptState().test {
      dao.setKeyRotationProposal()

      dao.setAuthKeysWritten(
        appAuthPublicKeys = keys
      )

      dao.clear()

      awaitItem().shouldBe(Ok(AuthKeyRotationAttemptDaoState.NoAttemptInProgress))
      awaitItem().shouldBe(Ok(AuthKeyRotationAttemptDaoState.KeyRotationProposalWritten))
      awaitItem().shouldBe(Ok(AuthKeyRotationAttemptDaoState.AuthKeysWritten(keys)))
      awaitItem().shouldBe(Ok(AuthKeyRotationAttemptDaoState.NoAttemptInProgress))
    }
  }
})
