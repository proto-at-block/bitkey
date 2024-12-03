
import Shared
import SwiftUI

private enum ObjectKey {
    static var ctl = malloc(1)!
}

struct ComposableRenderedModelView: View {
    var model: ComposableRenderedModel
    @SwiftUI.State var height: CGFloat = 1

    init(model: ComposableRenderedModel) {
        self.model = model
    }

    var body: some View {
        ComposableRenderedModelViewAdapter(
            model: model,
            height: $height
        )
        .frame(height: height)
    }
}

private struct ComposableRenderedModelViewAdapter: UIViewControllerRepresentable {
    var model: ComposableRenderedModel
    @Binding var height: CGFloat

    func makeUIViewController(context _: Context) -> UIViewController {
        let controller = ComposableRenderedModelUiController(
            initialModel: model,
            onHeightChanged: { self.height = CGFloat(truncating: $0) / UIScreen.main.scale }
        )
        objc_setAssociatedObject(
            controller.viewController,
            ObjectKey.ctl,
            controller,
            .OBJC_ASSOCIATION_RETAIN_NONATOMIC
        )
        return controller.viewController
    }

    func updateUIViewController(_ controller: UIViewController, context _: Context) {
        let object = objc_getAssociatedObject(controller, ObjectKey.ctl)
        (object as? ComposableRenderedModelUiController)?.update(model: model)
    }
}
