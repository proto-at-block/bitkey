import Foundation
import Shared
import SwiftUI

public struct FormView: View {

    // MARK: - Private Properties

    private var viewModel: FormBodyModel

    @SwiftUI.State
    private var safariUrl: URL?
    @SwiftUI.State private var isShowingToast = false

    // MARK: - Life Cycle

    public init(viewModel: FormBodyModel) {
        self.viewModel = viewModel
    }

    // MARK: - View

    public var body: some View {
        GeometryReader { reader in
            VStack(alignment: .leading) {
                ScrollView(showsIndicators: false) {
                    FormContentView(
                        toolbarModel: viewModel.toolbar,
                        headerModel: viewModel.header,
                        mainContentList: viewModel.mainContentList,
                        renderContext: .screen
                    )
                }
                .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
                .padding(.bottom, DesignSystemMetrics.verticalPadding)
                .padding(
                    .top,
                    reader.safeAreaInsets.top == 0 ? DesignSystemMetrics.verticalPadding : 0
                )
                .background(Color.background)

                Spacer()

                // If we have a primary or secondary button defined in the view model, we want to
                // show a FooterView that has a linear gradient background.
                // On small displays, this is particularly helpful so that the buttons don't appear
                // to chop off the contents of the FormView, and the top color provides a
                // gentle transition to the solid background color.
                //
                // We add padding individually to the ScrollView and FormFooterView instead of the
                // VStack so that we can apply them before the linear background to fill up to the
                // screen's edges.
                if viewModel.primaryButton != nil || viewModel.secondaryButton != nil {
                    FormFooterView(viewModel: viewModel)
                        .padding(.top, DesignSystemMetrics.verticalPadding)
                        .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
                        .padding(.bottom, DesignSystemMetrics.verticalPadding)
                        .background(
                            LinearGradient(
                                gradient: .init(
                                    stops: [
                                        .init(
                                            color: Color(
                                                hue: 1.0,
                                                saturation: 0.0,
                                                brightness: 1.0,
                                                opacity: 0.0
                                            ),
                                            location: 0
                                        ),
                                        .init(color: Color.background, location: 0.15),
                                    ]
                                ),
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                }
            }
            .navigationBarHidden(true)
            .fullScreenCover(item: $safariUrl) { url in
                SafariView(url: url)
                    .ignoresSafeArea()
            }
        }
        .onAppear {
            self.viewModel.onLoaded(
                NativeBrowserNavigator(openSafariView: { self.safariUrl = URL(string: $0) })
            )
        }
    }

}

struct FormView_Previews: PreviewProvider {

    static var previews: some View {
        FormView(
            viewModel: ListFormBodyModelKt.ListFormBodyModel(
                onBack: {},
                toolbarTitle: "Activity",
                listGroups: [
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
                ],
                id: nil
            )
        )
    }

}
