import Shared
import SwiftUI

// MARK: -

public struct LiteMoneyHomeView: View {
    // MARK: - Private Properties

    private var viewModel: LiteMoneyHomeBodyModel

    @SwiftUI.State
    private var moneyHomeCardsHeight: CGFloat?

    // MARK: - Lifecycle

    public init(viewModel: LiteMoneyHomeBodyModel) {
        self.viewModel = viewModel
    }

    // MARK: - View

    public var body: some View {
        ZStack(alignment: .top) {
            // Bottom of the ZStack: MoneyHome screen
            ScrollView(showsIndicators: false) {
                ZStack {
                    VStack(alignment: .center, spacing: 0) {
                        // Header
                        HStack(alignment: .center) {
                            TabHeaderView(headline: "Home")
                            Spacer()
                            ToolbarAccessoryView(viewModel: viewModel.trailingToolbarAccessoryModel)
                        }

                        Spacer(minLength: 40)

                        // Cards
                        if viewModel.cardsModel.cards.count > 0, moneyHomeCardsHeight != 0 {
                            MoneyHomeCardsView(
                                viewModel: viewModel.cardsModel,
                                height: $moneyHomeCardsHeight
                            )
                        }

                        if let buttonModel = viewModel
                            .buttonsModel as? MoneyHomeButtonsModelSingleButtonModel
                        {
                            ButtonView(model: buttonModel.button).padding(.vertical, 10)
                        }
                    }
                }
            }
            .padding(.horizontal, 20)
        }
        .navigationBarHidden(true)
    }
}

// MARK: -

struct LiteMoneyHomeView_Preview: PreviewProvider {
    static var previews: some View {
        LiteMoneyHomeView(
            viewModel: LiteMoneyHomeBodyModel(
                onSettings: {},
                buttonModel: MoneyHomeButtonsModelSingleButtonModel(
                    onSetUpBitkeyDevice: {}
                ),
                protectedCustomers: [
                    ProtectedCustomer(
                        relationshipId: "",
                        alias: "bob",
                        roles: ["SOCIAL_RECOVERY_CONTACT"]
                    ),
                ],
                badgedSettingsIcon: false,
                onProtectedCustomerClick: { _ in },
                onBuyOwnBitkeyClick: {},
                onAcceptInviteClick: {}
            )
        ).previewDisplayName("protected customer")
        LiteMoneyHomeView(
            viewModel: LiteMoneyHomeBodyModel(
                onSettings: {},
                buttonModel: MoneyHomeButtonsModelSingleButtonModel(
                    onSetUpBitkeyDevice: {}
                ),
                protectedCustomers: [],
                badgedSettingsIcon: false,
                onProtectedCustomerClick: { _ in },
                onBuyOwnBitkeyClick: {},
                onAcceptInviteClick: {}
            )
        ).previewDisplayName("accept invite")
        LiteMoneyHomeView(
            viewModel: LiteMoneyHomeBodyModel(
                onSettings: {},
                buttonModel: MoneyHomeButtonsModelSingleButtonModel(
                    onSetUpBitkeyDevice: {}
                ),
                protectedCustomers: [
                    ProtectedCustomer(
                        relationshipId: "",
                        alias: "bob",
                        roles: ["SOCIAL_RECOVERY_CONTACT"]
                    ),
                ],
                badgedSettingsIcon: true,
                onProtectedCustomerClick: { _ in },
                onBuyOwnBitkeyClick: {},
                onAcceptInviteClick: {}
            )
        ).previewDisplayName("coachmark badged")
    }
}
