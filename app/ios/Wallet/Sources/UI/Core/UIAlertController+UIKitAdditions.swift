import Shared
import UIKit

// MARK: -

extension UIAlertController {

    public struct Model {
        public struct Action {
            let title: String
            let action: () -> Void
            let style: UIAlertAction.Style

            public init(
                title: String,
                action: @escaping () -> Void,
                style: UIAlertAction.Style
            ) {
                self.title = title
                self.action = action
                self.style = style
            }

            public static func cancel(action: @escaping () -> Void = {}) -> Action {
                return Action(title: Strings.cancel, action: action, style: .cancel)
            }

            public static func ok(action: @escaping () -> Void = {}) -> Action {
                return Action(title: Strings.ok, action: action, style: .default)
            }
        }

        let title: String
        let message: String?
        let actions: [Action]

        public init(
            title: String,
            message: String? = nil,
            actions: [Action]
        ) {
            self.title = title
            self.message = message
            self.actions = actions
        }

        public init(alertModel: AlertModel) {
            self.title = alertModel.title
            self.message = alertModel.subline
            var actions: [Action] = [
                .init(
                    title: alertModel.primaryButtonText,
                    action: alertModel.onPrimaryButtonClick,
                    style: alertModel.primaryButtonStyle.asUIAlertActionStyle
                )
            ]

            if let secondaryButtonText = alertModel.secondaryButtonText,
               let onSecondaryButtonClick = alertModel.onSecondaryButtonClick {
                actions.append(
                    .init(
                        title: secondaryButtonText,
                        action: onSecondaryButtonClick,
                        style: alertModel.secondaryButtonStyle.asUIAlertActionStyle
                    )
                )
            }

            self.actions = actions
        }
    }

    public convenience init(model: Model) {
        self.init(title: model.title, message: model.message, preferredStyle: .alert)
        for action in model.actions {
            addAction(UIAlertAction(title: action.title, style: action.style, handler: { _ in action.action() }))
        }
    }

    public convenience init(alertModel: AlertModel) {
        self.init(model: .init(alertModel: alertModel))
    }

}

// MARK: -

private enum Strings {
    static let cancel = "Cancel".localized(comment: "Title of the button allowing a user to close an alert.")
    static let ok = "OK".localized(comment: "Title of the button allowing a user to dismiss an alert.")
}

private extension AlertModel.ButtonStyle {
    
    var asUIAlertActionStyle: UIAlertAction.Style {
        switch self {
        case .default_: return .default
        case .destructive: return .destructive
        default: fatalError("Unsupported alert model button style: \(self)")
        }
    }
}
