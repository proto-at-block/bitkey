package build.wallet.logging

import co.touchlab.kermit.platformLogWriter

actual fun platformLogWriter() = platformLogWriter(ColorfulMessageStringFormatter)
