package build.wallet.platform.random

class UuidGeneratorImpl : UuidGenerator {
  override fun random(): String = uuid()
}
