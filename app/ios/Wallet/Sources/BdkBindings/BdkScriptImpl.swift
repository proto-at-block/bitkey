import BitcoinDevKit
import Shared

class BdkScriptImpl: BdkScript {

    let ffiScript: Script

    init(ffiScript: Script) {
        self.ffiScript = ffiScript
    }

    func rawOutputScript() -> [KotlinUByte] {
        return ffiScript.toBytes().map { KotlinUByte(value: $0) }
    }

}
