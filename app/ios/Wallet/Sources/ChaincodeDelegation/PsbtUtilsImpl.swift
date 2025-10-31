import core
import Foundation
import Shared

class PsbtUtilsImpl: Shared.PsbtUtils {
    func psbtWithTweaks(
        psbt: Shared.Psbt,
        appAccountDprv: Shared.ExtendedPrivateKey,
        serverRootXpub: String,
        hwAccountDpub: Shared.DescriptorPublicKey
    ) -> ChaincodeDelegationResult<NSString> {
        return ChaincodeDelegationResult {
            try core.psbtWithTweaks(
                psbt: psbt.base64,
                appAccountDprv: appAccountDprv.xprv,
                serverRootXpub: serverRootXpub,
                hwDpub: hwAccountDpub.dpub
            ) as NSString
        }
    }

    func sweepPsbtWithTweaks(
        psbt: Shared.Psbt,
        sourceAppAccountDpub: Shared.DescriptorPublicKey,
        sourceServerRootXpub: String,
        sourceHwAccountDpub: Shared.DescriptorPublicKey,
        targetAppAccountDprv: Shared.ExtendedPrivateKey,
        targetServerRootXpub: String,
        targetHwAccountDpub: Shared.DescriptorPublicKey
    ) -> ChaincodeDelegationResult<NSString> {
        return ChaincodeDelegationResult {
            try core.sweepPsbtWithTweaks(
                psbt: psbt.base64,
                sourceAppAccountDpub: sourceAppAccountDpub.dpub,
                sourceServerRootXpub: sourceServerRootXpub,
                sourceHwDpub: sourceHwAccountDpub.dpub,
                targetAppAccountDprv: targetAppAccountDprv.xprv,
                targetServerRootXpub: targetServerRootXpub,
                targetHwDpub: targetHwAccountDpub.dpub
            ) as NSString
        }
    }

    func migrationSweepPsbtWithTweaks(
        psbt: Shared.Psbt,
        targetAppAccountDprv: Shared.ExtendedPrivateKey,
        targetServerRootXpub: String,
        targetHwAccountDpub: Shared.DescriptorPublicKey
    ) -> ChaincodeDelegationResult<NSString> {
        return ChaincodeDelegationResult {
            try core.migrationSweepPsbtWithTweaks(
                psbt: psbt.base64,
                targetAppAccountDprv: targetAppAccountDprv.xprv,
                targetServerRootXpub: targetServerRootXpub,
                targetHwDpub: targetHwAccountDpub.dpub
            ) as NSString
        }
    }
}
