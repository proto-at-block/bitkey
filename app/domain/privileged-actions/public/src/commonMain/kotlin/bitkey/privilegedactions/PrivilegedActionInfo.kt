package bitkey.privilegedactions

import bitkey.f8e.privilegedactions.PrivilegedActionInstance

/**
 * Extended information about a privileged action
 */
data class PrivilegedActionInfo(
  val instance: PrivilegedActionInstance,
  val status: PrivilegedActionStatus,
)
