package build.wallet.frost

class ShareGeneratorFactoryImpl : ShareGeneratorFactory {
  override fun createShareGenerator(): ShareGenerator {
    return ShareGeneratorImpl()
  }
}
