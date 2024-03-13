import Shared
import SwiftUI

// MARK: -

public struct MoneyHomeView: View {

    // MARK: - Private Properties
    
    private var viewModel: MoneyHomeBodyModel

    @SwiftUI.State
    private var moneyHomeCardsHeight: CGFloat?
    
    // MARK: - Lifecycle

    public init(viewModel: MoneyHomeBodyModel) {
        self.viewModel = viewModel
    }

    // MARK: - View

    public var body: some View {
        ZStack(alignment: .top) {
            // Bottom of the ZStack: MoneyHome screen
            ScrollView(showsIndicators: false) {
                ZStack {
                    scrollAnimationsGeometryReader
                    VStack(alignment: .center, spacing: 0) {
                        // Header
                        HStack(alignment: .center) {
                            TabHeaderView(headline: "Home")
                                .opacity(contentHeaderOpacity)
                            Spacer()
                            ToolbarAccessoryView(viewModel: viewModel.trailingToolbarAccessoryModel)
                        }

                        // Balance
                        MoneyHomeBalanceView(model: viewModel.balanceModel)
                            .padding(.top, 40)

                        // Balance Hero
                        MoneyHomeButtonsView(viewModel: viewModel.buttonsModel)

                        // No UI between the action buttons and the tx list so show a divider
                        if viewModel.cardsModel.cards.isEmpty, viewModel.transactionsModel != nil {
                            Divider()
                                .frame(height: 1)
                                .overlay(Color.foreground10)
                                .padding(.top, 16)
                        }

                        // Cards
                        if viewModel.cardsModel.cards.count > 0, moneyHomeCardsHeight != 0 {
                            MoneyHomeCardsView(viewModel: viewModel.cardsModel, height: $moneyHomeCardsHeight)
                        }

                        // Transactions
                        if let transactionsModel = viewModel.transactionsModel {
                            ListView(model: transactionsModel)
                            viewModel.seeAllButtonModel.map { seeAllButtonModel in
                                ButtonView(model: seeAllButtonModel)
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 20)
            .refreshable {
                _ = try? await viewModel.refresh.invoke()
            }

            // Top the ZStack: header overlay, starts at 0 opacity and increases with scroll.
            HeaderOverlayView()
                .opacity(overlayHeaderOpacity)
        }
        .navigationBarHidden(true)
    }

    // MARK: - Scroll Animations

    /// The opacity of the header view shown in the main content VStack.
    /// Transitions to 0 as the view scrolls, to be replaced with the overlay header.
    @SwiftUI.State var contentHeaderOpacity = 1.f

    /// The opacity of the header view shown overlaid the main content as a header bar.
    /// Transitions to 1 as the view scrolls.
    @SwiftUI.State var overlayHeaderOpacity = 0.f

    /**
     * A `GeometryReader` that reads the `minY` value of the frame and uses it to update
     * various animated values.
     */
    private var scrollAnimationsGeometryReader: some View {
        GeometryReader { proxy -> AnyView in
            let scrollOffset = proxy.frame(in: .named("scroll")).minY

            DispatchQueue.main.async {
                contentHeaderOpacity = modulate(
                    watchedViewValue: scrollOffset,
                    watchedViewStart: 16,
                    watchedViewEnd: 0,
                    appliedViewStart: 1.0,
                    appliedViewEnd: 0,
                    limit: false
                )

                overlayHeaderOpacity = modulate(
                    watchedViewValue: scrollOffset,
                    watchedViewStart: 16,
                    watchedViewEnd: 0,
                    appliedViewStart: 0,
                    appliedViewEnd: 1.0,
                    limit: false
                )
            }
            return AnyView(EmptyView())
        }
    }

}

struct MoneyHomeView_Preview: PreviewProvider {
    static var previews: some View {
        MoneyHomeView(
            viewModel: MoneyHomeBodyModel(
                onSettings: {},
                balanceModel: MoneyAmountModel(
                    primaryAmount: "$123.75",
                    secondaryAmount: "435,228 sats"
                ),
                buttonsModel: MoneyHomeButtonsModelMoneyMovementButtonsModel(
                    sendButton: .init(enabled: true, onClick: {}),
                    receiveButton: .init(enabled: true, onClick: {}),
                    addButton: .init(enabled: false, onClick: {})
                ),
                cardsModel: .init(cards: []),
                transactionsModel: nil,
                seeAllButtonModel: nil,
                refresh: TestSuspendFunction(),
                onRefresh: {},
                isRefreshing: false
            )
        )
    }
}

class TestSuspendFunction: KotlinSuspendFunction0 {
    func invoke() async throws -> Any? { return nil }
}

// MARK: -

// Don't allow scroll views to clip their subviews or else shadows (like that used in the CardView) will be clipped.
extension UIScrollView {
  open override var clipsToBounds: Bool {
    get { false }
    set {}
  }
}

public func modulate(
    watchedViewValue: CGFloat,
    watchedViewStart: CGFloat,
    watchedViewEnd: CGFloat,
    appliedViewStart: CGFloat,
    appliedViewEnd:CGFloat,
    limit: Bool = false
) -> CGFloat {
    var result: CGFloat = 0

    let toLow = CGFloat(appliedViewStart)
    let toHigh = CGFloat(appliedViewEnd)
    let fromLow = CGFloat(watchedViewStart)
    let fromHigh = CGFloat(watchedViewEnd)
    let watchedValue = CGFloat(watchedViewValue)

    result = toLow + (((watchedValue - fromLow) / (fromHigh - fromLow)) * (toHigh - toLow))

    if limit == true {
        if toLow < toHigh {
            if result < toLow {
                return toLow
            }
            if result > toHigh {
                return toHigh
            }
        }
        else {
            if result > toLow {
                return toLow
            }
            if result < toHigh {
                return toHigh
            }
        }
    }

    return result
}
