package build.wallet.inheritance

import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitcoin.address.BitcoinAddressServiceFake
import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilderMock
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimatorMock
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitcoin.wallet.SpendingWalletFake
import build.wallet.bitcoin.wallet.SpendingWalletProviderMock
import build.wallet.bitkey.inheritance.*
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.InheritanceUseEncryptedDescriptorFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.relationships.RelationshipsCryptoFake
import build.wallet.relationships.RelationshipsKeysDaoFake
import build.wallet.relationships.RelationshipsKeysRepository
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

class InheritanceTransactionFactoryTests : FunSpec({
  val addressService = BitcoinAddressServiceFake()
  val appPrivateKeyDao = AppPrivateKeyDaoFake()
  val relationshipsCrypto = RelationshipsCryptoFake(appPrivateKeyDao = appPrivateKeyDao)
  val relationshipsKeysRepository = RelationshipsKeysRepository(relationshipsCrypto, RelationshipsKeysDaoFake())
  val spendingWallet = object : SpendingWallet by SpendingWalletFake() {
    var psbtResult: Result<Psbt, Throwable> = Ok(PsbtMock)

    override suspend fun createSignedPsbt(
      constructionType: SpendingWallet.PsbtConstructionMethod,
    ): Result<Psbt, Throwable> {
      return psbtResult
    }
  }
  val spendingWalletProvider = SpendingWalletProviderMock()
  val inheritanceUseEncryptedDescriptorFeatureFlag = InheritanceUseEncryptedDescriptorFeatureFlag(featureFlagDao = FeatureFlagDaoFake())
  val factory = InheritanceTransactionFactoryImpl(
    bitcoinAddressService = addressService,
    bitcoinFeeRateEstimator = BitcoinFeeRateEstimatorMock(),
    descriptorBuilder = BitcoinMultiSigDescriptorBuilderMock(),
    relationshipsKeysRepository = relationshipsKeysRepository,
    inheritanceCrypto = InheritanceCryptoFake(
      inheritanceMaterial = Ok(InheritanceMaterial(emptyList()))
    ),
    spendingWalletProvider = spendingWalletProvider,
    inheritanceUseEncryptedDescriptorFeatureFlag = inheritanceUseEncryptedDescriptorFeatureFlag
  )

  beforeTest {
    inheritanceUseEncryptedDescriptorFeatureFlag.reset()
  }

  test("Create full balance transaction w/ no sealed descriptor & ff off") {
    addressService.result = Ok(someBitcoinAddress)
    spendingWallet.psbtResult = Ok(PsbtMock)
    spendingWalletProvider.walletResult = Ok(spendingWallet)

    val result = factory.createFullBalanceTransaction(
      account = FullAccountMock,
      claim = BeneficiaryLockedClaimNoSealedDescriptorFake
    )

    result.isOk.shouldBeTrue()
    result.getOrThrow().run {
      claim.shouldBe(BeneficiaryLockedClaimNoSealedDescriptorFake)
      psbt.shouldBe(PsbtMock)
      inheritanceWallet.shouldBe(spendingWallet)
      recipientAddress.shouldBe(someBitcoinAddress)
    }
  }

  test("Create full balance transaction w/ no sealed descriptor & ff on") {
    inheritanceUseEncryptedDescriptorFeatureFlag.setFlagValue(true)

    addressService.result = Ok(someBitcoinAddress)
    spendingWallet.psbtResult = Ok(PsbtMock)
    spendingWalletProvider.walletResult = Ok(spendingWallet)

    val result = factory.createFullBalanceTransaction(
      account = FullAccountMock,
      claim = BeneficiaryLockedClaimNoSealedDescriptorFake
    )

    result.isOk.shouldBeTrue()
    result.getOrThrow().run {
      claim.shouldBe(BeneficiaryLockedClaimNoSealedDescriptorFake)
      psbt.shouldBe(PsbtMock)
      inheritanceWallet.shouldBe(spendingWallet)
      recipientAddress.shouldBe(someBitcoinAddress)
    }
  }

  test("Create full balance transaction w/ both descriptors & ff off") {
    addressService.result = Ok(someBitcoinAddress)
    spendingWallet.psbtResult = Ok(PsbtMock)
    spendingWalletProvider.walletResult = Ok(spendingWallet)

    val result = factory.createFullBalanceTransaction(
      account = FullAccountMock,
      claim = BeneficiaryLockedClaimBothDescriptorsFake
    )

    result.isOk.shouldBeTrue()
    result.getOrThrow().run {
      claim.shouldBe(BeneficiaryLockedClaimBothDescriptorsFake)
      psbt.shouldBe(PsbtMock)
      inheritanceWallet.shouldBe(spendingWallet)
      recipientAddress.shouldBe(someBitcoinAddress)
    }
  }

  test("Create full balance transaction w/ both descriptors & ff on") {
    inheritanceUseEncryptedDescriptorFeatureFlag.setFlagValue(true)

    addressService.result = Ok(someBitcoinAddress)
    spendingWallet.psbtResult = Ok(PsbtMock)
    spendingWalletProvider.walletResult = Ok(spendingWallet)

    val result = factory.createFullBalanceTransaction(
      account = FullAccountMock,
      claim = BeneficiaryLockedClaimBothDescriptorsFake
    )

    result.isOk.shouldBeTrue()
    result.getOrThrow().run {
      claim.shouldBe(BeneficiaryLockedClaimBothDescriptorsFake)
      psbt.shouldBe(PsbtMock)
      inheritanceWallet.shouldBe(spendingWallet)
      recipientAddress.shouldBe(someBitcoinAddress)
    }
  }

  test("Create full balance transaction w/ no plaintext descriptor & ff on") {
    inheritanceUseEncryptedDescriptorFeatureFlag.setFlagValue(true)

    addressService.result = Ok(someBitcoinAddress)
    spendingWallet.psbtResult = Ok(PsbtMock)
    spendingWalletProvider.walletResult = Ok(spendingWallet)

    val result = factory.createFullBalanceTransaction(
      account = FullAccountMock,
      claim = BeneficiaryLockedClaimNoPlaintextDescriptorFake
    )

    result.isOk.shouldBeTrue()
    result.getOrThrow().run {
      claim.shouldBe(BeneficiaryLockedClaimNoPlaintextDescriptorFake)
      psbt.shouldBe(PsbtMock)
      inheritanceWallet.shouldBe(spendingWallet)
      recipientAddress.shouldBe(someBitcoinAddress)
    }
  }

  test("Failed Psbt fails transaction") {
    addressService.result = Ok(someBitcoinAddress)
    spendingWallet.psbtResult = Err(Throwable("Failed to create Psbt"))
    spendingWalletProvider.walletResult = Ok(spendingWallet)

    val result = factory.createFullBalanceTransaction(
      account = FullAccountMock,
      claim = BeneficiaryLockedClaimBothDescriptorsFake
    )

    result.isErr.shouldBeTrue()
    result.error.message.shouldBe("Failed to create Psbt")
  }

  test("Failed Wallet Creation fails transaction") {
    addressService.result = Ok(someBitcoinAddress)
    spendingWallet.psbtResult = Ok(PsbtMock)
    spendingWalletProvider.walletResult = Err(Throwable("Failed to create Wallet"))

    val result = factory.createFullBalanceTransaction(
      account = FullAccountMock,
      claim = BeneficiaryLockedClaimBothDescriptorsFake
    )

    result.isErr.shouldBeTrue()
    result.error.message.shouldBe("Failed to create Wallet")
  }
})
