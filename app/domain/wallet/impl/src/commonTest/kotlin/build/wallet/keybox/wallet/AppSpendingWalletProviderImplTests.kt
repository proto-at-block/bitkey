package build.wallet.keybox.wallet

import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilderMock
import build.wallet.bitcoin.descriptor.FrostWalletDescriptorFactoryFake
import build.wallet.bitcoin.wallet.SpendingWalletFake
import build.wallet.bitcoin.wallet.SpendingWalletProviderMock
import build.wallet.bitcoin.wallet.WalletV2ProviderMock
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.bitkey.spending.AppSpendingPrivateKeyMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.Bdk2FeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AppSpendingWalletProviderImplTests : FunSpec({

  val spendingWalletProvider = SpendingWalletProviderMock()
  val walletV2Provider = WalletV2ProviderMock()
  val featureFlagDao = FeatureFlagDaoFake()
  val bdk2FeatureFlag = Bdk2FeatureFlag(featureFlagDao)
  val appPrivateKeyDao = AppPrivateKeyDaoFake()
  val descriptorBuilder = BitcoinMultiSigDescriptorBuilderMock()
  val frostWalletDescriptorFactory = FrostWalletDescriptorFactoryFake()

  val v1Wallet = SpendingWalletFake(identifier = "v1-wallet")
  val v2Wallet = SpendingWalletFake(identifier = "v2-wallet")

  val provider = AppSpendingWalletProviderImpl(
    spendingWalletProvider = spendingWalletProvider,
    walletV2Provider = walletV2Provider,
    bdk2FeatureFlag = bdk2FeatureFlag,
    appPrivateKeyDao = appPrivateKeyDao,
    descriptorBuilder = descriptorBuilder,
    frostWalletDescriptorFactory = frostWalletDescriptorFactory
  )

  beforeTest {
    featureFlagDao.reset()
    appPrivateKeyDao.reset()
    walletV2Provider.reset()
    spendingWalletProvider.walletResult = Ok(v1Wallet)
    walletV2Provider.walletResult = Ok(v2Wallet)

    // Set up the app private key for the keyset
    appPrivateKeyDao.storeAppSpendingKeyPair(
      AppSpendingKeypair(
        publicKey = SpendingKeysetMock.appKey,
        privateKey = AppSpendingPrivateKeyMock
      )
    )
  }

  test("uses legacy wallet provider when feature flag is disabled") {
    bdk2FeatureFlag.setFlagValue(false)

    val result = provider.getSpendingWallet(SpendingKeysetMock)

    result.shouldBeOk()
    result.value.identifier.shouldBe("v1-wallet")
  }

  test("uses V2 wallet provider when feature flag is enabled") {
    bdk2FeatureFlag.setFlagValue(true)

    val result = provider.getSpendingWallet(SpendingKeysetMock)

    result.shouldBeOk()
    result.value.identifier.shouldBe("v2-wallet")
  }
})
