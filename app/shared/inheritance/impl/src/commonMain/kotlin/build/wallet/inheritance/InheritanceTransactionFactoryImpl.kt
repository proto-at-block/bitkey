package build.wallet.inheritance

import build.wallet.bitcoin.address.BitcoinAddressService
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilder
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimator
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitcoin.wallet.SpendingWalletDescriptor
import build.wallet.bitcoin.wallet.SpendingWalletProvider
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.relationships.DelegatedDecryptionKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.relationships.RelationshipsKeysRepository
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding

@BitkeyInject(AppScope::class)
class InheritanceTransactionFactoryImpl(
  private val bitcoinAddressService: BitcoinAddressService,
  private val bitcoinFeeRateEstimator: BitcoinFeeRateEstimator,
  private val descriptorBuilder: BitcoinMultiSigDescriptorBuilder,
  private val relationshipsKeysRepository: RelationshipsKeysRepository,
  private val inheritanceCrypto: InheritanceCrypto,
  private val spendingWalletProvider: SpendingWalletProvider,
) : InheritanceTransactionFactory {
  override suspend fun createFullBalanceTransaction(
    account: FullAccount,
    claim: BeneficiaryClaim.LockedClaim,
  ): Result<InheritanceTransactionDetails, Throwable> =
    coroutineBinding {
      val receiveAddress = bitcoinAddressService.generateAddress().bind()
      val delegatedDecryptionKey = relationshipsKeysRepository.getKeyWithPrivateMaterialOrCreate<DelegatedDecryptionKey>().bind()
      val inheritanceKeyset = inheritanceCrypto.decryptInheritanceMaterial(
        delegatedDecryptionKey = delegatedDecryptionKey,
        sealedDek = claim.sealedDek,
        sealedMobileKey = claim.sealedMobileKey
      ).bind()
      val inheritanceWallet = spendingWalletProvider.getWallet(
        walletDescriptor = SpendingWalletDescriptor(
          identifier = "inheritance-transaction-${claim.claimId.value}",
          networkType = account.config.bitcoinNetworkType,
          receivingDescriptor = descriptorBuilder.spendingReceivingDescriptor(
            descriptorKeyset = claim.benefactorKeyset.value,
            publicKey = inheritanceKeyset.appSpendingPublicKey.key,
            privateKey = inheritanceKeyset.appSpendingPrivateKey.key
          ),
          changeDescriptor = descriptorBuilder.spendingChangeDescriptor(
            descriptorKeyset = claim.benefactorKeyset.value,
            publicKey = inheritanceKeyset.appSpendingPublicKey.key,
            privateKey = inheritanceKeyset.appSpendingPrivateKey.key
          )
        )
      ).bind()
      val feeRate = bitcoinFeeRateEstimator.estimatedFeeRateForTransaction(
        networkType = account.config.bitcoinNetworkType,
        estimatedTransactionPriority = EstimatedTransactionPriority.inheritancePriority()
      )
      inheritanceWallet.sync().bind()

      val psbt = inheritanceWallet.createSignedPsbt(
        SpendingWallet.PsbtConstructionMethod.Regular(
          recipientAddress = receiveAddress,
          amount = BitcoinTransactionSendAmount.SendAll,
          feePolicy = FeePolicy.Rate(feeRate)
        )
      ).bind()

      InheritanceTransactionDetails(
        claim = claim,
        inheritanceWallet = inheritanceWallet,
        recipientAddress = receiveAddress,
        psbt = psbt
      )
    }
}