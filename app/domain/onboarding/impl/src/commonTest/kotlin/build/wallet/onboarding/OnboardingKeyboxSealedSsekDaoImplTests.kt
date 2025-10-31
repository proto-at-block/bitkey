package build.wallet.onboarding

import build.wallet.cloud.backup.csek.SealedSsekFake
import build.wallet.store.EncryptedKeyValueStoreFactoryFake
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.decodeHex

class OnboardingKeyboxSealedSsekDaoImplTests : FunSpec({
  val storeFactory = EncryptedKeyValueStoreFactoryFake()
  val dao = OnboardingKeyboxSealedSsekDaoImpl(storeFactory)

  val sealedSsek = SealedSsekFake
  val anotherSealedSsek =
    "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2".decodeHex()

  beforeTest {
    storeFactory.reset()
  }

  test("returns null when no sealed SSEK is persisted") {
    dao.get().shouldBe(Ok(null))
  }

  test("sets and retrieves sealed SSEK") {
    dao.set(sealedSsek).shouldBe(Ok(Unit))

    val result = dao.get()
    result.shouldBe(Ok(sealedSsek))
  }

  test("overwrites existing sealed SSEK") {
    dao.set(sealedSsek).shouldBe(Ok(Unit))
    dao.get().shouldBe(Ok(sealedSsek))

    dao.set(anotherSealedSsek).shouldBe(Ok(Unit))
    dao.get().shouldBe(Ok(anotherSealedSsek))
  }

  test("clears sealed SSEK from storage") {
    dao.set(sealedSsek).shouldBe(Ok(Unit))
    dao.get().shouldBe(Ok(sealedSsek))

    dao.clear().shouldBe(Ok(Unit))
    dao.get().shouldBe(Ok(null))
  }

  test("clear succeeds when no sealed SSEK exists") {
    dao.get().shouldBe(Ok(null))

    dao.clear().shouldBe(Ok(Unit))
    dao.get().shouldBe(Ok(null))
  }

  test("handles multiple set operations correctly") {
    dao.set(sealedSsek).shouldBe(Ok(Unit))
    dao.set(anotherSealedSsek).shouldBe(Ok(Unit))
    dao.set(sealedSsek).shouldBe(Ok(Unit))

    dao.get().shouldBe(Ok(sealedSsek))
  }

  test("sealed SSEK persists across multiple get operations") {
    dao.set(sealedSsek).shouldBe(Ok(Unit))

    dao.get().shouldBe(Ok(sealedSsek))
    dao.get().shouldBe(Ok(sealedSsek))
  }
})
