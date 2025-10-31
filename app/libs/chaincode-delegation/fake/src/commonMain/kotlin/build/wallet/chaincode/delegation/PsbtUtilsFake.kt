package build.wallet.chaincode.delegation

import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitcoin.keys.ExtendedPrivateKey
import build.wallet.bitcoin.transactions.Psbt

class PsbtUtilsFake : PsbtUtils {
  var psbtWithTweaksResult: ChaincodeDelegationResult<String> =
    ChaincodeDelegationResult.Ok("psbt-with-tweaks")
  var sweepPsbtWithTweaksResult: ChaincodeDelegationResult<String> =
    ChaincodeDelegationResult.Ok("sweep-psbt-with-tweaks")
  var migrationSweepPsbtWithTweaksResult: ChaincodeDelegationResult<String> =
    ChaincodeDelegationResult.Ok("migration-sweep-psbt-with-tweaks")

  override fun psbtWithTweaks(
    psbt: Psbt,
    appAccountDprv: ExtendedPrivateKey,
    serverRootXpub: String,
    hwAccountDpub: DescriptorPublicKey,
  ): ChaincodeDelegationResult<String> = psbtWithTweaksResult

  override fun sweepPsbtWithTweaks(
    psbt: Psbt,
    sourceAppAccountDpub: DescriptorPublicKey,
    sourceServerRootXpub: String,
    sourceHwAccountDpub: DescriptorPublicKey,
    targetAppAccountDprv: ExtendedPrivateKey,
    targetServerRootXpub: String,
    targetHwAccountDpub: DescriptorPublicKey,
  ): ChaincodeDelegationResult<String> = sweepPsbtWithTweaksResult

  override fun migrationSweepPsbtWithTweaks(
    psbt: Psbt,
    targetAppAccountDprv: ExtendedPrivateKey,
    targetServerRootXpub: String,
    targetHwAccountDpub: DescriptorPublicKey,
  ): ChaincodeDelegationResult<String> = migrationSweepPsbtWithTweaksResult

  fun reset() {
    psbtWithTweaksResult = ChaincodeDelegationResult.Ok("psbt-with-tweaks")
    sweepPsbtWithTweaksResult = ChaincodeDelegationResult.Ok("sweep-psbt-with-tweaks")
    migrationSweepPsbtWithTweaksResult =
      ChaincodeDelegationResult.Ok("migration-sweep-psbt-with-tweaks")
  }
}
