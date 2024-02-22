import SwiftUI

public struct TabHeaderView: View {

    public var headline: String

    public var body: some View {
        ModeledText(model: .standard(headline, font: .title1))
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 8)
    }
}
