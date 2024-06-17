import UIKit

// MARK: -

class HapticPlayer {

    // MARK: - Static Internal Methods

    static func play(_ hapticEffect: HapticEffect) {
        switch hapticEffect {
        case .impact:
            playImpactEffect()

        case let .impactWithIntensity(intensity):
            playImpactEffect(intensity: intensity)

        case .selection:
            playSelectionEffect()

        case let .notification(notificationType):
            playNotificationEffect(notificationType)
        }
    }

    // MARK: - Private Methods

    private static func playImpactEffect() {
        let generator = UIImpactFeedbackGenerator()
        generator.impactOccurred()
    }

    private static func playImpactEffect(intensity: CGFloat) {
        let generator = UIImpactFeedbackGenerator()
        generator.impactOccurred(intensity: intensity)
    }

    private static func playSelectionEffect() {
        let generator = UISelectionFeedbackGenerator()
        generator.selectionChanged()
    }

    private static func playNotificationEffect(
        _ notificationType: UINotificationFeedbackGenerator.FeedbackType
    ) {
        let generator = UINotificationFeedbackGenerator()
        generator.notificationOccurred(notificationType)
    }

}

// MARK: -

enum HapticEffect: Equatable {

    /// Indicates that an impact has occurred.
    /// Uses `UIImpactFeedbackGenerator` internally.
    case impact

    /// Indicates that an impact has occurred.
    /// Uses `UIImpactFeedbackGenerator` internally.
    case impactWithIntensity(CGFloat)

    /// Indicates that  a selection has changed.
    /// Uses `UISelectionFeedbackGenerator` internally.
    case selection

    /// Indicates that a task or action has succeeded, failed, or produced a warning.
    /// Uses `UINotificationFeedbackGenerator` internally.
    case notification(UINotificationFeedbackGenerator.FeedbackType)

}
