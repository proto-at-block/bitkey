import Foundation
import Shared
import SwiftUI

// MARK: -

/**
 * Used to wrap any SwiftUI view of a KMP screen model into a view controller
 */
public class SwiftUIWrapperViewController<T: View>: UIHostingController<WrapperView<T>> {

    override public var navigationController: UINavigationController? {
        nil
    }

    public init(_ wrappedView: T, screenModel: ScreenModel) {
        log { "Initializing \(wrappedView)" }
        super.init(rootView: WrapperView(wrappedView: wrappedView, screenModel: screenModel))
    }

    @available(*, unavailable)
    dynamic required init?(coder _: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    public func updateWrappedView(_ wrappedView: T, screenModel: ScreenModel) {
        self.rootView.updateWrappedView(wrappedView, screenModel: screenModel)
    }

    public func updateWrappedView(_ update: (_ view: T) -> Void) {
        update(rootView.wrappedView)
    }

}

// MARK: -

public class ObservableObjectHolder<T>: ObservableObject {
    @Published var value: T

    init(value: T) {
        self.value = value
    }
}

public struct WrapperView<T: View>: View {

    // MARK: - Private Properties

    @ObservedObject var wrappedViewHolder: ObservableObjectHolder<T>
    @ObservedObject var screenModelHolder: ObservableObjectHolder<ScreenModel>
    @SwiftUI.State private var isShowingToast = false

    var wrappedView: T {
        wrappedViewHolder.value
    }

    var screenModel: ScreenModel {
        screenModelHolder.value
    }

    // MARK: - Life Cycle

    init(wrappedView: T, screenModel: ScreenModel) {
        self.wrappedViewHolder = ObservableObjectHolder(value: wrappedView)
        self.screenModelHolder = ObservableObjectHolder(value: screenModel)
    }

    public var body: some View {
        VStack {
            wrappedView
        }
        // Check for uniqueness of toast id, otherwise we're trying showing a new toast so reset the
        // state flag
        .onChange(of: screenModel.toastModel?.id) { _ in
            self.isShowingToast = false
        }
        .safeAreaInset(edge: .top) {
            if let statusBannerModel = screenModel.statusBannerModel {
                StatusBannerView(viewModel: statusBannerModel)
            }
        }
        .animation(.easeInOut(duration: 0.4), value: screenModel.statusBannerModel)

        // Show a toast if we've got oen
        if let toast = screenModel.toastModel {
            // This entire animation block is very fragile, attempt to improve at your own risk
            if !isShowingToast {
                ToastView(model: toast)
                    .transition(.move(edge: .bottom))
                    .animation(.smooth.delay(0.25))
                    .onAppear {
                        // Delay and then set the state flag to trigger the dismiss animation in the
                        // else block below
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5.seconds) {
                            self.isShowingToast = true
                        }
                    }
            } else {
                ToastView(model: toast)
                    // we don't really fade out here, but need to change a value to trigger the
                    // animation
                    .opacity(0.0)
                    .transition(.moveOutToBottom.animation(.easeOut))
            }
        }
    }

    public func updateWrappedView(_ wrappedView: T, screenModel: ScreenModel) {
        self.wrappedViewHolder.value = wrappedView
        self.screenModelHolder.value = screenModel
    }
}

// MARK: -

private struct StatusBannerView: View {

    let viewModel: StatusBannerModel

    var body: some View {
        VStack {
            HStack(alignment: .center, spacing: 0) {
                ModeledText(model: .standard(
                    viewModel.title,
                    font: .body3Medium,
                    textAlignment: nil,
                    textColor: .warningForeground
                ))
                .padding(.bottom, 1) // This makes the icon centered on the actual text
                if viewModel.onClick != nil {
                    Image(uiImage: .smallIconInformationFilled)
                        .resizable()
                        .frame(iconSize: .XSmall())
                        .foregroundColor(.warningForeground)
                        .padding(.leading, 4)
                }
            }
            viewModel.subtitle.map { subtitle in
                ModeledText(model: .standard(
                    subtitle,
                    font: .body4Regular,
                    textAlignment: .center,
                    textColor: .warningForeground
                ))
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 12)
        .padding(.bottom, 16)
        .background(Color.warning)
        .onTapGesture(perform: viewModel.onClick ?? {})
    }

}
