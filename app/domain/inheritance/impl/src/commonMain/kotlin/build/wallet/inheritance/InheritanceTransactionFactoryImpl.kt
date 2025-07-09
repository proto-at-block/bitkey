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
import build.wallet.feature.flags.InheritanceUseEncryptedDescriptorFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.logInfo
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
  private val inheritanceUseEncryptedDescriptorFeatureFlag:
    InheritanceUseEncryptedDescriptorFeatureFlag,
) : InheritanceTransactionFactory {
  override suspend fun createFullBalanceTransaction(
    account: FullAccount,
    claim: BeneficiaryClaim.LockedClaim,
  ): Result<InheritanceTransactionDetails, Throwable> =
    coroutineBinding {
      val receiveAddress = bitcoinAddressService.generateAddress().bind()
      val delegatedDecryptionKey =
        relationshipsKeysRepository.getKeyWithPrivateMaterialOrCreate<DelegatedDecryptionKey>()
          .bind()
      val benefactorInheritancePackage = inheritanceCrypto.decryptInheritanceMaterialPackage(
        delegatedDecryptionKey = delegatedDecryptionKey,
        sealedDek = claim.sealedDek,
        sealedAppKey = claim.sealedMobileKey,
        sealedDescriptor = claim.sealedDescriptor
      ).bind()
      val descriptorKeyset = descriptorKeysetToUse(benefactorInheritancePackage, claim)
      val inheritanceWallet = spendingWalletProvider.getWallet(
        walletDescriptor = SpendingWalletDescriptor(
          identifier = "inheritance-transaction-${claim.claimId.value}",
          networkType = account.config.bitcoinNetworkType,
          receivingDescriptor = descriptorBuilder.spendingReceivingDescriptor(
            descriptorKeyset = descriptorKeyset,
            publicKey = benefactorInheritancePackage.inheritanceKeyset.appSpendingPublicKey.key,
            privateKey = benefactorInheritancePackage.inheritanceKeyset.appSpendingPrivateKey.key
          ),
          changeDescriptor = descriptorBuilder.spendingChangeDescriptor(
            descriptorKeyset = descriptorKeyset,
            publicKey = benefactorInheritancePackage.inheritanceKeyset.appSpendingPublicKey.key,
            privateKey = benefactorInheritancePackage.inheritanceKeyset.appSpendingPrivateKey.key
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

  private fun descriptorKeysetToUse(
    decryptInheritanceMaterialPackage: DecryptInheritanceMaterialPackageOutput,
    claim: BeneficiaryClaim.LockedClaim,
  ): String {
    // If the feature flag is disabled, fall back to using the benefactor keyset provided by the server.
    if (!inheritanceUseEncryptedDescriptorFeatureFlag.isEnabled()) {
      return claim.benefactorKeyset!!.value
    }

    // Otherwise, we try to use the decrypted package descriptor if it is available.
    return if (decryptInheritanceMaterialPackage.descriptor != null) {
      logInfo {
        "[Privacy] inheritance material package has non-null descriptor, using it to create transaction."
      }
      decryptInheritanceMaterialPackage.descriptor!!
    } else {
      logInfo {
        "[Privacy] inheritance material package has null descriptor, falling back to benefactor keyset."
      }
      claim.benefactorKeyset!!.value
    }
  }
}
