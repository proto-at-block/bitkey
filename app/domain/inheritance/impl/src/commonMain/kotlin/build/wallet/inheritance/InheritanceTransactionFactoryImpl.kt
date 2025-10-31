package build.wallet.inheritance

import bitkey.recovery.DescriptorBackupService
import build.wallet.bdk.bindings.BdkAddressIndex.New
import build.wallet.bdk.bindings.BdkAddressIndex.Peek
import build.wallet.bitcoin.address.BitcoinAddressService
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilder
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimator
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.wallet.SpendingWallet.PsbtConstructionMethod.Regular
import build.wallet.bitcoin.wallet.SpendingWalletDescriptor
import build.wallet.bitcoin.wallet.SpendingWalletProvider
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.relationships.DelegatedDecryptionKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.chaincode.delegation.ChaincodeDelegationTweakService
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
  private val chaincodeDelegationTweakService: ChaincodeDelegationTweakService,
  private val descriptorBackupService: DescriptorBackupService,
  private val inheritanceUseEncryptedDescriptorFeatureFlag:
    InheritanceUseEncryptedDescriptorFeatureFlag,
) : InheritanceTransactionFactory {
  override suspend fun createFullBalanceTransaction(
    account: FullAccount,
    claim: BeneficiaryClaim.LockedClaim,
  ): Result<InheritanceTransactionDetails, Throwable> =
    coroutineBinding {
      val receiveAddressIndex = if (account.keybox.isPrivateWallet) Peek(0u) else New
      val receiveAddress = bitcoinAddressService.generateAddress(receiveAddressIndex).bind()
      val delegatedDecryptionKey =
        relationshipsKeysRepository.getKeyWithPrivateMaterialOrCreate<DelegatedDecryptionKey>()
          .bind()
      val benefactorInheritancePackage = inheritanceCrypto.decryptInheritanceMaterialPackage(
        delegatedDecryptionKey = delegatedDecryptionKey,
        sealedDek = claim.sealedDek,
        sealedAppKey = claim.sealedMobileKey,
        sealedDescriptor = claim.sealedDescriptor,
        sealedServerRootXpub = claim.sealedServerRootXpub
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
        constructionType = Regular(
          recipientAddress = receiveAddress,
          amount = BitcoinTransactionSendAmount.SendAll,
          feePolicy = FeePolicy.Rate(feeRate)
        )
      ).bind()

      // Apply tweaks depending on the source and destination keysets
      val tweakedPsbt = applyTweaksIfNeeded(
        psbt = psbt,
        benefactorInheritancePackage = benefactorInheritancePackage,
        destinationKeyset = account.keybox.activeSpendingKeyset
      ).bind()

      InheritanceTransactionDetails(
        claim = claim,
        inheritanceWallet = inheritanceWallet,
        recipientAddress = receiveAddress,
        psbt = tweakedPsbt
      )
    }

  private suspend fun applyTweaksIfNeeded(
    psbt: Psbt,
    benefactorInheritancePackage: DecryptInheritanceMaterialPackageOutput,
    destinationKeyset: SpendingKeyset,
  ): Result<Psbt, Throwable> {
    return coroutineBinding {
      val isSourcePrivate = benefactorInheritancePackage.serverRootXpub != null
      val sourceSpendingKeyset = if (isSourcePrivate) {
        descriptorBackupService.parseDescriptorKeys(
          descriptorString = benefactorInheritancePackage.descriptor!!,
          privateWalletRootXpub = benefactorInheritancePackage.serverRootXpub,
          keysetId = "it-doesnt-matter", // we don't have keyset-id, but it isn't used when applying tweaks
          networkType = destinationKeyset.networkType
        ).bind()
      } else {
        null
      }
      val isDestinationPrivate = destinationKeyset.isPrivateWallet

      when {
        isSourcePrivate && isDestinationPrivate -> {
          chaincodeDelegationTweakService.sweepPsbtWithTweaks(
            psbt = psbt,
            sourceKeyset = sourceSpendingKeyset!!,
            destinationKeyset = destinationKeyset
          ).bind()
        }
        isSourcePrivate && !isDestinationPrivate -> {
          chaincodeDelegationTweakService
            .psbtWithTweaks(
              psbt = psbt,
              appSpendingPrivateKey = benefactorInheritancePackage.inheritanceKeyset.appSpendingPrivateKey.key,
              spendingKeyset = sourceSpendingKeyset!!
            )
            .bind()
        }
        !isSourcePrivate && isDestinationPrivate ->
          chaincodeDelegationTweakService
            .migrationSweepPsbtWithTweaks(psbt = psbt, destinationKeyset = destinationKeyset)
            .bind()
        else -> psbt
      }
    }
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
