package build.wallet.onboarding

import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.store.EncryptedKeyValueStoreFactoryFake
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.decodeHex

class OnboardingKeyboxSealedCsekDaoImplTests : FunSpec({
  val storeFactory = EncryptedKeyValueStoreFactoryFake()
  val dao = OnboardingKeyboxSealedCsekDaoImpl(storeFactory)

  val sealedCsek = SealedCsekFake
  val anotherSealedCsek =
    "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2".decodeHex()

  beforeTest {
    storeFactory.reset()
  }

  test("returns null when no sealed CSEK is persisted") {
    dao.get().shouldBe(Ok(null))
  }

  test("sets and retrieves sealed CSEK") {
    dao.set(sealedCsek).shouldBe(Ok(Unit))

    val result = dao.get()
    result.shouldBe(Ok(sealedCsek))
  }

  test("overwrites existing sealed CSEK") {
    dao.set(sealedCsek).shouldBe(Ok(Unit))
    dao.get().shouldBe(Ok(sealedCsek))

    dao.set(anotherSealedCsek).shouldBe(Ok(Unit))
    dao.get().shouldBe(Ok(anotherSealedCsek))
  }

  test("clears sealed CSEK from storage") {
    dao.set(sealedCsek).shouldBe(Ok(Unit))
    dao.get().shouldBe(Ok(sealedCsek))

    dao.clear().shouldBe(Ok(Unit))
    dao.get().shouldBe(Ok(null))
  }

  test("clear succeeds when no sealed CSEK exists") {
    dao.get().shouldBe(Ok(null))

    dao.clear().shouldBe(Ok(Unit))
    dao.get().shouldBe(Ok(null))
  }

  test("handles multiple set operations correctly") {
    dao.set(sealedCsek).shouldBe(Ok(Unit))
    dao.set(anotherSealedCsek).shouldBe(Ok(Unit))
    dao.set(sealedCsek).shouldBe(Ok(Unit))

    dao.get().shouldBe(Ok(sealedCsek))
  }

  test("sealed CSEK persists across multiple get operations") {
    dao.set(sealedCsek).shouldBe(Ok(Unit))

    dao.get().shouldBe(Ok(sealedCsek))
    dao.get().shouldBe(Ok(sealedCsek))
  }
})
