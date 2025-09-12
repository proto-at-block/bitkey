import core
import Foundation
import Shared

class PsbtUtilsImpl: Shared.PsbtUtils {
    func psbtWithTweaks(
        psbt: Shared.Psbt,
        appRootXprv: Shared.ExtendedPrivateKey,
        serverRootXpub: String,
        hwAccountDpub: Shared.DescriptorPublicKey
    ) -> ChaincodeDelegationResult<NSString> {
        return ChaincodeDelegationResult {
            try core.psbtWithTweaks(
                psbt: psbt.base64,
                appRootXprv: appRootXprv.xprv,
                serverRootXpub: serverRootXpub,
                hwDpub: hwAccountDpub.dpub
            ) as NSString
        }
    }
}
