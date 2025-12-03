package build.wallet.cloud.backup

import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitcoin.keys.ExtendedPrivateKey
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe

class KeyboxKeysTests : FunSpec({
  val appPrivateKeyDao = AppPrivateKeyDaoFake()

  beforeTest {
    appPrivateKeyDao.reset()
  }

  test("appKeys returns all keys from dao") {
    val activePublicKey = SpendingKeysetMock.appKey
    val activePrivateKey = AppSpendingPrivateKey(
      ExtendedPrivateKey(xprv = "xprv-active", mnemonic = "active mnemonic")
    )

    val otherPublicKey = AppSpendingPublicKey(DescriptorPublicKeyMock(identifier = "other-dpub"))
    val otherPrivateKey = AppSpendingPrivateKey(
      ExtendedPrivateKey(xprv = "xprv-other", mnemonic = "other mnemonic")
    )

    appPrivateKeyDao.storeAppSpendingKeyPair(
      AppSpendingKeypair(publicKey = activePublicKey, privateKey = activePrivateKey)
    )
    appPrivateKeyDao.storeAppSpendingKeyPair(
      AppSpendingKeypair(publicKey = otherPublicKey, privateKey = otherPrivateKey)
    )

    val result = KeyboxMock.appKeys(appPrivateKeyDao).getOrThrow()

    result.shouldHaveSize(2)
    result.shouldContainKey(activePublicKey)
    result.shouldContainKey(otherPublicKey)
    result[activePublicKey].shouldBe(activePrivateKey)
    result[otherPublicKey].shouldBe(otherPrivateKey)
  }

  test("appKeys fails when active spending key is not in dao") {
    val result = KeyboxMock.appKeys(appPrivateKeyDao)

    result.shouldBeErrOfType<IllegalStateException>()
  }
})
