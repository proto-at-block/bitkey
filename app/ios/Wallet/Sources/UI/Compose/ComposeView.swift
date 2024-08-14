import Shared
import SwiftUI

struct ComposeView: View {

    @SwiftUI.ObservedObject
    public var viewModelHolder: ObservableObjectHolder<ComposeBodyModel>

    private let bodyUiController: ComposeBodyModelUiController

    init(bodyModel: ComposeBodyModel) {
        self.viewModelHolder = .init(value: bodyModel)
        self.bodyUiController = ComposeBodyModelUiController(initialModel: bodyModel)
    }

    func update(bodyModel: ComposeBodyModel) {
        self.viewModelHolder.value = bodyModel
    }

    var body: some View {
        ComposeViewAdapter(
            bodyUiController: bodyUiController
        )
        .onReceive(viewModelHolder.$value, perform: { vm in
            self.bodyUiController.updateBodyModel(bodyModel: vm)
        })
    }
}

struct ComposeViewAdapter: UIViewControllerRepresentable {
    private let bodyUiController: ComposeBodyModelUiController

    init(bodyUiController: ComposeBodyModelUiController) {
        self.bodyUiController = bodyUiController
    }

    func makeUIViewController(context _: Context) -> UIViewController {
        return bodyUiController.viewController
    }

    func updateUIViewController(_: UIViewController, context _: Context) {}
}
