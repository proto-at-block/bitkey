import UIKit

/// Conforming types have an associated Model that can be applied to instances of this type.
public protocol ModelRepresentable {

    /// A data type that represents an instance and can be applied.
    associatedtype Model

    /// Applies the given model to this instance.
    func apply(model: Model)
}

// MARK: -

/// A UIView that has an associated Model type that can be applied instances of this type.
public protocol ModelRepresentableView: ModelRepresentable & UIView {

    /// Required initializer so that instance of this UIView type can be statically initialized.
    init()

    var isHidden: Bool { get set }

}

// MARK: -

/// A UIViewController that has an associated Model type that can be applied instances of this type.
public protocol ModelRepresentableViewController: ModelRepresentable & UIViewController {}

// MARK: -

public extension ModelRepresentableView {

    /**
     If the given model if nil, hides the view.
     Otherwise, shows the view and applies the model to it.
     */
    func applyOrHide(model: Model?) {
        if let model {
            apply(model: model)
            isHidden = false
        } else {
            isHidden = true
        }
    }

}

// MARK: -

extension ModeledButton: ModelRepresentableView {}
extension UILabel: ModelRepresentableView {}
