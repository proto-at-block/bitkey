package build.wallet.chaincode.delegation

import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitcoin.keys.ExtendedPrivateKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.rust.core.Psbt as CorePsbt
import build.wallet.rust.core.migrationSweepPsbtWithTweaks as coreMigrationSweepPsbtWithTweaks
import build.wallet.rust.core.psbtWithTweaks as corePsbtWithTweaks
import build.wallet.rust.core.sweepPsbtWithTweaks as coreSweepPsbtWithTweaks

@BitkeyInject(AppScope::class)
class PsbtUtilsImpl : PsbtUtils {
  override fun psbtWithTweaks(
    psbt: build.wallet.bitcoin.transactions.Psbt,
    appAccountDprv: ExtendedPrivateKey,
    serverRootXpub: String,
    hwAccountDpub: DescriptorPublicKey,
  ): ChaincodeDelegationResult<CorePsbt> =
    runCatchingChaincodeDelegationError {
      corePsbtWithTweaks(
        psbt = psbt.base64,
        appAccountDprv = appAccountDprv.xprv,
        serverRootXpub = serverRootXpub,
        hwDpub = hwAccountDpub.dpub
      )
    }

  override fun sweepPsbtWithTweaks(
    psbt: build.wallet.bitcoin.transactions.Psbt,
    sourceAppAccountDpub: DescriptorPublicKey,
    sourceServerRootXpub: String,
    sourceHwAccountDpub: DescriptorPublicKey,
    targetAppAccountDprv: ExtendedPrivateKey,
    targetServerRootXpub: String,
    targetHwAccountDpub: DescriptorPublicKey,
  ): ChaincodeDelegationResult<CorePsbt> =
    runCatchingChaincodeDelegationError {
      coreSweepPsbtWithTweaks(
        psbt = psbt.base64,
        sourceAppAccountDpub = sourceAppAccountDpub.dpub,
        sourceServerRootXpub = sourceServerRootXpub,
        sourceHwDpub = sourceHwAccountDpub.dpub,
        targetAppAccountDprv = targetAppAccountDprv.xprv,
        targetServerRootXpub = targetServerRootXpub,
        targetHwDpub = targetHwAccountDpub.dpub
      )
    }

  override fun migrationSweepPsbtWithTweaks(
    psbt: build.wallet.bitcoin.transactions.Psbt,
    targetAppAccountDprv: ExtendedPrivateKey,
    targetServerRootXpub: String,
    targetHwAccountDpub: DescriptorPublicKey,
  ): ChaincodeDelegationResult<CorePsbt> =
    runCatchingChaincodeDelegationError {
      coreMigrationSweepPsbtWithTweaks(
        psbt = psbt.base64,
        targetAppAccountDprv = targetAppAccountDprv.xprv,
        targetServerRootXpub = targetServerRootXpub,
        targetHwDpub = targetHwAccountDpub.dpub
      )
    }
}
