import LocalAuthentication
import Shared

// MARK: -

public final class BiometricPrompterImpl: BiometricPrompter {
    public var isPrompting: Bool = false
    
    public func biometricsAvailability() -> BiometricsResult<KotlinBoolean> {
        let context = LAContext()
        var error: NSError?

        if (context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)) {
            return BiometricsResultOk(value: true)
        } else {
            if let laError = error as? LAError {
                switch laError.code {
                case .biometryNotAvailable:
                    return BiometricsResultErr(error: BiometricError.HardwareUnavailable())
                case .biometryNotEnrolled:
                    return BiometricsResultErr(error: BiometricError.NoBiometricEnrolled())
                case .biometryLockout:
                    return BiometricsResultErr(error: BiometricError.BiometricsLocked())
                default:
                    return BiometricsResultErr(error: BiometricError.HardwareUnavailable())
                }
            }
            return BiometricsResultErr(error: BiometricError.HardwareUnavailable())
        }
    }
    
    public func enrollBiometrics(completionHandler: @escaping (BiometricsResult<KotlinUnit>?, Error?) -> Void) {
        isPrompting = true
        let context = LAContext()
        context.evaluatePolicy(
            .deviceOwnerAuthenticationWithBiometrics,
            localizedReason: "To use secure features",
            reply: { success, authenticationError in
                self.isPrompting = false
                if (success) {
                    completionHandler(BiometricsResultOk(value: KotlinUnit()), nil)
                } else {
                    if let error = authenticationError as? NSError {
                        let laError = LAError(_nsError: error)
                        switch laError.code {
                        case .authenticationFailed:
                            completionHandler(BiometricsResultErr(error: BiometricError.AuthenticationFailed()), nil)
                        case .biometryNotAvailable:
                            completionHandler(BiometricsResultErr(error: BiometricError.HardwareUnavailable()), nil)
                        case .biometryNotEnrolled:
                            completionHandler(BiometricsResultErr(error: BiometricError.NoBiometricEnrolled()), nil)
                        case .biometryLockout:
                            completionHandler(BiometricsResultErr(error: BiometricError.BiometricsLocked()), nil)
                        default:
                            completionHandler(BiometricsResultErr(error: BiometricError.HardwareUnavailable()), nil)
                        }
                    }
                }
            }
        )
    }
    
    public func promptForAuth(completionHandler: @escaping (BiometricsResult<KotlinUnit>?, Error?) -> Void) {
        isPrompting = true
        let context = LAContext()
        context.evaluatePolicy(
            .deviceOwnerAuthenticationWithBiometrics,
            localizedReason: "To use secure features",
            reply: { success, authenticationError in
                self.isPrompting = false
                if (success) {
                    completionHandler(BiometricsResultOk(value: KotlinUnit()), nil)
                } else {
                    completionHandler(BiometricsResultErr(error: BiometricError.AuthenticationFailed()), nil)
                }
            }
        )
    }
}
