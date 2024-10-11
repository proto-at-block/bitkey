import Shared
import SwiftUI

struct ComposableRenderedView: View {

    @SwiftUI.ObservedObject
    public var viewModelHolder: ObservableObjectHolder<ComposableRenderedModel>

    private let bodyUiController: ComposableRenderedModelUiController

    init(bodyModel: ComposableRenderedModel) {
        self.viewModelHolder = .init(value: bodyModel)
        self.bodyUiController = ComposableRenderedModelUiController(initialModel: bodyModel)
    }

    func update(bodyModel: ComposableRenderedModel) {
        self.viewModelHolder.value = bodyModel
    }

    var body: some View {
        ComposableRenderedModelViewAdapter(
            bodyUiController: bodyUiController
        )
        .onReceive(viewModelHolder.$value, perform: { vm in
            self.bodyUiController.updateBodyModel(model: vm)
        })
    }
}

struct ComposableRenderedModelViewAdapter: UIViewControllerRepresentable {
    private let bodyUiController: ComposableRenderedModelUiController

    init(bodyUiController: ComposableRenderedModelUiController) {
        self.bodyUiController = bodyUiController
    }

    func makeUIViewController(context _: Context) -> UIViewController {
        return bodyUiController.viewController
    }

    func updateUIViewController(_: UIViewController, context _: Context) {}
}
