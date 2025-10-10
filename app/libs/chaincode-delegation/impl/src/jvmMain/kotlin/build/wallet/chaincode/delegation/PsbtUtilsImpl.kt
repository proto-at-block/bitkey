package build.wallet.chaincode.delegation

import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitcoin.keys.ExtendedPrivateKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.rust.core.Psbt as CorePsbt
import build.wallet.rust.core.psbtWithTweaks as corePsbtWithTweaks

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
}
