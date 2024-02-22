import UIKit

// MARK: -

/**
 * Top header / app bar component that allows displaying arbitrary leading, middle, and/or
 * trailing contents as slots.
 *
 *  ```
 * ┌────────────────────────────────────────────┐
 * │ ┌────────────┐┌────────────┐┌────────────┐ │
 * │ │  leading   ││   middle   ││  trailing  │ │
 * │ └────────────┘└────────────┘└────────────┘ │
 * └────────────────────────────────────────────┘
 * ```
 *
 */
public final class HeaderView<
    LeadingView: ModelRepresentableView,
    MiddleView: ModelRepresentableView,
    TrailingView: ModelRepresentableView
>: UIStackView {

    // MARK: - Private Properties

    private let leadingView = LeadingView()
    private let middleView = MiddleView()
    private let trailingView = TrailingView()

    // MARK: - Life Cycle

    public init() {
        super.init(frame: .zero)

        [leadingView, middleView, trailingView].forEach {
            addArrangedSubview($0)
            $0.translatesAutoresizingMaskIntoConstraints = false
        }

        axis = .horizontal
        distribution = .fill
        alignment = .center

        leadingView.setContentHuggingPriority(.defaultHigh, for: .horizontal)
        trailingView.setContentHuggingPriority(.defaultHigh, for: .horizontal)
    }

    required init(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    // MARK: - UIView

    public override func layoutSubviews() {
        leadingView.sizeToFit()
        trailingView.sizeToFit()

        // If we only have one side filled, artificially fill the other side proportionally to make sure the
        // middle is centered, if middle content was provided
        if leadingView.frame.isEmpty || trailingView.frame.isEmpty {
            NSLayoutConstraint.activate([
                leadingView.widthAnchor.constraint(equalTo: trailingView.widthAnchor)
            ])
        }
    }

    // MARK: - Public Methods

    public func apply(model: Model) {
        backgroundColor = .clear
        if let leadingModel = model.leadingModel {
            leadingView.apply(model: leadingModel)
        }
        if let middleModel = model.middleModel {
            middleView.apply(model: middleModel)
        }
        if let trailingModel = model.trailingModel {
            trailingView.apply(model: trailingModel)
        }
    }

}

// MARK: -

extension HeaderView {

    public struct Model {
        let leadingModel: LeadingView.Model?
        let middleModel: MiddleView.Model?
        let trailingModel: TrailingView.Model?

        public init(
            leadingModel: LeadingView.Model? = nil,
            middleModel: MiddleView.Model? = nil,
            trailingModel: TrailingView.Model? = nil
        ) {
            self.leadingModel = leadingModel
            self.middleModel = middleModel
            self.trailingModel = trailingModel
        }
    }

}
