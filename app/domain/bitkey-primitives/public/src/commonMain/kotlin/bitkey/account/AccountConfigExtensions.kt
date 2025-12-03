package bitkey.account

/**
 * Returns true if the hardware type is configured as W3.
 * Works with both active account configs and default configs.
 */
val AccountConfig.isW3Hardware: Boolean
  get() = when (this) {
    is FullAccountConfig -> hardwareType == HardwareType.W3
    is DefaultAccountConfig -> hardwareType == HardwareType.W3
    else -> false
  }
