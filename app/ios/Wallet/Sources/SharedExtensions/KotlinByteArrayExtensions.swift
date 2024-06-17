import Foundation
import Shared

public extension KotlinByteArray {

    convenience init(_ intArray: [UInt8]) {
        self.init(size: Int32(intArray.count)) { index in
            return KotlinByte(integerLiteral: .init(intArray[index.intValue]))
        }
    }

    func asData() -> Data {
        Data(asUInt8Array())
    }

    func asUInt8Array() -> [UInt8] {
        return (0 ..< size).map {
            UInt8(bitPattern: get(index: $0))
        }
    }

}

public extension Data {

    var asKotlinByteArray: KotlinByteArray {
        KotlinByteArray(bytes)
    }

    private var bytes: [UInt8] {
        return [UInt8](self)
    }

}
