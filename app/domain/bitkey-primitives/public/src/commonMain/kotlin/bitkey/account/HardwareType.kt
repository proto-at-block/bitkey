package bitkey.account

import kotlinx.serialization.Serializable

/**
 * Represents the type of hardware device.
 */
@Serializable
enum class HardwareType {
  /**
   * W1 hardware variant.
   */
  W1,

  /**
   * W3 hardware variant.
   */
  W3,
}
