import BitcoinDevKit
import Shared

internal class BdkMnemonicImpl : BdkMnemonic {
    var words: String {
        return ffiMnemonic.asString()
    }
    
    let ffiMnemonic: Mnemonic

    init(ffiMnemonic: Mnemonic) {
        self.ffiMnemonic = ffiMnemonic
    }
}
