package build.wallet.platform.random

import platform.Foundation.NSUUID

actual class UuidImpl : Uuid {
  override fun random(): String = NSUUID().UUIDString()
}
