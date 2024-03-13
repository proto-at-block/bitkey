package build.wallet.recovery.socrec

import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.keys.app.AppKeyImpl
import build.wallet.bitkey.socrec.PakeCode
import build.wallet.bitkey.socrec.ProtectedCustomerEnrollmentPakeKey
import build.wallet.crypto.CurveType
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.database.sqldelight.SocRecEnrollmentAuthentication
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

class SocRecEnrollmentAuthenticationDaoImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()

  test("crud") {
    val dao =
      SocRecEnrollmentAuthenticationDaoImpl(
        AppPrivateKeyDaoFake(),
        BitkeyDatabaseProviderImpl(sqlDriver.factory)
      )
    val key =
      ProtectedCustomerEnrollmentPakeKey(
        AppKeyImpl(
          CurveType.Curve25519,
          PublicKey("pub"),
          PrivateKey("priv".encodeUtf8())
        )
      )
    val relationshipId = "a"
    val pakeCode = PakeCode("F00DBAR".toByteArray().toByteString())
    dao.insert(relationshipId, key, pakeCode)
      .shouldBeOk()

    dao.getByRelationshipId(relationshipId)
      .shouldBeOk()
      .shouldNotBeNull()
      .shouldBeEqual(
        SocRecEnrollmentAuthentication(
          recoveryRelationshipId = relationshipId,
          protectedCustomerEnrollmentPakeKey = key,
          pakeCode = pakeCode.bytes
        )
      )
    dao.deleteByRelationshipId(relationshipId)
      .shouldBeOk()
    dao.getByRelationshipId(relationshipId)
      .getOrThrow()
      .shouldBeNull()
  }
})
