import Shared
import SwiftUI

// MARK: -

public struct MoneyHomeView: View {

    // MARK: - Private Properties

    @SwiftUI.ObservedObject
    public var viewModelHolder: ObservableObjectHolder<MoneyHomeBodyModel>

    @SwiftUI.State
    private var viewModel: MoneyHomeBodyModel

    @SwiftUI.State
    private var moneyHomeCardsHeight: CGFloat?

    @SwiftUI.State
    private var balanceViewYOffset: CGFloat?

    // MARK: - Lifecycle

    public init(viewModel: MoneyHomeBodyModel) {
        self.viewModelHolder = .init(value: viewModel)
        self.viewModel = viewModel
    }

    // MARK: - View

    public var body: some View {
        ZStack(alignment: .top) {
            // Bottom of the ZStack: MoneyHome screen
            ScrollView(showsIndicators: false) {
                ZStack(alignment: .top) {
                    // when adding a coachmark to this screen, be sure to add a zIndex so it
                    // appropriately stays on top of all elements
                    // as it is exiting
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
                        MoneyHomeBalanceView(
                            model: viewModel.balanceModel,
                            hideBalance: viewModel.hideBalance,
                            onHideBalance: {
                                viewModel.onHideBalance()
                                // dismiss the HiddenBalanceCoachmark coachmark if it's showing
                                // since you've interacted with the feature
                                if viewModel.coachmark?.identifier == .hiddenbalancecoachmark {
                                    viewModel.coachmark?.dismiss()
                                }
                            }
                        )
                        .background(GeometryReader { geometry in
                            AnyView(Color.clear.onAppear {
                                // get the y offset of the balance view relative to the money home
                                // scroll view
                                balanceViewYOffset = geometry
                                    .frame(in: .named("money-home-scroll-view")).maxY
                            })
                        })
                        .padding(.top, 40)

                        // Balance Hero
                        MoneyHomeButtonsView(viewModel: viewModel.buttonsModel)
                            .animation(.none, value: viewModel)

                        // No UI between the action buttons and the tx list so show a divider
                        if viewModel.cardsModel.cards.isEmpty, viewModel.transactionsModel != nil {
                            Divider()
                                .frame(height: 1)
                                .overlay(Color.foreground10)
                                .padding(.top, 16)
                        }

                        // Cards
                        if viewModel.cardsModel.cards.count > 0, moneyHomeCardsHeight != 0 {
                            MoneyHomeCardsView(
                                viewModel: viewModel.cardsModel,
                                height: $moneyHomeCardsHeight
                            )
                            .animation(.none, value: viewModel)
                        }

                        // Transactions
                        if let transactionsModel = viewModel.transactionsModel {
                            ListView(model: transactionsModel, hideContent: viewModel.hideBalance)
                                .animation(.none, value: viewModel)
                            viewModel.seeAllButtonModel.map { seeAllButtonModel in
                                ButtonView(model: seeAllButtonModel)
                            }
                        }
                    }

                    if let coachmark = viewModel.coachmark, let yOffset = balanceViewYOffset {
                        CoachmarkView(model: coachmark)
                            .offset(
                                x: 0,
                                y: yOffset +
                                    4
                            ) // add a little space in addition to balance view y offset
                            .zIndex(2) // this ensures the coachmark is always above all elements
                    }
                }
                .coordinateSpace(name: "money-home-scroll-view")
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
        .onReceive(viewModelHolder.$value, perform: { vm in
            self.viewModel = vm
        })
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
                hideBalance: false,
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
                transactionsModel: ListModel(
                    headerText: "Recent Activity",
                    sections: [
                        ListGroupModel(
                            header: nil,
                            items: [
                                TransactionItemModelKt.TransactionItemModel(
                                    truncatedRecipientAddress: "1AH7...CkGJ",
                                    date: "Pending",
                                    amount: "$23.50",
                                    amountEquivalent: "45,075 sats",
                                    incoming: true,
                                    isPending: false,
                                    onClick: {}
                                ),
                                TransactionItemModelKt.TransactionItemModel(
                                    truncatedRecipientAddress: "1AH7...CkGJ",
                                    date: "Pending",
                                    amount: "$34.21",
                                    amountEquivalent: "49,000 sats",
                                    incoming: true,
                                    isPending: false,
                                    onClick: {}
                                ),
                            ],
                            style: .none,
                            headerTreatment: .secondary,
                            footerButton: nil,
                            explainerSubtext: nil
                        ),
                        ListGroupModel(
                            header: nil,
                            items: [
                                TransactionItemModelKt.TransactionItemModel(
                                    truncatedRecipientAddress: "2AH7...CkGJ",
                                    date: "Apr 6 at 12:20 pm",
                                    amount: "$90.50",
                                    amountEquivalent: "121,075 sats",
                                    incoming: false,
                                    isPending: false,
                                    onClick: {}
                                ),
                            ],
                            style: .none,
                            headerTreatment: .secondary,
                            footerButton: nil,
                            explainerSubtext: nil
                        ),
                    ]
                ),
                seeAllButtonModel: ButtonModel(
                    text: "See All",
                    isEnabled: true,
                    isLoading: false,
                    leadingIcon: nil,
                    treatment: .secondary,
                    size: .footer,
                    testTag: nil,
                    onClick: StandardClick {}
                ),
                coachmark: nil,
                refresh: TestSuspendFunction(),
                onRefresh: {},
                onHideBalance: {},
                isRefreshing: false,
                badgedSettingsIcon: false,
                onOpenPriceDetails: {}
            )
        )

        MoneyHomeView(
            viewModel: MoneyHomeBodyModel(
                hideBalance: false,
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
                transactionsModel: ListModel(
                    headerText: "Recent Activity",
                    sections: [
                        ListGroupModel(
                            header: nil,
                            items: [
                                TransactionItemModelKt.TransactionItemModel(
                                    truncatedRecipientAddress: "1AH7...CkGJ",
                                    date: "Pending",
                                    amount: "$23.50",
                                    amountEquivalent: "45,075 sats",
                                    incoming: true,
                                    isPending: false,
                                    onClick: {}
                                ),
                                TransactionItemModelKt.TransactionItemModel(
                                    truncatedRecipientAddress: "1AH7...CkGJ",
                                    date: "Pending",
                                    amount: "$34.21",
                                    amountEquivalent: "49,000 sats",
                                    incoming: true,
                                    isPending: false,
                                    onClick: {}
                                ),
                            ],
                            style: .none,
                            headerTreatment: .secondary,
                            footerButton: nil,
                            explainerSubtext: nil
                        ),
                        ListGroupModel(
                            header: nil,
                            items: [
                                TransactionItemModelKt.TransactionItemModel(
                                    truncatedRecipientAddress: "2AH7...CkGJ",
                                    date: "Apr 6 at 12:20 pm",
                                    amount: "$90.50",
                                    amountEquivalent: "121,075 sats",
                                    incoming: false,
                                    isPending: false,
                                    onClick: {}
                                ),
                            ],
                            style: .none,
                            headerTreatment: .secondary,
                            footerButton: nil,
                            explainerSubtext: nil
                        ),
                    ]
                ),
                seeAllButtonModel: ButtonModel(
                    text: "See All",
                    isEnabled: true,
                    isLoading: false,
                    leadingIcon: nil,
                    treatment: .secondary,
                    size: .footer,
                    testTag: nil,
                    onClick: StandardClick {}
                ),
                coachmark: nil,
                refresh: TestSuspendFunction(),
                onRefresh: {},
                onHideBalance: {},
                isRefreshing: false,
                badgedSettingsIcon: true,
                onOpenPriceDetails: {}
            )
        ).previewDisplayName("settings badged")
    }
}

class TestSuspendFunction: KotlinSuspendFunction0 {
    func invoke() async throws -> Any? { return nil }
}

// MARK: -

// Don't allow scroll views to clip their subviews or else shadows (like that used in the CardView)
// will be clipped.
extension UIScrollView {
    override open var clipsToBounds: Bool {
        get { false }
        set {}
    }
}

public func modulate(
    watchedViewValue: CGFloat,
    watchedViewStart: CGFloat,
    watchedViewEnd: CGFloat,
    appliedViewStart: CGFloat,
    appliedViewEnd: CGFloat,
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
        } else {
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
