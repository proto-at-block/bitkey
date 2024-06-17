import BitcoinDevKit
import Shared

public class BdkMnemonicGeneratorImpl: BdkMnemonicGenerator {

    public init() {}

    public func generateMnemonicBlocking(wordCount: BdkMnemonicWordCount) -> BdkMnemonic {
        var ffiWordCount: WordCount {
            switch wordCount {
            case .words24:
                return WordCount.words24
            default:
                fatalError()
            }
        }
        return BdkMnemonicImpl(ffiMnemonic: Mnemonic(wordCount: ffiWordCount))
    }

    public func fromStringBlocking(mnemonic: String) -> BdkMnemonic {
        return BdkMnemonicImpl(ffiMnemonic: try! Mnemonic.fromString(mnemonic: mnemonic))
    }
}
