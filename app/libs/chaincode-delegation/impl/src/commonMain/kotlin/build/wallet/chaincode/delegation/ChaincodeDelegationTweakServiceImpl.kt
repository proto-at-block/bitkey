package build.wallet.chaincode.delegation

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitcoin.keys.ExtendedPrivateKey
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ensureNotNull
import build.wallet.keybox.KeyboxDao
import build.wallet.logging.logDebug
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toErrorIfNull
import kotlinx.coroutines.flow.first

@BitkeyInject(AppScope::class)
class ChaincodeDelegationTweakServiceImpl(
  private val psbtUtils: PsbtUtils,
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val keyboxDao: KeyboxDao,
) : ChaincodeDelegationTweakService {
  override suspend fun psbtWithTweaks(psbt: Psbt): Result<Psbt, ChaincodeDelegationError> =
    coroutineBinding {
      val keybox = keyboxDao
        .activeKeybox()
        .first()
        .logFailure { "Failed to fetch active keybox for mobile pay signing." }
        .mapError {
          ChaincodeDelegationError.ActiveKeyboxUnavailable(it.cause, "${it.message}")
        }.toErrorIfNull {
          ChaincodeDelegationError.ActiveKeyboxUnavailable(null, "No active keybox available.")
        }.bind()

      val appAccountDprv = appPrivateKeyDao
        .getAppSpendingPrivateKey(keybox.activeSpendingKeyset.appKey)
        .logFailure { "Failed to retrieve app spending private key for mobile pay signing." }
        .mapError { error ->
          ChaincodeDelegationError.AppSpendingPrivateKeyMissing(
            cause = error,
            message = "Failed to retrieve app spending private key for mobile pay signing."
          )
        }.toErrorIfNull {
          ChaincodeDelegationError.AppSpendingPrivateKeyMissing(
            cause = null,
            message = "App spending private key is missing."
          )
        }
        .bind()
        .key

      psbtWithTweaks(psbt, appAccountDprv, keybox.activeSpendingKeyset).bind()
    }

  override suspend fun psbtWithTweaks(
    psbt: Psbt,
    appSpendingPrivateKey: ExtendedPrivateKey,
    spendingKeyset: SpendingKeyset,
  ): Result<Psbt, ChaincodeDelegationError> =
    coroutineBinding {
      val hwAccountDpub = spendingKeyset.hardwareKey.key

      val serverRootXpub = ensureNotNull(
        spendingKeyset.f8eSpendingKeyset.privateWalletRootXpub
      ) {
        ChaincodeDelegationError.ServerRootXpubMissing(null, "Server root xpub is missing.")
      }

      val psbtWithTweaks = psbtUtils.psbtWithTweaks(
        psbt = psbt,
        appAccountDprv = appSpendingPrivateKey,
        serverRootXpub = serverRootXpub,
        hwAccountDpub = hwAccountDpub
      )
        .onFailure { logDebug { "Failed to apply PSBT tweaks: $it" } }
        .result
        .bind()

      psbt.copy(base64 = psbtWithTweaks)
    }

  override suspend fun sweepPsbtWithTweaks(
    psbt: Psbt,
    sourceKeyset: SpendingKeyset,
    destinationKeyset: SpendingKeyset,
  ): Result<Psbt, ChaincodeDelegationError> =
    coroutineBinding {
      // Get destination keyset app private key
      val destAppPrivateKey = appPrivateKeyDao
        .getAppSpendingPrivateKey(destinationKeyset.appKey)
        .logFailure { "Failed to retrieve destination app private key for sweep." }
        .mapError { error ->
          ChaincodeDelegationError.AppSpendingPrivateKeyMissing(
            cause = error,
            message = "Failed to retrieve destination app spending private key for sweep."
          )
        }
        .toErrorIfNull {
          ChaincodeDelegationError.AppSpendingPrivateKeyMissing(
            cause = null,
            message = "Destination app private key not found."
          )
        }
        .bind()
        .key

      val destServerRootXpub =
        ensureNotNull(destinationKeyset.f8eSpendingKeyset.privateWalletRootXpub) {
          ChaincodeDelegationError.ServerRootXpubMissing(
            cause = null,
            message = "Destination server root xpub not found."
          )
        }

      val destHwDpub = destinationKeyset.hardwareKey.key

      val sourceAppAccountDpub = sourceKeyset.appKey.key

      val sourceServerRootXpub =
        ensureNotNull(sourceKeyset.f8eSpendingKeyset.privateWalletRootXpub) {
          ChaincodeDelegationError.ServerRootXpubMissing(
            cause = null,
            message = "Source server root xpub not found."
          )
        }

      val sourceHwDpub = sourceKeyset.hardwareKey.key

      val tweakedBase64 = psbtUtils.sweepPsbtWithTweaks(
        psbt = psbt,
        sourceAppAccountDpub = sourceAppAccountDpub,
        sourceServerRootXpub = sourceServerRootXpub,
        sourceHwAccountDpub = sourceHwDpub,
        targetAppAccountDprv = destAppPrivateKey,
        targetServerRootXpub = destServerRootXpub,
        targetHwAccountDpub = destHwDpub
      )
        .onFailure {
          logDebug { "Failed to apply sweep PSBT tweaks: $it" }
        }
        .result
        .bind()

      psbt.copy(base64 = tweakedBase64)
    }

  override suspend fun migrationSweepPsbtWithTweaks(
    psbt: Psbt,
    destinationKeyset: SpendingKeyset,
  ): Result<Psbt, ChaincodeDelegationError> =
    coroutineBinding {
      // Get destination keyset app private key
      val destAppPrivateKey = appPrivateKeyDao
        .getAppSpendingPrivateKey(destinationKeyset.appKey)
        .logFailure { "Failed to retrieve destination app private key for migration sweep." }
        .mapError { error ->
          ChaincodeDelegationError.AppSpendingPrivateKeyMissing(
            cause = error,
            message = "Failed to retrieve destination app spending private key for migration sweep."
          )
        }
        .toErrorIfNull {
          ChaincodeDelegationError.AppSpendingPrivateKeyMissing(
            cause = null,
            message = "Destination app private key not found for migration."
          )
        }
        .bind()
        .key

      val destServerRootXpub =
        ensureNotNull(destinationKeyset.f8eSpendingKeyset.privateWalletRootXpub) {
          ChaincodeDelegationError.ServerRootXpubMissing(
            cause = null,
            message = "Destination server root xpub not found for migration."
          )
        }

      val destHwDpub = destinationKeyset.hardwareKey.key

      val tweakedBase64 = psbtUtils.migrationSweepPsbtWithTweaks(
        psbt = psbt,
        targetAppAccountDprv = destAppPrivateKey,
        targetServerRootXpub = destServerRootXpub,
        targetHwAccountDpub = destHwDpub
      )
        .onFailure {
          logDebug { "Failed to apply migration sweep PSBT tweaks: $it" }
        }
        .result
        .bind()

      psbt.copy(base64 = tweakedBase64)
    }
}
