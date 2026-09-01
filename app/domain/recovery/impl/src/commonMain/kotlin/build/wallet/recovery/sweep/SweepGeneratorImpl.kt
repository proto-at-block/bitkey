package build.wallet.recovery.sweep

import bitkey.recovery.DescriptorBackupService
import build.wallet.bdk.bindings.BdkError
import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimator
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.wallet.WatchingWallet
import build.wallet.bitkey.f8e.isPrivateWallet
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.chaincode.delegation.ChaincodeDelegationTweakService
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.recovery.LegacyRemoteKeyset
import build.wallet.f8e.recovery.ListKeysetsF8eClient
import build.wallet.f8e.recovery.toSpendingKeysets
import build.wallet.feature.flags.Bdk2FeatureFlag
import build.wallet.feature.flags.SweepKeysetReconciliationFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.keybox.wallet.AppSpendingWalletProvider
import build.wallet.keybox.wallet.KeysetWalletProvider
import build.wallet.logging.logFailure
import build.wallet.logging.logInfo
import build.wallet.logging.logWarn
import build.wallet.notifications.RegisterWatchAddressContext
import build.wallet.notifications.RegisterWatchAddressProcessor
import build.wallet.platform.random.UuidGenerator
import build.wallet.queueprocessor.process
import build.wallet.recovery.sweep.SweepGenerator.SweepGeneratorError
import build.wallet.recovery.sweep.SweepGenerator.SweepGeneratorError.*
import build.wallet.recovery.sweep.SweepSignaturePlan.AppAndHardware
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.CoroutineBindingScope
import com.github.michaelbull.result.coroutines.coroutineBinding

@BitkeyInject(AppScope::class)
class SweepGeneratorImpl(
  private val listKeysetsF8eClient: ListKeysetsF8eClient,
  private val bitcoinFeeRateEstimator: BitcoinFeeRateEstimator,
  private val keysetWalletProvider: KeysetWalletProvider,
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val registerWatchAddressProcessor: RegisterWatchAddressProcessor,
  private val uuidGenerator: UuidGenerator,
  private val chaincodeDelegationTweakService: ChaincodeDelegationTweakService,
  private val descriptorBackupService: DescriptorBackupService,
  private val appSpendingWalletProvider: AppSpendingWalletProvider,
  private val bdk2FeatureFlag: Bdk2FeatureFlag,
  private val sweepKeysetReconciliationFeatureFlag: SweepKeysetReconciliationFeatureFlag,
) : SweepGenerator {
  override suspend fun generateSweep(
    keybox: Keybox,
    sweepContext: SweepContext,
    context: SweepGenerationContext,
  ): Result<List<SweepPsbt>, SweepGeneratorError> =
    coroutineBinding {
      // Use local keysets if available and authoritative, otherwise fetch from F8e
      val keysets = if (keybox.canUseKeyboxKeysets) {
        logInfo { "Using local keysets for sweep generation" }
        // Local keysets are authoritative, but they can be incomplete: a recovery can write a
        // keybox that omits a previously active keyset. Any funds left on an omitted keyset are
        // then invisible to sweep detection -- no PSBT is generated, so the "funds in inactive
        // wallet" prompt never appears and the balance is silently unreachable.
        //
        // Legacy keysets carry full descriptors in the listKeysets response, so we can rebuild
        // them without a descriptor backup or hardware interaction. Backfill those. Private
        // keysets cannot be rebuilt this way (their chaincode never leaves the client), so they
        // are only reported.
        //
        // Flag-gated: this adds an f8e call to a path that runs on every foreground and every few
        // minutes, so it is limited to accounts we are actively investigating until we understand
        // fleet-wide prevalence.
        if (sweepKeysetReconciliationFeatureFlag.isEnabled()) {
          withLegacyKeysetsMissingFromLocal(keybox)
        } else {
          keybox.keysets
        }
      } else if (keybox.isPrivateWallet) {
        // This should never happen as private wallets require local keysets
        Err(PrivateWalletMissingLocalKeysets).bind()
      } else {
        logInfo { "Using remote keysets for sweep generation" }
        listKeysetsF8eClient.listKeysets(
          keybox.config.f8eEnvironment,
          keybox.fullAccountId
        )
          .mapError { FailedToListKeysets }
          .logFailure { "Error fetching keysets for an account when generating sweep." }
          .bind()
          .keysets
          .filterIsInstance<LegacyRemoteKeyset>()
          .toSpendingKeysets(uuidGenerator)
      }

      // The active hw key's dpub contains the master key fingerprint for all
      // keys generated by the hw.
      val hardwareMasterKeyFingerprint =
        keybox.activeSpendingKeyset.hardwareKey.key.origin.fingerprint

      // Find the list of keysets we can sign for and determine their signature plans
      val candidateKeysets = keysets
        .filter { it.f8eSpendingKeyset.keysetId != keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId }
        .filter { it.matchesSweepContext(sweepContext) }

      val signableKeysets = candidateKeysets
        .mapNotNull { keyset ->
          val isHwSignable = isHardwareSignable(hardwareMasterKeyFingerprint, keyset, sweepContext)
          val isAppSignable = isAppSignable(keyset).getOrElse { false }

          // Determine signature plan based keyset capabilities
          determineSignaturePlan(
            isAppSignable = isAppSignable,
            isHwSignable = isHwSignable
          ).fold(
            success = { signaturePlan -> SignableKeyset(keyset, signaturePlan) },
            failure = { error ->
              // Continue with other keysets, but do not let this pass quietly: an inactive keyset
              // we cannot sign for may still hold funds, and dropping it here is exactly how funds
              // become silently unreachable (no sweep is generated, so no "transfer funds" prompt
              // is ever shown). Logged at warn so it is visible in telemetry.
              //
              // Deliberately omits the hardware key fingerprints. They are stable per-device
              // identifiers, and this line runs on every sweep generation, so logging them would
              // put device-linkable values into telemetry on a hot path. `hwSignable` already
              // records the outcome of the fingerprint comparison, which is what triage needs.
              logWarn {
                "Cannot sign for inactive keyset ${keyset.f8eSpendingKeyset.keysetId} " +
                  "(appSignable=$isAppSignable, hwSignable=$isHwSignable): $error. " +
                  "Any funds on this keyset cannot be swept."
              }
              null
            }
          )
        }

      if (signableKeysets.size < candidateKeysets.size) {
        logWarn {
          "Sweep generation excluded ${candidateKeysets.size - signableKeysets.size} of " +
            "${candidateKeysets.size} inactive keyset(s) due to missing signing factors"
        }
      }

      val feeRate =
        bitcoinFeeRateEstimator.estimatedFeeRateForTransaction(
          networkType = keybox.config.bitcoinNetworkType,
          estimatedTransactionPriority = EstimatedTransactionPriority.sweepPriority()
        )

      val psbts = buildList<SweepPsbt> {
        signableKeysets.forEach { keyset ->
          // Generate the sweep psbt(s), failing fast on the first non-recoverable error
          buildPsbt(keyset, keybox.activeSpendingKeyset, feeRate, keybox, context).bind()
            ?.let { psbt -> add(psbt) }
        }
      }

      // Unconditional summary of how keysets narrowed at each stage. When a customer reports funds
      // stranded on an inactive wallet, this is what distinguishes "the keyset was never in the
      // local list" from "it was excluded for signing factors" from "PSBT building produced
      // nothing" -- without it, every one of those looks identical in telemetry (silence).
      logInfo {
        "Sweep keyset resolution: local=${keybox.keysets.size}, resolved=${keysets.size}, " +
          "candidates=${candidateKeysets.size}, signable=${signableKeysets.size}, " +
          "psbts=${psbts.size}"
      }

      psbts
    }.logFailure { "Error generating sweep psbts" }

  /**
   * Returns the local keysets, plus any legacy keysets f8e knows about that the local keybox is
   * missing.
   *
   * Best-effort: if the f8e call fails we fall back to the local keysets alone, so a network
   * failure never breaks sweep generation for keysets we can already sign for.
   */
  private suspend fun withLegacyKeysetsMissingFromLocal(keybox: Keybox): List<SpendingKeyset> {
    val localKeysets = keybox.keysets

    return listKeysetsF8eClient.listKeysets(
      keybox.config.f8eEnvironment,
      keybox.fullAccountId
    )
      .map { response ->
        val localKeysetIds = localKeysets.map { it.f8eSpendingKeyset.keysetId }.toSet()
        // Never backfill the server's active keyset. Downstream candidate selection only excludes
        // the *local* active keyset, so if the two disagree (the stale-backup case) a backfilled
        // server-active keyset would be treated as inactive and swept -- moving funds out of the
        // real active wallet and into the stale local one. That mismatch is the repair flow's job,
        // not sweep's.
        val missingRemoteKeysets = response.keysets.filter {
          it.keysetId !in localKeysetIds && it.keysetId != response.activeKeysetId
        }
        if (missingRemoteKeysets.isEmpty()) {
          return@map localKeysets
        }

        val (missingLegacy, missingOther) = missingRemoteKeysets
          .partition { it is LegacyRemoteKeyset }

        if (missingOther.isNotEmpty()) {
          logWarn {
            "Local keybox is missing ${missingOther.size} non-legacy server keyset(s) that cannot " +
              "be rebuilt from server data; they cannot be swept: " +
              missingOther.joinToString { it.keysetId }
          }
        }

        if (missingLegacy.isEmpty()) {
          return@map localKeysets
        }

        logWarn {
          "Local keybox is missing ${missingLegacy.size} legacy server keyset(s); including them " +
            "in sweep generation: ${missingLegacy.joinToString { it.keysetId }}"
        }
        // These keysets are not persisted, so a random localId would change every app session.
        // localId is used as the on-disk BDK wallet identifier, which would create a fresh
        // database and full rescan each cold start. Derive it from the stable f8e keyset id.
        localKeysets + missingLegacy.filterIsInstance<LegacyRemoteKeyset>()
          .toSpendingKeysets(uuidGenerator)
          .map { it.copy(localId = it.f8eSpendingKeyset.keysetId) }
      }
      .logFailure { "Could not reconcile local keysets against f8e during sweep generation" }
      .getOr(localKeysets)
  }

  /**
   * Determines the signature plan for a sweep based on context and keyset capabilities.
   */
  private fun determineSignaturePlan(
    isAppSignable: Boolean,
    isHwSignable: Boolean,
  ): Result<SweepSignaturePlan, Error> {
    return when {
      isAppSignable && isHwSignable -> Ok(AppAndHardware)
      isAppSignable -> Ok(SweepSignaturePlan.AppAndServer)
      isHwSignable -> Ok(SweepSignaturePlan.HardwareAndServer)
      else -> Err(Error("No available signing factors"))
    }
  }

  private suspend fun isAppSignable(keyset: SpendingKeyset): Result<Boolean, Throwable> {
    return appPrivateKeyDao.getAppSpendingPrivateKey(keyset.appKey).map { it != null }
  }

  private fun isHardwareSignable(
    hardwareMasterKeyFingerprint: String,
    keyset: SpendingKeyset,
    sweepContext: SweepContext,
  ): Boolean {
    val keysetFingerprint = keyset.hardwareKey.key.origin.fingerprint
    // During W3 upgrade, only the replaced device's fingerprint determines HW signability —
    // the new W3 hardware is not relevant for signing old keysets.
    return when (sweepContext) {
      is SweepContext.InactiveHardware -> keysetFingerprint == sweepContext.hardwareFingerprint
      is SweepContext.W3Upgrade -> keysetFingerprint == sweepContext.replacedHardwareFingerprint
      else -> hardwareMasterKeyFingerprint == keysetFingerprint
    }
  }

  private fun SpendingKeyset.matchesSweepContext(sweepContext: SweepContext): Boolean =
    when (sweepContext) {
      is SweepContext.InactiveHardware ->
        hardwareKey.key.origin.fingerprint == sweepContext.hardwareFingerprint
      else -> true
    }

  private suspend fun buildPsbt(
    signableKeyset: SignableKeyset,
    destinationKeyset: SpendingKeyset,
    feeRate: FeeRate,
    keybox: Keybox,
    context: SweepGenerationContext,
  ): Result<SweepPsbt?, SweepGeneratorError> =
    coroutineBinding {
      val destinationWallet =
        keysetWalletProvider
          .getWatchingWallet(destinationKeyset)
          .mapError(::ErrorCreatingWallet)
          .bind()

      if (destinationKeyset.isPrivateWallet && context is SweepGenerationContext.Real) {
        descriptorBackupService.checkBackupForPrivateKeyset(destinationKeyset.f8eSpendingKeyset.keysetId)
          .mapError(::FailedToGenerateDestinationAddress)
          .bind()
      }

      val destinationAddress = address(destinationKeyset, destinationWallet)

      // don't bind on process the address, if this fails we still want the sweep to continue
      if (context is SweepGenerationContext.Real) {
        registerWatchAddressProcessor.process(
          RegisterWatchAddressContext(
            address = destinationAddress,
            f8eSpendingKeyset = destinationKeyset.f8eSpendingKeyset,
            accountId = keybox.fullAccountId.serverId,
            f8eEnvironment = keybox.config.f8eEnvironment
          )
        ).logFailure { "Error registering address with f8e" }
      }

      destinationWallet
        .sync()
        .mapError(::ErrorCreatingWallet)
        .bind()

      val wallet =
        keysetWalletProvider
          .getWatchingWallet(signableKeyset.keyset)
          .mapError(::ErrorCreatingWallet)
          .bind()

      wallet
        .sync()
        .mapError {
          ErrorSyncingSpendingWallet(it)
        }
        .bind()

      wallet
        .createPsbt(
          recipientAddress = destinationAddress,
          amount = BitcoinTransactionSendAmount.SendAll,
          feePolicy = FeePolicy.Rate(feeRate)
        )
        .map { psbt ->
          // Apply tweaks if destination is a private wallet and we need to sign with server
          val finalPsbt =
            if (destinationKeyset.isPrivateWallet && signableKeyset.signaturePlan !is AppAndHardware) {
              applyTweaksForPrivateDestination(
                psbt = psbt,
                sourceKeyset = signableKeyset.keyset,
                destinationKeyset = destinationKeyset
              ).bind()
            } else {
              psbt
            }

          SweepPsbt(
            psbt = finalPsbt,
            signaturePlan = signableKeyset.signaturePlan,
            sourceKeyset = signableKeyset.keyset,
            destinationAddress = destinationAddress.address
          )
        }
        // Return null if the wallet doesn't have enough funds to sweep.
        .recoverIf(
          predicate = { it is BdkError.InsufficientFunds },
          transform = {
            // Expected for empty inactive keysets, which are common, so this is info rather than
            // warn to avoid drowning telemetry -- sweep generation runs on every foreground. Still
            // logged because a keyset that does hold funds lands here too when the balance cannot
            // cover the fee, and that case was previously indistinguishable from an empty keyset.
            logInfo {
              "No sweep PSBT for keyset ${signableKeyset.keyset.f8eSpendingKeyset.keysetId}: " +
                "insufficient funds at fee rate $feeRate"
            }
            null
          }
        )
        .mapError { BdkFailedToCreatePsbt(it, signableKeyset.keyset) }
        .bind()
    }

  /**
   * Applies tweaks to a PSBT when sweeping to a private wallet.
   * Uses sweepPsbtWithTweaks for private-to-private sweeps,
   * and migrationSweepPsbtWithTweaks for legacy-to-private sweeps.
   */
  private suspend fun applyTweaksForPrivateDestination(
    psbt: build.wallet.bitcoin.transactions.Psbt,
    sourceKeyset: SpendingKeyset,
    destinationKeyset: SpendingKeyset,
  ): Result<build.wallet.bitcoin.transactions.Psbt, SweepGeneratorError> {
    // Check if source is a private wallet
    return if (sourceKeyset.f8eSpendingKeyset.isPrivateWallet) {
      // Private-to-private sweep
      chaincodeDelegationTweakService
        .sweepPsbtWithTweaks(
          psbt = psbt,
          sourceKeyset = sourceKeyset,
          destinationKeyset = destinationKeyset
        )
        .mapError { FailedToTweakPsbt(it) }
    } else {
      // Legacy-to-private migration sweep
      chaincodeDelegationTweakService
        .migrationSweepPsbtWithTweaks(psbt = psbt, destinationKeyset = destinationKeyset)
        .mapError { FailedToTweakPsbt(it) }
    }
  }

  /**
   * For private keysets, we must use the 0th address. This is because the sweep psbt is generated by
   * the source wallet. As far as the source wallet is concerned, it knows nothing about the destination
   * wallet. Therefore, it cannot populate any derivation path information about the sweep output to
   * the destination wallet.
   *
   *  So, in order to be able to compute tweaks, we always use the first receive address
   *  at index 0 (i.e. /0/0 ) and ensure it's revealed to the destination wallet.
   *
   *  For non-private keysets, we can just generate a new address
   */
  private suspend fun CoroutineBindingScope<SweepGeneratorError>.address(
    destinationKeyset: SpendingKeyset,
    destinationWallet: WatchingWallet,
  ): BitcoinAddress {
    return if (destinationKeyset.isPrivateWallet) {
      if (bdk2FeatureFlag.isEnabled()) {
        appSpendingWalletProvider
          .getSpendingWallet(destinationKeyset)
          .mapError(::FailedToGenerateDestinationAddress)
          .bind()
          .revealAddress(0u)
          .mapError(::FailedToGenerateDestinationAddress)
          .bind()
      } else {
        destinationWallet
          .revealAddress(0u)
          .mapError(::FailedToGenerateDestinationAddress)
          .bind()
      }
    } else {
      if (bdk2FeatureFlag.isEnabled()) {
        appSpendingWalletProvider
          .getSpendingWallet(destinationKeyset)
          .mapError(::FailedToGenerateDestinationAddress)
          .bind()
          .getNewAddress()
          .mapError(::FailedToGenerateDestinationAddress)
          .bind()
      } else {
        destinationWallet
          .getNewAddress()
          .mapError(::FailedToGenerateDestinationAddress)
          .bind()
      }
    }
  }

  private data class SignableKeyset(
    val keyset: SpendingKeyset,
    val signaturePlan: SweepSignaturePlan,
  )
}
