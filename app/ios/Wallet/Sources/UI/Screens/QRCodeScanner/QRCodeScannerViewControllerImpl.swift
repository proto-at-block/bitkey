import AVFoundation
import UIKit

// MARK: -

public final class QRCodeScannerViewControllerImpl: UIViewController, QRCodeScannerViewController {

    // MARK: - Private Properties

    private let mainView: View
    private var presenter: QRCodeScannerPresenter
    private let previewLayer: AVCaptureVideoPreviewLayer

    // MARK: - Life Cycle

    public init(
        presenter: QRCodeScannerPresenter
    ) {
        self.presenter = presenter

        previewLayer = AVCaptureVideoPreviewLayer(session: presenter.captureSession)
        previewLayer.videoGravity = .resizeAspectFill

        self.mainView = View(previewLayer: previewLayer)

        super.init(nibName: nil, bundle: nil)

        self.presenter.viewController = self
    }

    @available(*, unavailable)
    required init?(coder _: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    // MARK: - UIViewController

    override public var preferredStatusBarStyle: UIStatusBarStyle {
        return .lightContent
    }

    override public func loadView() {
        view = mainView
    }

    override public func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        presenter.viewWillAppear()
    }

    override public func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        presenter.viewDidAppear()
    }

    override public func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        presenter.viewWillDisappear()
    }

    override public func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        mainView.applyMask()
    }

    // MARK: - QRCodeScannerPresenterViewController

    public func apply(model: QRCodeScannerViewControllerModel) {
        mainView.apply(model)
    }

    public func setBackgroundColor(_ color: UIColor) {
        mainView.backgroundColor = color
    }

}

// MARK: -

private extension QRCodeScannerViewControllerImpl {

    final class View: UIView {

        // MARK: - Private Types

        private enum Metrics {
            static let reticleCornerRadius = 40.f
            static let reticleEdgeToViewWidthRatio = 0.78
        }

        // MARK: - Private Properties

        private let headerView = HeaderView<ModeledButton, UILabel, ModeledButton>()
        private let reticleView = UIView()
        private let reticleDescriptionLabel = UILabel()
        private let primaryButton = ModeledButton()
        private let secondaryButton = ModeledButton()
        private let callToActionStackView = {
            let stackView = UIStackView()
            stackView.axis = .vertical
            stackView.spacing = 16
            stackView.alignment = .fill
            return stackView
        }()

        private let overlayView = UIView()
        private let previewLayer: AVCaptureVideoPreviewLayer?

        // MARK: - Life Cycle

        init(previewLayer: AVCaptureVideoPreviewLayer?) {
            self.previewLayer = previewLayer

            super.init(frame: .zero)

            if let previewLayer {
                layer.addSublayer(previewLayer)
            }

            reticleView.backgroundColor = .clear
            reticleView.layer.cornerRadius = Metrics.reticleCornerRadius

            addSubview(overlayView)

            for item in [primaryButton, secondaryButton] {
                item.translatesAutoresizingMaskIntoConstraints = false
            }

            callToActionStackView.addArrangedSubview(primaryButton)
            callToActionStackView.addArrangedSubview(secondaryButton)

            for item in [headerView, reticleView, reticleDescriptionLabel, callToActionStackView] {
                item.translatesAutoresizingMaskIntoConstraints = false
                addSubview(item)
            }

            // Start the view out with a black background. If the camera is available, it will be
            // updated by the presenter to clear right before the capture session starts
            backgroundColor = .black
        }

        @available(*, unavailable)
        required init(coder _: NSCoder) {
            fatalError("init(coder:) has not been implemented")
        }

        // MARK: - UIView

        override func layoutSubviews() {
            overlayView.frame = bounds
            previewLayer?.frame = bounds

            setUpConstraints()
        }

        // MARK: - Internal Methods

        func apply(_ model: QRCodeScannerViewControllerModel) {
            reticleView.layer.borderColor = model.reticle.borderColor.cgColor
            reticleView.layer.borderWidth = model.reticle.borderWidth
            reticleDescriptionLabel.applyOrHide(model: model.reticle.descriptionLabel)

            overlayView.backgroundColor = model.overlayBackgroundColor

            headerView.apply(model: model.headerModel)
            primaryButton.applyOrHide(model: model.primaryButtonModel)
            secondaryButton.applyOrHide(model: model.secondaryButtonModel)

            setNeedsLayout()
        }

        func applyMask() {
            guard reticleView.frame.width > 0 else {
                return
            }

            let maskLayer = CAShapeLayer()
            let path = CGMutablePath()
            path.addRect(overlayView.frame)
            path.addRoundedRect(
                in: reticleView.frame,
                cornerWidth: Metrics.reticleCornerRadius,
                cornerHeight: Metrics.reticleCornerRadius
            )

            maskLayer.path = path
            maskLayer.cornerRadius = Metrics.reticleCornerRadius
            maskLayer.fillRule = .evenOdd

            overlayView.layer.mask = maskLayer
        }

        // MARK: - Private Methods

        private func setUpConstraints() {
            let reticleSideLength = bounds.width * Metrics.reticleEdgeToViewWidthRatio

            NSLayoutConstraint.activate([
                headerView.topAnchor.constraint(equalTo: topAnchor, constant: safeAreaInsets.top),
                headerView.heightAnchor.constraint(equalToConstant: 72),
                headerView.leftAnchor.constraint(equalTo: leftAnchor, constant: 20),
                headerView.rightAnchor.constraint(equalTo: rightAnchor, constant: -20),

                reticleView.widthAnchor.constraint(equalToConstant: reticleSideLength),
                reticleView.heightAnchor.constraint(equalToConstant: reticleSideLength),
                reticleView.centerXAnchor.constraint(equalTo: centerXAnchor),
                reticleView.centerYAnchor.constraint(equalTo: centerYAnchor),

                reticleDescriptionLabel.topAnchor.constraint(
                    equalTo: reticleView.bottomAnchor,
                    constant: 16
                ),
                reticleDescriptionLabel.centerXAnchor.constraint(equalTo: centerXAnchor),

                secondaryButton.heightAnchor.constraint(equalToConstant: 52),
                primaryButton.heightAnchor.constraint(equalToConstant: 52),

                callToActionStackView.leadingAnchor.constraint(
                    equalTo: leadingAnchor,
                    constant: 20
                ),
                callToActionStackView.trailingAnchor.constraint(
                    equalTo: trailingAnchor,
                    constant: -20
                ),
                callToActionStackView.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -60),
            ])
        }

    }

}
