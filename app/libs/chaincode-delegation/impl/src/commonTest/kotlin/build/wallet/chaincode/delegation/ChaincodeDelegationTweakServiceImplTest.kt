package build.wallet.chaincode.delegation

import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitcoin.keys.ExtendedPrivateKey
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.PrivateWalletKeyboxMock
import build.wallet.bitkey.spending.AppSpendingPrivateKeyMock
import build.wallet.bitkey.spending.PrivateSpendingKeysetMock
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.coroutines.turbine.turbines
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.money.BitcoinMoney
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ChaincodeDelegationTweakServiceImplTest : FunSpec({

  val originalPsbt = Psbt(
    id = "psbt-id",
    base64 = "original-psbt",
    fee = Fee(BitcoinMoney.sats(0)),
    baseSize = 0,
    numOfInputs = 0,
    amountSats = 0u
  )

  val keyboxDao = KeyboxDaoMock(turbine = turbines::create, defaultActiveKeybox = PrivateWalletKeyboxMock)
  val psbtUtils = PsbtUtilsFake()
  val appPrivateKeyDao = AppPrivateKeyDaoFake()

  val service = ChaincodeDelegationTweakServiceImpl(
    psbtUtils = psbtUtils,
    appPrivateKeyDao = appPrivateKeyDao,
    keyboxDao = keyboxDao
  )

  beforeTest {
    appPrivateKeyDao.reset()
    keyboxDao.reset()
    psbtUtils.reset()
  }

  fun createPrivateKeyset(
    localId: String,
    keysetIdSuffix: String,
    hasRootXpub: Boolean = true,
  ): SpendingKeyset =
    SpendingKeyset(
      localId = localId,
      f8eSpendingKeyset = F8eSpendingKeyset(
        keysetId = "$keysetIdSuffix-keyset-id",
        spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKeyMock("$keysetIdSuffix-server-dpub")),
        privateWalletRootXpub = if (hasRootXpub) "$keysetIdSuffix-root-xpub" else null
      ),
      networkType = build.wallet.bitcoin.BitcoinNetworkType.SIGNET,
      appKey = AppSpendingPublicKey(DescriptorPublicKeyMock("$keysetIdSuffix-app-dpub")),
      hardwareKey = HwSpendingPublicKey(DescriptorPublicKeyMock("$keysetIdSuffix-hw-dpub"))
    )

  suspend fun storeAppPrivateKey(
    publicKey: AppSpendingPublicKey,
    privateDprv: String = "app-dprv",
  ) {
    appPrivateKeyDao.storeAppSpendingKeyPair(
      AppSpendingKeypair(
        publicKey = publicKey,
        privateKey = AppSpendingPrivateKeyMock.copy(
          key = ExtendedPrivateKey(privateDprv, "mnemonic")
        )
      )
    )
  }

  test("returns PSBT with applied tweaks on success") {
    appPrivateKeyDao.storeAppSpendingKeyPair(
      AppSpendingKeypair(
        publicKey = PrivateSpendingKeysetMock.appKey,
        privateKey = AppSpendingPrivateKeyMock
      )
    )

    val result = service.psbtWithTweaks(originalPsbt)

    result.shouldBeOk { tweaked ->
      tweaked.base64 shouldBe "psbt-with-tweaks"
      tweaked.id shouldBe originalPsbt.id
    }
  }

  test("returns PSBT with applied tweaks when keyset is provided") {
    val keyset = createPrivateKeyset("test-private", "test")
    storeAppPrivateKey(keyset.appKey, "test-app-dprv")

    val result = service.psbtWithTweaks(originalPsbt, AppSpendingPrivateKeyMock.key, keyset)

    result.shouldBeOk { tweaked ->
      tweaked.base64 shouldBe "psbt-with-tweaks"
      tweaked.id shouldBe originalPsbt.id
    }
  }

  test("sweepPsbtWithTweaks applies tweaks for private-to-private sweep") {
    val sourcePrivateKeyset = createPrivateKeyset("source-private", "source")
    val destPrivateKeyset = createPrivateKeyset("dest-private", "dest")

    storeAppPrivateKey(destPrivateKeyset.appKey, "dest-app-dprv")

    val result = service.sweepPsbtWithTweaks(originalPsbt, sourcePrivateKeyset, destPrivateKeyset)

    result.shouldBeOk { tweaked ->
      tweaked.base64 shouldBe "sweep-psbt-with-tweaks"
      tweaked.id shouldBe originalPsbt.id
    }
  }

  test("sweepPsbtWithTweaks fails when destination app private key is missing") {
    val sourcePrivateKeyset = createPrivateKeyset("source-private", "source")
    val destPrivateKeyset = createPrivateKeyset("dest-private", "dest")

    val result = service.sweepPsbtWithTweaks(originalPsbt, sourcePrivateKeyset, destPrivateKeyset)

    result.shouldBeErrOfType<ChaincodeDelegationError.AppSpendingPrivateKeyMissing>()
  }

  test("sweepPsbtWithTweaks fails when destination server root xpub is missing") {
    val sourcePrivateKeyset = createPrivateKeyset("source-private", "source")
    val destLegacyKeyset = createPrivateKeyset("dest-legacy", "dest", hasRootXpub = false)

    storeAppPrivateKey(destLegacyKeyset.appKey)

    val result = service.sweepPsbtWithTweaks(originalPsbt, sourcePrivateKeyset, destLegacyKeyset)

    result.shouldBeErrOfType<ChaincodeDelegationError.ServerRootXpubMissing>()
  }

  test("migrationSweepPsbtWithTweaks applies tweaks for legacy-to-private sweep") {
    val destPrivateKeyset = createPrivateKeyset("dest-private", "dest")

    storeAppPrivateKey(destPrivateKeyset.appKey, "dest-app-dprv")

    val result = service.migrationSweepPsbtWithTweaks(originalPsbt, destPrivateKeyset)

    result.shouldBeOk { tweaked ->
      tweaked.base64 shouldBe "migration-sweep-psbt-with-tweaks"
      tweaked.id shouldBe originalPsbt.id
    }
  }

  test("migrationSweepPsbtWithTweaks fails when destination app private key is missing") {
    val destPrivateKeyset = createPrivateKeyset("dest-private", "dest")

    val result = service.migrationSweepPsbtWithTweaks(originalPsbt, destPrivateKeyset)

    result.shouldBeErrOfType<ChaincodeDelegationError.AppSpendingPrivateKeyMissing>()
  }

  test("migrationSweepPsbtWithTweaks fails when destination server root xpub is missing") {
    val destLegacyKeyset = createPrivateKeyset("dest-legacy", "dest", hasRootXpub = false)

    storeAppPrivateKey(destLegacyKeyset.appKey)

    val result = service.migrationSweepPsbtWithTweaks(originalPsbt, destLegacyKeyset)

    result.shouldBeErrOfType<ChaincodeDelegationError.ServerRootXpubMissing>()
  }

  test("sweepPsbtWithTweaks fails when tweak computation fails") {
    val sourcePrivateKeyset = createPrivateKeyset("source-private", "source")
    val destPrivateKeyset = createPrivateKeyset("dest-private", "dest")

    storeAppPrivateKey(destPrivateKeyset.appKey)
    psbtUtils.sweepPsbtWithTweaksResult = ChaincodeDelegationResult.Err(
      ChaincodeDelegationError.TweakComputation(
        cause = Exception("Tweak failed"),
        message = "Failed to compute tweaks"
      )
    )

    val result = service.sweepPsbtWithTweaks(originalPsbt, sourcePrivateKeyset, destPrivateKeyset)

    result.shouldBeErrOfType<ChaincodeDelegationError.TweakComputation>()
  }
})
