package build.wallet.inheritance

import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitcoin.address.BitcoinAddressServiceFake
import build.wallet.bitcoin.address.bitcoinAddressP2WPKH
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
import build.wallet.bitkey.keybox.withNewSpendingKeyset
import build.wallet.bitkey.spending.PrivateSpendingKeysetMock
import build.wallet.chaincode.delegation.ChaincodeDelegationTweakServiceFake
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.InheritanceUseEncryptedDescriptorFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.recovery.DescriptorBackupServiceFake
import build.wallet.relationships.RelationshipsCryptoFake
import build.wallet.relationships.RelationshipsKeysDaoFake
import build.wallet.relationships.RelationshipsKeysRepository
import build.wallet.testing.shouldBeOk
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
  val relationshipsKeysRepository =
    RelationshipsKeysRepository(relationshipsCrypto, RelationshipsKeysDaoFake())
  val spendingWallet = object : SpendingWallet by SpendingWalletFake() {
    var psbtResult: Result<Psbt, Throwable> = Ok(PsbtMock)

    override suspend fun createSignedPsbt(
      constructionType: SpendingWallet.PsbtConstructionMethod,
    ): Result<Psbt, Throwable> {
      return psbtResult
    }
  }
  val spendingWalletProvider = SpendingWalletProviderMock()
  val chaincodeDelegationTweakService = ChaincodeDelegationTweakServiceFake()
  val descriptorBackupService = DescriptorBackupServiceFake()
  val inheritanceUseEncryptedDescriptorFeatureFlag =
    InheritanceUseEncryptedDescriptorFeatureFlag(featureFlagDao = FeatureFlagDaoFake())
  val inheritanceCryptoFake = InheritanceCryptoFake(
    inheritanceMaterial = Ok(InheritanceMaterial(emptyList()))
  )
  val factory = InheritanceTransactionFactoryImpl(
    bitcoinAddressService = addressService,
    bitcoinFeeRateEstimator = BitcoinFeeRateEstimatorMock(),
    descriptorBuilder = BitcoinMultiSigDescriptorBuilderMock(),
    relationshipsKeysRepository = relationshipsKeysRepository,
    inheritanceCrypto = inheritanceCryptoFake,
    spendingWalletProvider = spendingWalletProvider,
    chaincodeDelegationTweakService = chaincodeDelegationTweakService,
    descriptorBackupService = descriptorBackupService,
    inheritanceUseEncryptedDescriptorFeatureFlag = inheritanceUseEncryptedDescriptorFeatureFlag
  )

  beforeTest {
    inheritanceUseEncryptedDescriptorFeatureFlag.reset()
    chaincodeDelegationTweakService.reset()
    descriptorBackupService.reset()

    inheritanceCryptoFake.inheritanceMaterialPackageResult = Ok(
      DecryptInheritanceMaterialPackageOutput(
        inheritanceKeyset = InheritanceKeysetFake,
        descriptor = "fake-descriptor",
        serverRootXpub = "fake-sealed-server-root-xpub"
      )
    )
  }

  test("Create full balance transaction w/ no sealed descriptor & ff off") {
    addressService.result = Ok(someBitcoinAddress)
    spendingWallet.psbtResult = Ok(PsbtMock)
    spendingWalletProvider.walletResult = Ok(spendingWallet)

    inheritanceCryptoFake.inheritanceMaterialPackageResult = Ok(
      DecryptInheritanceMaterialPackageOutput(
        inheritanceKeyset = InheritanceKeysetFake,
        descriptor = null,
        serverRootXpub = null
      )
    )

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

    inheritanceCryptoFake.inheritanceMaterialPackageResult = Ok(
      DecryptInheritanceMaterialPackageOutput(
        inheritanceKeyset = InheritanceKeysetFake,
        descriptor = null,
        serverRootXpub = null
      )
    )

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

    inheritanceCryptoFake.inheritanceMaterialPackageResult = Ok(
      DecryptInheritanceMaterialPackageOutput(
        inheritanceKeyset = InheritanceKeysetFake,
        descriptor = null,
        serverRootXpub = null
      )
    )

    result.isOk.shouldBeTrue()
    result.getOrThrow().run {
      claim.shouldBe(BeneficiaryLockedClaimBothDescriptorsFake)
      psbt.shouldBe(PsbtMock.copy(base64 = "delegated-base-64"))
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
      psbt.shouldBe(PsbtMock.copy(base64 = "delegated-base-64"))
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

  test("applies sweep tweaks when inheriting from private wallet to private wallet") {
    inheritanceUseEncryptedDescriptorFeatureFlag.setFlagValue(true)

    addressService.result = Ok(someBitcoinAddress)
    spendingWallet.psbtResult = Ok(PsbtMock)
    spendingWalletProvider.walletResult = Ok(spendingWallet)

    inheritanceCryptoFake.inheritanceMaterialPackageResult = Ok(
      DecryptInheritanceMaterialPackageOutput(
        inheritanceKeyset = InheritanceKeysetFake,
        descriptor = "fake-descriptor",
        serverRootXpub = "fake-sealed-server-root-xpub"
      )
    )

    val privateAccount = FullAccountMock.copy(
      keybox = FullAccountMock.keybox.withNewSpendingKeyset(PrivateSpendingKeysetMock)
    )

    val result = factory.createFullBalanceTransaction(
      account = privateAccount,
      claim = BeneficiaryLockedClaimBothDescriptorsFake
    )

    result.isOk.shouldBeTrue()
    result.getOrThrow().psbt.base64.shouldBe("sweep-tweaked-psbt")
  }

  test("applies migration tweaks when inheriting from legacy wallet to private wallet") {
    inheritanceUseEncryptedDescriptorFeatureFlag.setFlagValue(true)

    addressService.result = Ok(someBitcoinAddress)
    spendingWallet.psbtResult = Ok(PsbtMock)
    spendingWalletProvider.walletResult = Ok(spendingWallet)

    inheritanceCryptoFake.inheritanceMaterialPackageResult = Ok(
      DecryptInheritanceMaterialPackageOutput(
        inheritanceKeyset = InheritanceKeysetFake,
        descriptor = null,
        serverRootXpub = null
      )
    )

    val privateAccount = FullAccountMock.copy(
      keybox = FullAccountMock.keybox.withNewSpendingKeyset(PrivateSpendingKeysetMock)
    )

    val result = factory.createFullBalanceTransaction(
      account = privateAccount,
      claim = BeneficiaryLockedClaimNoSealedDescriptorFake
    )

    result.isOk.shouldBeTrue()
    result.getOrThrow().psbt.base64.shouldBe("migration-tweaked-psbt")
  }

  test("applies standard tweaks when inheriting from private wallet to legacy wallet") {
    inheritanceUseEncryptedDescriptorFeatureFlag.setFlagValue(true)

    addressService.result = Ok(someBitcoinAddress)
    spendingWallet.psbtResult = Ok(PsbtMock)
    spendingWalletProvider.walletResult = Ok(spendingWallet)

    inheritanceCryptoFake.inheritanceMaterialPackageResult = Ok(
      DecryptInheritanceMaterialPackageOutput(
        inheritanceKeyset = InheritanceKeysetFake,
        descriptor = "fake-descriptor",
        serverRootXpub = "fake-sealed-server-root-xpub"
      )
    )

    val result = factory.createFullBalanceTransaction(
      account = FullAccountMock,
      claim = BeneficiaryLockedClaimBothDescriptorsFake
    )

    result.isOk.shouldBeTrue()
    result.getOrThrow().psbt.base64.shouldBe("delegated-base-64")
  }

  test("applies no tweaks when inheriting from legacy wallet to legacy wallet") {
    inheritanceUseEncryptedDescriptorFeatureFlag.setFlagValue(true)

    addressService.result = Ok(someBitcoinAddress)
    spendingWallet.psbtResult = Ok(PsbtMock)
    spendingWalletProvider.walletResult = Ok(spendingWallet)

    inheritanceCryptoFake.inheritanceMaterialPackageResult = Ok(
      DecryptInheritanceMaterialPackageOutput(
        inheritanceKeyset = InheritanceKeysetFake,
        descriptor = null,
        serverRootXpub = null
      )
    )

    val result = factory.createFullBalanceTransaction(
      account = FullAccountMock,
      claim = BeneficiaryLockedClaimNoSealedDescriptorFake
    )

    result.isOk.shouldBeTrue()
    result.getOrThrow().psbt.shouldBe(PsbtMock)
  }

  test("uses Peek(0u) address index for private wallet") {
    addressService.result = Ok(someBitcoinAddress)
    spendingWallet.psbtResult = Ok(PsbtMock)
    spendingWalletProvider.walletResult = Ok(spendingWallet)

    val privateAccount = FullAccountMock.copy(
      keybox = FullAccountMock.keybox.withNewSpendingKeyset(PrivateSpendingKeysetMock)
    )

    val result = factory.createFullBalanceTransaction(
      account = privateAccount,
      claim = BeneficiaryLockedClaimBothDescriptorsFake
    )
    result.shouldBeOk().recipientAddress.shouldBe(someBitcoinAddress)
  }

  test("uses New address index for non-private wallet") {
    addressService.result = Ok(bitcoinAddressP2WPKH)
    spendingWallet.psbtResult = Ok(PsbtMock)
    spendingWalletProvider.walletResult = Ok(spendingWallet)

    val result = factory.createFullBalanceTransaction(
      account = FullAccountMock,
      claim = BeneficiaryLockedClaimBothDescriptorsFake
    )
    result.shouldBeOk().recipientAddress.shouldBe(bitcoinAddressP2WPKH)
  }
})
