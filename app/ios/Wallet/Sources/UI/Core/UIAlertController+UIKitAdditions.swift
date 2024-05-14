import Shared
import UIKit

// MARK: -

extension UIAlertController {

    public struct ButtonModel {
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

        public init(buttonAlertModel: ButtonAlertModel) {
            self.title = buttonAlertModel.title
            self.message = buttonAlertModel.subline
            var actions: [Action] = [
                .init(
                    title: buttonAlertModel.primaryButtonText,
                    action: buttonAlertModel.onPrimaryButtonClick,
                    style: buttonAlertModel.primaryButtonStyle.asUIAlertActionStyle
                )
            ]

            if let secondaryButtonText = buttonAlertModel.secondaryButtonText,
               let onSecondaryButtonClick = buttonAlertModel.onSecondaryButtonClick {
                actions.append(
                    .init(
                        title: secondaryButtonText,
                        action: onSecondaryButtonClick,
                        style: buttonAlertModel.secondaryButtonStyle.asUIAlertActionStyle
                    )
                )
            }

            self.actions = actions
        }
    }
    
    public struct InputModel {
        let title: String
        let message: String?
        let text: String
        let confirm: (String) -> Void
        let cancel: () -> Void
        
        init(inputAlertModel: InputAlertModel) {
            self.title = inputAlertModel.title
            self.message = inputAlertModel.subline
            self.confirm = inputAlertModel.onConfirm
            self.text = inputAlertModel.value
            self.cancel = inputAlertModel.onCancel
        }
        
    }

    public convenience init(model: ButtonModel) {
        self.init(title: model.title, message: model.message, preferredStyle: .alert)
        for action in model.actions {
            addAction(UIAlertAction(title: action.title, style: action.style, handler: { _ in action.action() }))
        }
    }
    
    public convenience init(model: InputModel) {
        self.init(title: model.title, message: model.message, preferredStyle: .alert)
        addTextField { (textField) in
            textField.text = model.text
        }
        addAction(
            UIAlertAction(
                title: "Cancel",
                style: .default,
                handler: { _ in
                    model.cancel()
                }
            )
        )
        addAction(
            UIAlertAction(
                title: "Confirm",
                style: .default,
                handler: { _ in
                    if let textField = self.textFields?.first, let inputText = textField.text {
                        model.confirm(inputText)
                    }
                }
            )
        )
    }

    public convenience init(alertModel: AlertModel) {
        switch alertModel {
        case is ButtonAlertModel:
            self.init(model: .init(buttonAlertModel: alertModel as! ButtonAlertModel))
        case is InputAlertModel:
            self.init(model: .init(inputAlertModel: alertModel as! InputAlertModel))
        default:
            fatalError("Unsupported alert model: \(alertModel)")
        }
    }

}

// MARK: -

private enum Strings {
    static let cancel = "Cancel".localized(comment: "Title of the button allowing a user to close an alert.")
    static let ok = "OK".localized(comment: "Title of the button allowing a user to dismiss an alert.")
}

private extension ButtonAlertModel.ButtonStyle {
    
    var asUIAlertActionStyle: UIAlertAction.Style {
        switch self {
        case .default_: return .default
        case .destructive: return .destructive
        default: fatalError("Unsupported alert model button style: \(self)")
        }
    }
}
