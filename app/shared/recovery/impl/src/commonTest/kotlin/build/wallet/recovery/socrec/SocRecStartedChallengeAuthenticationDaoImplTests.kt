package build.wallet.recovery.socrec

import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.PakeCode
import build.wallet.bitkey.socrec.ProtectedCustomerRecoveryPakeKey
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.utils.io.core.toByteArray
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

class SocRecStartedChallengeAuthenticationDaoImplTests : FunSpec({

  val sqlDriver = inMemorySqlDriver()

  test("crud") {
    val dao =
      SocRecStartedChallengeAuthenticationDaoImpl(
        AppPrivateKeyDaoFake(),
        BitkeyDatabaseProviderImpl(sqlDriver.factory)
      )
    val relationshipId = "a"
    val key = AppKey<ProtectedCustomerRecoveryPakeKey>(
      PublicKey("pub"),
      PrivateKey("priv".encodeUtf8())
    )
    val pakeCode = PakeCode("12345678901".toByteArray().toByteString())
    dao.insert(relationshipId, key, pakeCode = pakeCode)
      .shouldBeOk()

    dao.getByRelationshipId(relationshipId)
      .shouldBeOk()
      .shouldNotBeNull()
      .shouldBeEqual(
        SocRecStartedChallengeAuthenticationDao.SocRecStartedChallengeAuthenticationRow(
          relationshipId = relationshipId,
          protectedCustomerRecoveryPakeKey = key,
          pakeCode = pakeCode
        )
      )
    dao.deleteByRelationshipId(relationshipId)
      .shouldBeOk()
    dao.getByRelationshipId(relationshipId)
      .getOrThrow()
      .shouldBeNull()
  }
})
