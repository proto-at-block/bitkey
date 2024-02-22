package build.wallet.recovery.socrec

import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.keys.app.AppKeyImpl
import build.wallet.bitkey.socrec.ProtectedCustomerIdentityKey
import build.wallet.crypto.CurveType
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.getError
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.utils.io.core.toByteArray
import okio.ByteString.Companion.toByteString

class SocRecKeysDaoImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()
  lateinit var dao: SocRecKeysDaoImpl

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    dao =
      SocRecKeysDaoImpl(
        databaseProvider = databaseProvider,
        appPrivateKeyDao = AppPrivateKeyDaoFake()
      )
  }

  test("persist and retrieve keypair") {
    val key =
      ProtectedCustomerIdentityKey(
        key =
          AppKeyImpl(
            CurveType.SECP256K1,
            PublicKey(value = "pubkey"),
            PrivateKey(bytes = "privkey".toByteArray().toByteString())
          )
      )

    dao.saveKey(key)
    dao.getKeyWithPrivateMaterial(::ProtectedCustomerIdentityKey)
      .shouldNotBeNull()
      .shouldBeOk(key)

    dao.getKey(::ProtectedCustomerIdentityKey)
      .shouldNotBeNull()
      .shouldBeOk()
      .shouldBeEqual(
        ProtectedCustomerIdentityKey(
          AppKeyImpl(
            CurveType.SECP256K1,
            key.publicKey,
            null
          )
        )
      )
  }

  test("persist public key and retrieve") {
    val key =
      ProtectedCustomerIdentityKey(
        key =
          AppKeyImpl(
            CurveType.SECP256K1,
            PublicKey(value = "pubkey"),
            null
          )
      )

    dao.saveKey(key)
    dao.getKeyWithPrivateMaterial(::ProtectedCustomerIdentityKey)
      .shouldBeErrOfType<SocRecKeyError.NoPrivateKeyAvailable>()

    dao.getKey(::ProtectedCustomerIdentityKey)
      .shouldNotBeNull()
      .shouldBeOk(key)
  }

  test("retrieving empty keys returns error") {
    dao.getKey(::ProtectedCustomerIdentityKey)
      .getError()
      .shouldNotBeNull()
      .shouldBeInstanceOf<SocRecKeyError.NoKeyAvailable>()
  }
})
