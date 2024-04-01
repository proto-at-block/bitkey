package build.wallet.recovery.socrec

import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.DelegatedDecryptionKey
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.sqldelight.inMemorySqlDriver
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
    val key = AppKey<DelegatedDecryptionKey>(
      PublicKey(value = "pubkey"),
      PrivateKey(bytes = "privkey".toByteArray().toByteString())
    )

    dao.saveKey(key)
    dao.getKeyWithPrivateMaterial<DelegatedDecryptionKey>()
      .shouldNotBeNull()
      .shouldBeOk(key)

    dao.getPublicKey<DelegatedDecryptionKey>()
      .shouldNotBeNull()
      .shouldBeOk()
      .shouldBeEqual(
        key.publicKey
      )
  }

  test("retrieving empty keys returns error") {
    dao.getPublicKey<DelegatedDecryptionKey>()
      .getError()
      .shouldNotBeNull()
      .shouldBeInstanceOf<SocRecKeyError.NoKeyAvailable>()
  }
})
