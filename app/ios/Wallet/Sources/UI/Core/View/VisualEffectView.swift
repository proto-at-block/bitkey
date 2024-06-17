import SwiftUI

// MARK: -

public struct VisualEffectView: UIViewRepresentable {

    var effect: UIVisualEffect?

    public init(effect: UIVisualEffect? = nil) {
        self.effect = effect
    }

    public func makeUIView(context _: UIViewRepresentableContext<Self>) -> UIVisualEffectView {
        UIVisualEffectView()
    }

    public func updateUIView(
        _ uiView: UIVisualEffectView,
        context _: UIViewRepresentableContext<Self>
    ) {
        uiView.effect = effect
    }

}
