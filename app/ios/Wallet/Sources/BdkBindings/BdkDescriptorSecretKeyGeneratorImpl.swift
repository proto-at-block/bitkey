import BitcoinDevKit
import Shared

public class BdkDescriptorSecretKeyGeneratorImpl : BdkDescriptorSecretKeyGenerator {
    
    public init() {}
    
    public func generate(network: BdkNetwork, mnemonic: BdkMnemonic) -> BdkDescriptorSecretKey {
        let realBdkMnemonic = mnemonic as! BdkMnemonicImpl
        return BdkDescriptorSecretKeyImpl(
            ffiDescriptorSecretKey: DescriptorSecretKey(
                network: network.ffiNetwork,
                mnemonic: realBdkMnemonic.ffiMnemonic,
                password: nil
            )
        )
    }
}
