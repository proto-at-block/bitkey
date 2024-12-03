import Shared
import SwiftUI

struct ComposableRenderedScreenView: View {

    @SwiftUI.ObservedObject
    public var viewModelHolder: ObservableObjectHolder<ScreenModel>

    private let bodyUiController: ComposableRenderedScreenModelUiController

    init(screenModel: ScreenModel) {
        self.viewModelHolder = .init(value: screenModel)
        self.bodyUiController = ComposableRenderedScreenModelUiController(initialModel: screenModel)
    }

    func update(bodyModel: ScreenModel) {
        self.viewModelHolder.value = bodyModel
    }

    var body: some View {
        ComposableRenderedScreenModelViewAdapter(
            bodyUiController: bodyUiController
        )
        .onReceive(viewModelHolder.$value, perform: { vm in
            self.bodyUiController.updateBodyModel(model: vm)
        })
    }
}

private struct ComposableRenderedScreenModelViewAdapter: UIViewControllerRepresentable {
    private let bodyUiController: ComposableRenderedScreenModelUiController

    init(bodyUiController: ComposableRenderedScreenModelUiController) {
        self.bodyUiController = bodyUiController
    }

    func makeUIViewController(context _: Context) -> UIViewController {
        return bodyUiController.viewController
    }

    func updateUIViewController(_: UIViewController, context _: Context) {}
}
