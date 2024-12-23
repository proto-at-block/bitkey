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
import build.wallet.bitkey.inheritance.BeneficiaryLockedClaimFake
import build.wallet.bitkey.inheritance.InheritanceMaterial
import build.wallet.bitkey.keybox.FullAccountMock
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
  val factory = InheritanceTransactionFactoryImpl(
    bitcoinAddressService = addressService,
    bitcoinFeeRateEstimator = BitcoinFeeRateEstimatorMock(),
    descriptorBuilder = BitcoinMultiSigDescriptorBuilderMock(),
    relationshipsKeysRepository = relationshipsKeysRepository,
    inheritanceCrypto = InheritanceCryptoFake(
      inheritanceMaterial = Ok(InheritanceMaterial(emptyList()))
    ),
    spendingWalletProvider = spendingWalletProvider
  )

  test("Create full balance transaction") {
    addressService.result = Ok(someBitcoinAddress)
    spendingWallet.psbtResult = Ok(PsbtMock)
    spendingWalletProvider.walletResult = Ok(spendingWallet)

    val result = factory.createFullBalanceTransaction(
      account = FullAccountMock,
      claim = BeneficiaryLockedClaimFake
    )

    result.isOk.shouldBeTrue()
    result.getOrThrow().run {
      claim.shouldBe(BeneficiaryLockedClaimFake)
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
      claim = BeneficiaryLockedClaimFake
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
      claim = BeneficiaryLockedClaimFake
    )

    result.isErr.shouldBeTrue()
    result.error.message.shouldBe("Failed to create Wallet")
  }
})
