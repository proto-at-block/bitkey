import Foundation
import Shared
import SwiftUI

// MARK: -

public struct MoneyHomeCardsView: View {

    // MARK: - Public Properties

    public var viewModel: MoneyHomeCardsModel

    @Binding
    public var height: CGFloat?

    // MARK: - View

    public var body: some View {
        VStack {
            ForEach(viewModel.cards, id: \.self) { cardModel in
                CardView(
                    viewModel: cardModel,
                    // If there's only one card, bind the height of this view to the height of the
                    // card so that
                    // if it changes (and goes to 0), we can notify MoneyHomeView to stop showing
                    // this view
                    withHeight: viewModel.cards.count == 1 ? $height : .constant(nil)
                )
                .padding(.bottom, 24)
            }
        }
    }

}
