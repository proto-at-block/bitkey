
import Shared
import LightningDevKitNode

typealias FFINodeBuilder = LightningDevKitNode.Builder

final public class LdkNodeBuilderFactoryImpl: LdkNodeBuilderFactory {
    public init() {}
    
    public func ldkNode(config: LdkNodeConfig) -> LdkResult<LdkNode> {
        let node = LdkNodeImpl(
            ffiNode: FFINodeBuilder.fromConfig(config: config.ffiNodeConfig).build()
        )
        
        return LdkResultOk(value: node)
    }
}
