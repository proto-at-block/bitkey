import DeclaredAgeRange
import Shared
import UIKit

/**
 * iOS implementation of IosAgeRangeService using Apple's DeclaredAgeRange framework (iOS 26.2+).
 *
 * Named `IosAgeRangeServiceImpl` (instead of `AgeRangeServiceImpl`) to avoid naming collision
 * with Apple's `DeclaredAgeRange.AgeRangeService` framework interface. Using `AgeRangeService`
 * in Swift would cause ambiguous type references.
 *
 * Wraps Apple's privacy-preserving age verification API to comply with App Store
 * Accountability Acts (Texas SB2420, Utah, Louisiana).
 *
 * ## Apple DeclaredAgeRange Framework
 *
 * Apple's DeclaredAgeRange framework provides privacy-preserving age verification:
 * - Age data is stored on-device in the user's Apple Account
 * - User sets their "Age Range for Apps" in: Settings → [Name] → Personal Information → Age Range
 * - Apps never receive exact age, only ranges relative to requested age gates
 * - System UI handles all user interaction; apps cannot customize the prompt
 *
 * ## Request Flow
 *
 * 1. **Jurisdiction Check** (`isEligibleForAgeFeatures`):
 *    - Apple determines if the user's region requires age verification
 *    - Currently applies to: Texas, Utah, Louisiana
 *    - If false, returns `NotApplicable` immediately (no prompt shown)
 *
 * 2. **Age Range Request** (`requestAgeRange(ageGate:in:)`):
 *    - Behavior depends on user's "Age Range for Apps" setting (default is "Ask First"):
 *      - **Always**: Returns `.sharing(range)` immediately, no dialog shown
 *      - **Never**: Returns `.declinedSharing` immediately, no dialog shown
 *      - **Ask First**: Shows system dialog where user can share or decline
 *
 * 3. **Response Handling**:
 *    - `.sharing(Range)`: User shared their age range
 *      - `lowerBound` indicates minimum age (e.g., 18 means "18 or older")
 *      - `lowerBound` can be nil if age is unknown
 *    - `.declinedSharing`: User declined or has sharing disabled
 *
 * ## Response Types
 *
 * | Scenario | Response | User Experience |
 * |----------|----------|-----------------|
 * | iOS < 26.2 | `ApiNotAvailable` | Nothing shown |
 * | No root VC | Throws `NO_ROOT_VIEW_CONTROLLER` | Nothing shown |
 * | Not in TX/UT/LA | `NotApplicable` | Nothing shown |
 * | User shares 18+ | `Sharing(lowerBound: 18+)` | Dialog or auto |
 * | User shares <18 | `Sharing(lowerBound: <18)` | Dialog or auto |
 * | User declines | `DeclinedSharing` | Dialog or auto-decline |
 * | Unknown age | `Sharing(lowerBound: nil)` | Dialog or auto |
 * | API error | Throws exception | Nothing shown |
 *
 * ## Error Handling
 *
 * - `NO_ROOT_VIEW_CONTROLLER`: Developer error - UIWindow has no root VC
 * - `INVALID_REQUEST`: Invalid age gate parameter (should not happen with our usage)
 * - `UNKNOWN_ERROR`: Unrecognized error from Apple framework
 * - `UNEXPECTED_ERROR`: Unexpected exception during request
 *
 * All errors are thrown as `AgeRangeServiceException` and should be handled by callers
 * with fail-open behavior (allow access on error).
 *
 * @see https://developer.apple.com/documentation/declaredagerange
 */
public final class IosAgeRangeServiceImpl: Shared.IosAgeRangeService {
    private let window: UIWindow

    public init(window: UIWindow) {
        self.window = window
    }

    /**
     * Requests the user's age range from Apple's DeclaredAgeRange API.
     *
     * - Parameter ageGate: The age threshold to check against (e.g., 18 for adult content).
     *   The API returns ranges relative to this gate.
     * - Returns: An `AgeRangeResponse` indicating the result of the age check.
     * - Throws: `AgeRangeServiceException` on API errors.
     *
     * This method is safe to call on any iOS version - it returns `ApiNotAvailable`
     * on iOS < 26.2 rather than crashing.
     */
    public func requestAgeRange(ageGate: Int32) async throws -> AgeRangeResponse {
        // iOS 26.2+ required for DeclaredAgeRange framework
        guard #available(iOS 26.2, *) else {
            return AgeRangeResponseApiNotAvailable.shared
        }

        // Need a view controller to present the system age sharing dialog
        // This accesses UIKit on main actor
        guard let viewController = await window.rootViewController else {
            throw AgeRangeServiceException(
                errorCode: "NO_ROOT_VIEW_CONTROLLER",
                message: "Window has no root view controller",
                cause: nil
            ).asError()
        }

        do {
            // Step 1: Check if user is in a jurisdiction requiring age verification
            // Apple determines this based on device region settings
            // Currently true for: Texas, Utah, Louisiana
            let isEligible = try await DeclaredAgeRange.AgeRangeService.shared
                .isEligibleForAgeFeatures

            guard isEligible else {
                // User not in an applicable jurisdiction - no age check required
                return AgeRangeResponseNotApplicable.shared
            }

            // Step 2: Request age range from user
            // Behavior depends on user's "Age Range for Apps" setting (default is "Ask First"):
            // - "Always": Returns .sharing(range) immediately, no dialog
            // - "Never": Returns .declinedSharing immediately, no dialog
            // - "Ask First": Shows system dialog where user chooses to share or decline
            let response = try await DeclaredAgeRange.AgeRangeService.shared.requestAgeRange(
                ageGates: Int(ageGate),
                in: viewController
            )

            // Step 3: Map Apple's response to our Kotlin-friendly types
            switch response {
            case let .sharing(range):
                // User shared their age range
                // lowerBound is the minimum age in their range (e.g., 18 means "18 or older")
                // Can be nil if Apple doesn't have age information for this user
                let lowerBound = range.lowerBound.map { KotlinInt(int: Int32($0)) }
                return AgeRangeResponseSharing(lowerBound: lowerBound)
            case .declinedSharing:
                // User declined to share or has "Never" enabled in settings
                return AgeRangeResponseDeclinedSharing.shared
            @unknown default:
                // Future-proofing: treat unknown responses as declined
                return AgeRangeResponseDeclinedSharing.shared
            }
        } catch let ageError as DeclaredAgeRange.AgeRangeService.Error {
            // Handle known DeclaredAgeRange framework errors
            switch ageError {
            case .notAvailable:
                // API became unavailable after initial check (rare edge case)
                return AgeRangeResponseApiNotAvailable.shared
            case .invalidRequest:
                // Invalid ageGate parameter - should not happen with valid input
                throw AgeRangeServiceException(
                    errorCode: "INVALID_REQUEST",
                    message: ageError.localizedDescription,
                    cause: nil
                ).asError()
            @unknown default:
                // Future-proofing: unknown error types from Apple
                throw AgeRangeServiceException(
                    errorCode: "UNKNOWN_ERROR",
                    message: ageError.localizedDescription,
                    cause: nil
                ).asError()
            }
        } catch {
            // Unexpected errors (network issues, system errors, etc.)
            throw AgeRangeServiceException(
                errorCode: "UNEXPECTED_ERROR",
                message: error.localizedDescription,
                cause: nil
            ).asError()
        }
    }
}
