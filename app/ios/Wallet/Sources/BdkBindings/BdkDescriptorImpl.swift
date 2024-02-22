import BitcoinDevKit
import Shared

class BdkDescriptorImpl : BdkDescriptor {

    let ffiDescriptor: Descriptor
    
    init(ffiDescriptor: Descriptor) {
        self.ffiDescriptor = ffiDescriptor
    }
    
    func asStringPrivate() -> String {
        return ffiDescriptor.asStringPrivate()
    }
}
