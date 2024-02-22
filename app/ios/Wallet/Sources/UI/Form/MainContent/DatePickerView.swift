import Shared
import SwiftUI

struct DatePickerView: View {
    private enum Metrics {
        static let height = 56.f
        static let cornerRadius = 32.f
    }
    
    private let viewModel: FormMainContentModelDatePicker
    
    init(viewModel: FormMainContentModelDatePicker) {
        self.viewModel = viewModel
    }
    
    @SwiftUI.State
    private var datePickerPresentation: DatePickerPopoverState? = nil
        
    var body: some View {
        VStack(spacing: 8) {
            if let title = viewModel.title {
                ModeledText(model: .standard(title, font: .title2))
            }
            
            Button {
                datePickerPresentation = DatePickerPopoverState(
                    initialDate: viewModel.fieldModel.value?.toNativeDate() ?? Date.now,
                    onDateChanged: { newDate in
                        datePickerPresentation = nil
                        viewModel.fieldModel.onValueChange(
                            LocalDateExtKt.toLocalDate(newDate)
                        )
                    })
            } label: {
                Text(viewModel.fieldModel.valueStringRepresentation)
                    .frame(height: Metrics.height)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal)
                    .background(
                        RoundedRectangle(cornerRadius: Metrics.cornerRadius)
                            .fill(Color.foreground10)
                    )
                    .foregroundColor(.foreground)
                    .tint(.foreground)
            }
            .datePickerPopover(popoverState: $datePickerPresentation)
            
        }
    }
}

private struct DatePickerPopoverState {
    let initialDate: Date
    let onDateChanged: (Date) -> Void
}

private struct DatePickerDialogContent: View {
    
    @Binding
    private var selectedDate: Date
    
    init(popoverState: DatePickerPopoverState) {
        var date = popoverState.initialDate
        _selectedDate = Binding(get: {
            date
        }, set: { newDate in
            date = newDate
            popoverState.onDateChanged(newDate)
        })
    }
 
    var body: some View {
        DatePicker(
            "Please Choose a Date",
            selection: $selectedDate,
            displayedComponents: .date
        )
        .labelsHidden()
        .datePickerStyle(.graphical)
        .padding(.top)
    }
}

private extension View {
    /**
     Displays `content` as popover on both iPadOS and iOS.
     */
    func datePickerPopover(
        popoverState: Binding<DatePickerPopoverState?>
    ) -> some View {
        self.background(
            DatePickerAnchorView(popoverState: popoverState)
        )
    }
}

private struct DatePickerAnchorView: UIViewControllerRepresentable {
    
    @Binding
    var popoverState: DatePickerPopoverState?
    
    func makeUIViewController(context: Context) -> UIViewController {
        let anchorController = UIViewController()
        anchorController.view.backgroundColor = UIColor.clear
        anchorController.view.bounds.size = .zero
        return anchorController
    }
    
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        let presentedContentController = uiViewController.presentedViewController as? UIHostingController<DatePickerDialogContent>
        if let popoverState {
            // Make sure the keyboard is closed so we have enough space to show the whole date picker
            UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
            
            if let presentedContentController {
                // We'll just update the currently presented controller's content
                presentedContentController.rootView = DatePickerDialogContent(popoverState: popoverState)
            } else {
                // We need to create and configure a new controller that holds the DatePicker
                let contentController = UIHostingController(rootView: DatePickerDialogContent(popoverState: popoverState))
                contentController.modalPresentationStyle = .popover
                
                // The `popoverPresentationController` never returns `nil` as long as `modalPresentationStyle = .popover`,
                //   but we want to make sure the date picker is presented even if this invariant is broken.
                if let popover = contentController.popoverPresentationController {
                    popover.sourceView = uiViewController.view
                    popover.sourceRect = uiViewController.view.bounds
                    popover.delegate = context.coordinator
                }
                
                contentController.preferredContentSize = contentController.sizeThatFits(
                    in: UIView.layoutFittingExpandedSize
                )
                uiViewController.present(contentController, animated: true)
            }
        } else if let presentedContentController {
            presentedContentController.dismiss(animated: true)
        }
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator(
            onDismiss: {
                popoverState = nil
            }
        )
    }
    
    static func dismantleUIViewController(_ uiViewController: UIViewController, coordinator: Coordinator) {
        uiViewController.presentedViewController?.dismiss(animated: true)
    }
    
    class Coordinator: NSObject, UIPopoverPresentationControllerDelegate {
        private let onDismiss: () -> Void
        
        init(onDismiss: @escaping () -> Void) {
            self.onDismiss = onDismiss
        }
        
        func presentationControllerDidDismiss(_ presentationController: UIPresentationController) {
            onDismiss()
        }
        
        func adaptivePresentationStyle(
            for controller: UIPresentationController,
            traitCollection: UITraitCollection
        ) -> UIModalPresentationStyle {
            // This way we tell iOS to present as a popover, by effectively disabling adaptive presentation style.
            return .none
        }
    }
}

#Preview {
    DatePickerView(
        viewModel: FormMainContentModelDatePicker(
            title: "Date field preview",
            fieldModel: DatePickerModel(
                valueStringRepresentation: "",
                value: nil,
                onValueChange: { _ in }
            )
        )
    )
}

#Preview {
    DatePickerView(
        viewModel: FormMainContentModelDatePicker(
            title: "Date field preview",
            fieldModel: DatePickerModel(
                valueStringRepresentation: Date.now.formatted(date: .long, time: .omitted),
                value: LocalDateExtKt.toLocalDate(Date.now),
                onValueChange: { _ in }
            )
        )
    )
}
