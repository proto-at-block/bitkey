package build.wallet.platform.random

import java.util.UUID

actual class UuidImpl : Uuid {
  override fun random(): String = UUID.randomUUID().toString()
}
