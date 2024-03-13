import Foundation
import Shared
import SwiftUI

// MARK: -

/**
 * Used to wrap any SwiftUI view of a KMP screen model into a view controller and to add a tab bar as necessary
 */
public class SwiftUIWrapperViewController<T: View>: UIHostingController<WrapperView<T>> {

    public override var navigationController: UINavigationController? {
        nil
    }

    public init(_ wrappedView: T, screenModel: ScreenModel) {
        log { "Initializing \(wrappedView)" }
        super.init(
            rootView: WrapperView(wrappedView: wrappedView, screenModel: screenModel)
        )
    }

    required dynamic init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    public func updateWrappedView(_ wrappedView: T, screenModel: ScreenModel) {
        rootView = WrapperView(wrappedView: wrappedView, screenModel: screenModel)
    }

    public func updateWrappedView(_ update: (_ view: T) -> Void) {
        update(rootView.wrappedView)
    }

}

// MARK: -

public struct WrapperView<T: View>: View {

    // MARK: - Private Properties

    private var screenModel: ScreenModel
    let wrappedView: T

    // MARK: - Life Cycle

    init(wrappedView: T, screenModel: ScreenModel) {
        self.screenModel = screenModel
        self.wrappedView = wrappedView
    }

    public var body: some View {
        VStack {
            wrappedView
            screenModel.tabBar.map { TabBarView(viewModel: $0) }
        }
        .safeAreaInset(edge: .top) {
            if let statusBannerModel = screenModel.statusBannerModel {
                StatusBannerView(viewModel: statusBannerModel)
            }
        }
    }

}

// MARK: -

private struct StatusBannerView: View {

    let viewModel: StatusBannerModel

    var body: some View {
        VStack {
            HStack(alignment: .center, spacing: 0) {
                ModeledText(model: .standard(viewModel.title, font: .body3Medium, textAlignment: nil, textColor: .warningForeground))
                    .padding(.bottom, 1) // This makes the icon centered on the actual text
                if viewModel.onClick != nil {
                    Image(uiImage: .smallIconInformationFilled)
                        .resizable()
                        .frame(iconSize: .xsmall)
                        .foregroundColor(.warningForeground)
                        .padding(.leading, 4)
                }
            }
            viewModel.subtitle.map { subtitle in
                ModeledText(model: .standard(subtitle, font: .body4Regular, textAlignment: .center, textColor: .warningForeground))
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 12)
        .padding(.bottom, 16)
        .background(Color.warning)
        .onTapGesture(perform: viewModel.onClick ?? {})
    }

}

// MARK: -

private struct TabBarView: View {

    let viewModel: TabBarModel

    var body: some View {
        VStack {
            Divider()
                .frame(height: 1)
                .overlay(Color.foreground10)
            Spacer()
            HStack {
                TabBarItemView(viewModel: viewModel.firstItem)
                TabBarItemView(viewModel: viewModel.secondItem)
            }
            Spacer()
        }
        .frame(height: DesignSystemMetrics.tabBarHeight)
        .background(.background)
    }

}

// MARK: -

private struct TabBarItemView: View {

    let viewModel: TabBarItem

    var body: some View {
        Button {
            viewModel.onClick()
        } label: {
            Image(uiImage: viewModel.icon.uiImage)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .tint(viewModel.selected ? .foreground : .foreground30)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .ifNonnull(viewModel.testTag) { view, testTag in
            view.accessibilityIdentifier(testTag)
         }
    }

}
