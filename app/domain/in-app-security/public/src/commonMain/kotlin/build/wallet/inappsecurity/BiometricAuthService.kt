package build.wallet.inappsecurity

import kotlinx.coroutines.flow.StateFlow

interface BiometricAuthService {
  /**
   * Returns true if biometric authentication is enabled and required for the customer.
   */
  fun isBiometricAuthRequired(): StateFlow<Boolean>
}
