package build.wallet.cloud.backup

import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitcoin.keys.ExtendedPrivateKey
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.spending.PrivateSpendingKeysetMock
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

  test("appKeys returns keys for keysets in keybox that have private keys in dao") {
    val keybox = KeyboxMock.copy(
      activeSpendingKeyset = SpendingKeysetMock,
      keysets = listOf(SpendingKeysetMock, PrivateSpendingKeysetMock)
    )

    val activePublicKey = keybox.activeSpendingKeyset.appKey
    val activePrivateKey = AppSpendingPrivateKey(
      ExtendedPrivateKey(xprv = "xprv-active", mnemonic = "active mnemonic")
    )

    val inactivePublicKey = PrivateSpendingKeysetMock.appKey
    val otherPrivateKey = AppSpendingPrivateKey(
      ExtendedPrivateKey(xprv = "xprv-other", mnemonic = "other mnemonic")
    )

    appPrivateKeyDao.storeAppSpendingKeyPair(
      AppSpendingKeypair(publicKey = activePublicKey, privateKey = activePrivateKey)
    )
    appPrivateKeyDao.storeAppSpendingKeyPair(
      AppSpendingKeypair(publicKey = inactivePublicKey, privateKey = otherPrivateKey)
    )

    // Store an additional key that is NOT in the keybox keysets; it should be ignored.
    val extraPublicKey = AppSpendingPublicKey(DescriptorPublicKeyMock(identifier = "extra-dpub"))
    val extraPrivateKey = AppSpendingPrivateKey(
      ExtendedPrivateKey(xprv = "xprv-extra", mnemonic = "extra mnemonic")
    )
    appPrivateKeyDao.storeAppSpendingKeyPair(
      AppSpendingKeypair(publicKey = extraPublicKey, privateKey = extraPrivateKey)
    )

    val result = keybox.appKeys(appPrivateKeyDao).getOrThrow()

    result.shouldHaveSize(2)
    result.shouldContainKey(activePublicKey)
    result.shouldContainKey(inactivePublicKey)
    result[activePublicKey].shouldBe(activePrivateKey)
    result[inactivePublicKey].shouldBe(otherPrivateKey)
  }

  test("appKeys fails when active spending key is not in dao") {
    val result = KeyboxMock.appKeys(appPrivateKeyDao)

    result.shouldBeErrOfType<IllegalStateException>()
  }

  test("appKeys succeeds when inactive spending key is missing from dao") {
    val keybox = KeyboxMock.copy(
      activeSpendingKeyset = SpendingKeysetMock,
      keysets = listOf(SpendingKeysetMock, PrivateSpendingKeysetMock)
    )

    // The active key must be present for appKeys to succeed
    val activePublicKey = SpendingKeysetMock.appKey
    val activePrivateKey = AppSpendingPrivateKey(
      ExtendedPrivateKey(xprv = "xprv-active", mnemonic = "active mnemonic")
    )

    appPrivateKeyDao.storeAppSpendingKeyPair(
      AppSpendingKeypair(publicKey = activePublicKey, privateKey = activePrivateKey)
    )

    val result = keybox.appKeys(appPrivateKeyDao).getOrThrow()

    result.shouldHaveSize(1)
    result.shouldContainKey(activePublicKey)
    result[activePublicKey].shouldBe(activePrivateKey)
  }

  test("appKeys returns keys in deterministic order regardless of keyset order") {
    val activeKeyset = SpendingKeysetMock

    // Create keysets with dpubs that sort differently than insertion order.
    val keysetZ = activeKeyset.copy(
      localId = "keyset-z",
      appKey = AppSpendingPublicKey(DescriptorPublicKeyMock(identifier = "zzz-dpub"))
    )
    val keysetA = activeKeyset.copy(
      localId = "keyset-a",
      appKey = AppSpendingPublicKey(DescriptorPublicKeyMock(identifier = "aaa-dpub"))
    )
    val keysetM = activeKeyset.copy(
      localId = "keyset-m",
      appKey = AppSpendingPublicKey(DescriptorPublicKeyMock(identifier = "mmm-dpub"))
    )

    // Insert keysets in non-alphabetical order: Z (active), A, M
    val keybox = KeyboxMock.copy(
      activeSpendingKeyset = keysetZ,
      keysets = listOf(keysetZ, keysetA, keysetM)
    )

    // Store all private keys (active must be present).
    listOf(keysetZ.appKey to "xprv-z", keysetA.appKey to "xprv-a", keysetM.appKey to "xprv-m")
      .forEach { (publicKey, xprv) ->
        appPrivateKeyDao.storeAppSpendingKeyPair(
          AppSpendingKeypair(
            publicKey = publicKey,
            privateKey = AppSpendingPrivateKey(ExtendedPrivateKey(xprv = xprv, mnemonic = "$xprv mnemonic"))
          )
        )
      }

    val result = keybox.appKeys(appPrivateKeyDao).getOrThrow()

    // Keys should be sorted alphabetically by dpub
    val dpubs = result.keys.map { it.key.dpub }
    dpubs.shouldBe(dpubs.sorted())
  }
})
