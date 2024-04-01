package build.wallet.platform.random

import java.util.UUID

actual fun uuid(): String = UUID.randomUUID().toString()
