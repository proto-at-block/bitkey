import BitcoinDevKit
import Shared

class BdkScriptImpl: NSObject, BdkScript {

    let rawOutputScript: [KotlinUByte]

    init(ffiScript: Script) {
        self.rawOutputScript = ffiScript.toBytes().map { KotlinUByte(value: $0) }
    }

    func toFfiScript() -> Script {
        return Script(rawOutputScript: rawOutputScript.map(\.uint8Value))
    }

    override func isEqual(_ object: Any?) -> Bool {
        guard let other = object as? BdkScriptImpl else {
            return false
        }
        return self.rawOutputScript == other.rawOutputScript
    }

    override var hash: Int {
        return rawOutputScript.hashValue
    }

}
