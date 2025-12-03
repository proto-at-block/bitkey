package build.wallet.cloud.backup.csek

import bitkey.data.PrivateData
import build.wallet.store.EncryptedKeyValueStoreFactoryFake
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

@OptIn(PrivateData::class)
class CsekDaoImplTests : FunSpec({
  val secureStoreFactory = EncryptedKeyValueStoreFactoryFake()
  val dao = CsekDaoImpl(secureStoreFactory)

  val csek = CsekFake
  val sealedCsek = SealedCsekFake

  afterTest {
    secureStoreFactory.store.clear()
  }

  test("no CSEK persisted") {
    dao.get(sealedCsek).shouldBe(Ok(null))
  }

  test("set new CSEK") {
    dao.set(key = sealedCsek, value = csek)

    dao.get(sealedCsek).shouldBe(Ok(csek))
  }

  test("CSEK already present") {
    secureStoreFactory.store.putString(
      key = sealedCsek.hex(),
      value = csek.key.raw.hex()
    )

    dao.get(sealedCsek).shouldBe(Ok(csek))
  }

  test("CSEK no longer persisted") {
    dao.set(key = sealedCsek, value = csek)

    secureStoreFactory.store.clear()

    dao.get(sealedCsek).shouldBe(Ok(null))
  }
})
