import UIKit

/**
 A protocol which should be adopted by view controllers that want to use the `SheetTransitioningDelegate`
 and `SheetPresentationController` in order to be presented like a sheet.
 */
public protocol SheetTransitioningViewController {

    func sizeThatFits(_ size: CGSize) -> CGSize

}

// MARK: -

/**
 * A class which can be used as the `transitioningDelegate` for a `UIViewController`,
 * and which instructs the view controller to be presented / dismissed from the bottom of the screen like
 * a system sheet.
 */
public final class SheetTransitioningDelegate: NSObject, UIViewControllerTransitioningDelegate {

    // MARK: - UIViewControllerTransitioningDelegate

    public func presentationController(
        forPresented presented: UIViewController,
        presenting: UIViewController?,
        source: UIViewController
    ) -> UIPresentationController? {
        SheetPresentationController(
            presentedViewController: presented,
            presenting: presenting
        )
    }

}

// MARK: -

public class SheetPresentationController: UIPresentationController {

    // MARK: - Private Properties

    private let blurEffectView: UIVisualEffectView
    private var tapGestureRecognizer = UITapGestureRecognizer()

    // MARK: - Life Cycle

    public override init(
      presentedViewController: UIViewController,
      presenting presentingViewController: UIViewController?
    ) {
        let blurEffect = UIBlurEffect(style: .systemThinMaterialDark)
        blurEffectView = UIVisualEffectView(effect: blurEffect)

        super.init(
          presentedViewController: presentedViewController,
          presenting: presentingViewController
        )

        tapGestureRecognizer = UITapGestureRecognizer(
          target: self,
          action: #selector(dismissController)
        )
        blurEffectView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        blurEffectView.isUserInteractionEnabled = true
        blurEffectView.addGestureRecognizer(tapGestureRecognizer)
    }

    // MARK: - UIPresentationController

    public override var frameOfPresentedViewInContainerView: CGRect {
        guard let containerView = presentingViewController.view else { return .zero }

        // Use the size that the presented view needs, but max out at 80% of the container.
        let height = min(
            // Try to get the computed size from the view controller
            (presentedViewController as? SheetTransitioningViewController)?
                .sizeThatFits(containerView.frame.size).height
            ?? .greatestFiniteMagnitude,
            containerView.frame.height * 0.8
        )

        return CGRect(
            origin: CGPoint(x: 0, y: containerView.frame.height - height + containerView.safeAreaInsets.bottom),
            size: CGSize(
                width: containerView.frame.width,
                height: height
            )
        )
    }

    public override func presentationTransitionWillBegin() {
        blurEffectView.alpha = 0
        containerView?.addSubview(blurEffectView)
        presentedViewController.transitionCoordinator?.animate(
            alongsideTransition: { _ in
                self.blurEffectView.alpha = 0.7
            },
            completion: { _ in }
        )
    }

    public override func dismissalTransitionWillBegin() {
        presentedViewController.transitionCoordinator?.animate(
            alongsideTransition: { _ in
                self.blurEffectView.alpha = 0
            },
            completion: { _ in
                self.blurEffectView.removeFromSuperview()
            }
        )
    }

    public override func containerViewWillLayoutSubviews() {
        super.containerViewWillLayoutSubviews()
        presentedView!.roundCorners([.topLeft, .topRight], radius: 22)
    }

    public override func containerViewDidLayoutSubviews() {
        super.containerViewDidLayoutSubviews()
        presentedView?.frame = frameOfPresentedViewInContainerView
        blurEffectView.frame = presentingViewController.view?.bounds ?? .zero
    }

    // MARK: - Private Methods

    @objc
    private func dismissController(){
        presentedViewController.dismiss(animated: true, completion: nil)
    }

}

// MARK: -

extension UIView {
  func roundCorners(_ corners: UIRectCorner, radius: CGFloat) {
      let path = UIBezierPath(
        roundedRect: bounds,
        byRoundingCorners: corners,
        cornerRadii: CGSize(width: radius, height: radius)
      )
      let mask = CAShapeLayer()
      mask.path = path.cgPath
      layer.mask = mask
  }
}
