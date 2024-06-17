import UIKit

public protocol ViewUsingKeyboardLayoutGuide {

    func deactivateKeyboardLayoutGuideConstraint()

}

/// Intended to be used with view controllers that are presented full screen.
public final class ModalFlowTransitioningAnimator: NSObject, UIViewControllerAnimatedTransitioning {

    // MARK: -

    public enum Style {
        case presented
        case dismissed
    }

    public enum ScreenType {
        case standard
        case roundedCorners

        fileprivate var cornerRadius: CGFloat {
            switch screenType {
            case .standard: return 10
            case .roundedCorners: return 40
            }
        }
    }

    public static var screenType = ScreenType.roundedCorners

    // MARK: - Private Properties

    private let style: Style

    private let presentationDuration: TimeInterval = 0.55
    private let dismissalDuration: TimeInterval = 0.3

    // MARK: - Life Cycle

    public init(style: Style) {
        self.style = style
    }

    // MARK: - UIViewControllerAnimatedTransitioning

    // Use the result of this method internally as the source of truth for what the duration of the
    // animation should be.
    public func transitionDuration(using _: UIViewControllerContextTransitioning?) -> TimeInterval {
        switch style {
        case .presented: return presentationDuration
        case .dismissed: return dismissalDuration
        }
    }

    public func animateTransition(using transitionContext: UIViewControllerContextTransitioning) {
        switch style {
        case .presented:
            animatePresentedTransition(using: transitionContext)

        case .dismissed:
            animateDismissedTransition(using: transitionContext)
        }
    }

    // MARK: - Private Methods

    private func animatePresentedTransition(
        using transitionContext: UIViewControllerContextTransitioning
    ) {
        let containerView = transitionContext.containerView
        let duration = transitionDuration(using: transitionContext)
        guard let toView = transitionContext.view(forKey: .to),
              let fromView = transitionContext.view(forKey: .from)
        else {
            transitionContext.completeTransition(false)
            return
        }

        // The `.from` view is automatically added to the container view by the system, but we want
        // to manage it
        fromView.removeFromSuperview()

        // Place a corner radius on the from view
        let fromContainerView = ModalFlowTransitioningAnimator.createRoundedSubviewContainer(
            fromView,
            frame: containerView.bounds
        )
        containerView.addSubview(fromContainerView)

        let dimmingView = ModalFlowTransitioningAnimator
            .createDimmingView(frame: containerView.bounds)
        dimmingView.alpha = 0
        containerView.addSubview(dimmingView)

        let toContainerView = ModalFlowTransitioningAnimator.createRoundedSubviewContainer(
            toView,
            frame: containerView.bounds
        )
        toContainerView.frame.origin.y = containerView.bounds.height
        containerView.addSubview(toContainerView)
        if let toViewControllerModalPresented = transitionContext
            .viewController(forKey: .to) as? ModalFlowTransitionPresented
        {
            // The `to` view controller adopts `ModalFlowTransitionPresented`, so we have a
            // background color below the content
            toContainerView.autoresizesSubviews = false
            toContainerView.backgroundColor = toViewControllerModalPresented
                .modalFlowPresentedBottomContentColor
            toContainerView.frame.size.height = toContainerView.frame.height + 50
        }

        let propertyAnimator = UIViewPropertyAnimator(duration: duration, dampingRatio: 0.75) {
            toContainerView.frame.origin.y = 0
            dimmingView.alpha = 1
            fromContainerView.transform = CGAffineTransform(scaleX: 0.9, y: 0.9)
        }

        propertyAnimator.addCompletion { _ in
            // The containerView is persistent during the presented phase, so clean up the views
            // that were created for the animation
            fromContainerView.removeFromSuperview()
            dimmingView.removeFromSuperview()
            containerView.addSubview(toView)
            toContainerView.removeFromSuperview()

            transitionContext.completeTransition(true)
        }

        propertyAnimator.startAnimation()
    }

    private func animateDismissedTransition(
        using transitionContext: UIViewControllerContextTransitioning
    ) {
        let containerView = transitionContext.containerView
        let duration = transitionDuration(using: transitionContext)
        guard let toView = transitionContext.view(forKey: .to),
              let fromView = transitionContext.view(forKey: .from)
        else {
            transitionContext.completeTransition(false)
            return
        }

        // We need to deactivate the bottom keyboard layout guide constraint for the animation to
        // work
        fromView.deactivateAllKeyboardLayoutGuideConstraints()
        // The `.from` view is automatically added to the container view by the system, but we want
        // to manage it
        fromView.removeFromSuperview()

        let toContainerView = ModalFlowTransitioningAnimator.createRoundedSubviewContainer(
            toView,
            frame: containerView.bounds
        )
        containerView.addSubview(toContainerView)
        toContainerView
            .transform = CGAffineTransform(
                scaleX: 0.9,
                y: 0.9
            ) // Make sure to set the transform after adding the subview, so it can correctly
        // inherit attributes such as safeAreaInsets

        let dimmingView = ModalFlowTransitioningAnimator
            .createDimmingView(frame: containerView.bounds)
        containerView.addSubview(dimmingView)

        // Place a corner radius on the from view
        let fromContainerView = ModalFlowTransitioningAnimator.createRoundedSubviewContainer(
            fromView,
            frame: containerView.bounds
        )
        containerView.addSubview(fromContainerView)

        let propertyAnimator = UIViewPropertyAnimator(duration: duration, curve: .easeIn) {
            toContainerView.transform = .identity
            fromContainerView.frame.origin.y = containerView.bounds.height
            dimmingView.alpha = 0
        }

        propertyAnimator.addCompletion { _ in
            transitionContext.completeTransition(true)
        }

        propertyAnimator.startAnimation()
    }

    /// Places a `fromView` or `toView` into a container view with a clipping corner radius.
    private static func createRoundedSubviewContainer(_ subview: UIView, frame: CGRect) -> UIView {
        let result = UIView(frame: frame)
        result.addSubview(subview)
        subview.frame = result.bounds
        result.layer.cornerRadius = screenType.cornerRadius
        result.clipsToBounds = true
        return result
    }

    /// Just creates a view with a black background.
    private static func createDimmingView(frame: CGRect) -> UIView {
        let result = UIView(frame: frame)
        result.backgroundColor = .black
        return result
    }

}

// MARK: -

/// Optionally adopt this protocol on the presented view controller to provide a bottom content
/// color.
public protocol ModalFlowTransitionPresented where Self: UIViewController {

    var modalFlowPresentedBottomContentColor: UIColor { get }

}

// MARK: -

/**
 A simple transitioning delegate that always returns ModalFlowTransitioningAnimators for presenting and dismissing view
 controllers. If you need more conditional behavior, adopt the `UIViewControllerTransitioningDelegate` protocol on your
 decision-making object.

 - Precondition: all view controllers that use this transitioning delegate must have a modalPresentationStyle of
   .fullScreen.
 */
public class ModalFlowTransitioningDelegate: NSObject, UIViewControllerTransitioningDelegate {

    /// Use this shared instance because the `transitioningDelegate` is a weak reference.
    public static let `default` = ModalFlowTransitioningDelegate()

    public func animationController(
        forPresented presented: UIViewController,
        presenting _: UIViewController,
        source _: UIViewController
    ) -> UIViewControllerAnimatedTransitioning? {
        assert(
            presented.modalPresentationStyle == .fullScreen,
            "View controllers presented with a modal flow transitioning delegate must have a presentation style of .fullScreen."
        )
        return ModalFlowTransitioningAnimator(style: .presented)
    }

    public func animationController(
        forDismissed _: UIViewController
    ) -> UIViewControllerAnimatedTransitioning? {
        return ModalFlowTransitioningAnimator(style: .dismissed)
    }

}

// MARK: -

private extension UIView {

    func deactivateAllKeyboardLayoutGuideConstraints() {
        var nextView: UIView? = self
        while let view = nextView {
            if let viewWithGuide = view as? ViewUsingKeyboardLayoutGuide {
                viewWithGuide.deactivateKeyboardLayoutGuideConstraint()
            }
            nextView = view.subviews.first
        }
    }

}
