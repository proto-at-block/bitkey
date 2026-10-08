package build.wallet.bitcoin.wallet

import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitcoin.descriptor.AppKeyWalletDescriptorBuilderFake
import build.wallet.bitkey.spending.AppSpendingPrivateKeyMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.Bdk2FeatureFlag
import build.wallet.feature.flags.setBdk2Enabled
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

class AppKeyWalletProviderImplTests : FunSpec({
  val spendingWalletProvider = SpendingWalletProviderMock()
  val spendingWalletV2Provider = SpendingWalletV2ProviderMock()
  val featureFlagDao = FeatureFlagDaoFake()
  val bdk2FeatureFlag = Bdk2FeatureFlag(featureFlagDao)
  val descriptorBuilder = AppKeyWalletDescriptorBuilderFake()

  val v1Wallet = SpendingWalletFake(identifier = "v1-app-key-wallet")
  val v2Wallet = SpendingWalletFake(identifier = "v2-app-key-wallet")

  val appPrivateKeyDao = AppPrivateKeyDaoFake()

  val provider = AppKeyWalletProviderImpl(
    spendingWalletProvider = spendingWalletProvider,
    spendingWalletV2Provider = spendingWalletV2Provider,
    bdk2FeatureFlag = bdk2FeatureFlag,
    descriptorBuilder = descriptorBuilder,
    appPrivateKeyDao = appPrivateKeyDao
  )

  beforeTest {
    featureFlagDao.reset()
    bdk2FeatureFlag.reset()
    bdk2FeatureFlag.initializeFromDao()
    spendingWalletV2Provider.reset()
    appPrivateKeyDao.reset()
    appPrivateKeyDao.appSpendingKeys[SpendingKeysetMock.appKey] = AppSpendingPrivateKeyMock
    spendingWalletProvider.walletResult = Ok(v1Wallet)
    spendingWalletV2Provider.walletResult = Ok(v2Wallet)
  }

  test("uses legacy wallet provider when BDK2 flag is disabled") {
    bdk2FeatureFlag.setBdk2Enabled(false)

    provider.getAppKeyWallet(SpendingKeysetMock).shouldBeOk().identifier.shouldBe("v1-app-key-wallet")
  }

  test("uses V2 wallet provider when BDK2 flag is enabled") {
    bdk2FeatureFlag.setBdk2Enabled(true)

    provider.getAppKeyWallet(SpendingKeysetMock).shouldBeOk().identifier.shouldBe("v2-app-key-wallet")
  }

  test("builds a single-sig spending descriptor with distinct receiving and change chains") {
    bdk2FeatureFlag.setBdk2Enabled(true)

    provider.getAppKeyWallet(SpendingKeysetMock).shouldBeOk()

    val descriptor = spendingWalletV2Provider.requestedDescriptors.value.single()
    descriptor.identifier.shouldBe("app-key-wallet-${SpendingKeysetMock.localId}")
    descriptor.networkType.shouldBe(SpendingKeysetMock.networkType)
    descriptor.receivingDescriptor.raw.shouldBe("wpkh(${AppSpendingPrivateKeyMock.key.xprv}/0/*)")
    descriptor.changeDescriptor.raw.shouldBe("wpkh(${AppSpendingPrivateKeyMock.key.xprv}/1/*)")
    // Rescue wallets must be app-key-only; a multisig descriptor could not be signed locally.
    descriptor.receivingDescriptor.raw.shouldNotContain("sortedmulti")
  }

  test("propagates the requested network so callers cannot sweep on the wrong chain") {
    bdk2FeatureFlag.setBdk2Enabled(true)

    provider.getAppKeyWallet(SpendingKeysetMock).shouldBeOk()

    spendingWalletV2Provider.requestedDescriptors.value.single().networkType.shouldBe(SpendingKeysetMock.networkType)
  }

  test("wraps underlying wallet creation failure") {
    bdk2FeatureFlag.setBdk2Enabled(true)
    val cause = RuntimeException("bdk failure")
    spendingWalletV2Provider.walletResult = Err(cause)

    provider.getAppKeyWallet(SpendingKeysetMock).shouldBeErrOfType<AppKeyWalletError.WalletCreationFailed>()
      .cause.shouldBe(cause)
  }
})
