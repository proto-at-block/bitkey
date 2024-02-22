package build.wallet.ldk.bindings

interface LdkNodeBuilderFactory {
  fun ldkNode(config: LdkNodeConfig): LdkResult<LdkNode>
}
