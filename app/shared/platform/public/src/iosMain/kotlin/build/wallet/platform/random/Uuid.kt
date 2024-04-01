package build.wallet.platform.random

import platform.Foundation.NSUUID

actual fun uuid(): String = NSUUID().UUIDString()
