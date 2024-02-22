import Foundation
import SwiftUI

// MARK: -

/**
 * A header view that is meant to sit overlaid on top of other content as a top bar.
 * Includes positioning the view at the top of the screen.
 */
public struct HeaderOverlayView: View {

    // MARK: - Public Types

    public enum Metrics {
        static let height = 64.f
    }

    // MARK: - View

    public var body: some View {
        VStack {
            ZStack {
                VisualEffectView(effect: UIBlurEffect(style: .light))
                    .ignoresSafeArea()
                    .frame(height: Metrics.height)
                ModeledText(model: .standard("Home", font: .title2, textAlignment: .center))
            }
            Spacer()
        }
    }

}
