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

  test("appKeys returns keys in deterministic order regardless of insertion order") {
    // The active key must be present for appKeys to succeed
    val activePublicKey = SpendingKeysetMock.appKey
    val activePrivateKey = AppSpendingPrivateKey(
      ExtendedPrivateKey(xprv = "xprv-active", mnemonic = "active mnemonic")
    )

    // Create additional keys with dpubs that sort differently than insertion order
    val keyZ = AppSpendingKeypair(
      publicKey = AppSpendingPublicKey(DescriptorPublicKeyMock(identifier = "zzz-dpub")),
      privateKey = AppSpendingPrivateKey(ExtendedPrivateKey(xprv = "xprv-z", mnemonic = "z mnemonic"))
    )
    val keyA = AppSpendingKeypair(
      publicKey = AppSpendingPublicKey(DescriptorPublicKeyMock(identifier = "aaa-dpub")),
      privateKey = AppSpendingPrivateKey(ExtendedPrivateKey(xprv = "xprv-a", mnemonic = "a mnemonic"))
    )
    val keyM = AppSpendingKeypair(
      publicKey = AppSpendingPublicKey(DescriptorPublicKeyMock(identifier = "mmm-dpub")),
      privateKey = AppSpendingPrivateKey(ExtendedPrivateKey(xprv = "xprv-m", mnemonic = "m mnemonic"))
    )

    // Insert in non-alphabetical order: Z, active, A, M
    appPrivateKeyDao.storeAppSpendingKeyPair(keyZ)
    appPrivateKeyDao.storeAppSpendingKeyPair(
      AppSpendingKeypair(publicKey = activePublicKey, privateKey = activePrivateKey)
    )
    appPrivateKeyDao.storeAppSpendingKeyPair(keyA)
    appPrivateKeyDao.storeAppSpendingKeyPair(keyM)

    val result = KeyboxMock.appKeys(appPrivateKeyDao).getOrThrow()

    // Keys should be sorted alphabetically by dpub
    val dpubs = result.keys.map { it.key.dpub }
    dpubs.shouldBe(dpubs.sorted())
  }
})
