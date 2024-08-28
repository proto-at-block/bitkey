import Shared
import SwiftUI

// MARK: -

struct CardContentView: View {

    // MARK: - Private Types

    private enum Metrics {
        static let titleToSubtitleSpacing = 2.f
        static let titleSubtitleToIconSpacing = 12.f
        static let trailingButtonHorizontalPadding = 9.f
        static let trailingButtonHeight = 32.f
    }

    @SwiftUI.State private var minLabelHeight: CGFloat = 0

    // MARK: - Public Properties

    var viewModel: CardModel
    var overridenTitleToSubtitleSpacing: CGFloat?

    // MARK: - View

    var body: some View {
        VStack(spacing: 0) {
            // Hero Image
            viewModel.heroImage.map { heroImage in
                Image(uiImage: heroImage.uiImage)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(maxWidth: .infinity)
            }

            // Title + Content
            VStack {
                // Title + Subtitle + Leading Image
                HStack(
                    spacing: overridenTitleToSubtitleSpacing == nil ? Metrics
                        .titleSubtitleToIconSpacing : overridenTitleToSubtitleSpacing
                ) {
                    viewModel.leadingImage.map {
                        CardImage(viewModel: $0)
                            .overlay {
                                GeometryReader { imageGeoProxy in
                                    Color.clear
                                        .onAppear { self.minLabelHeight = imageGeoProxy.size.height
                                        }
                                }
                            }
                    }

                    VStack(spacing: Metrics.titleToSubtitleSpacing) {
                        if let title = viewModel.title {
                            ModeledText(
                                model: .standard(
                                    .string(from: title, font: .body2Regular),
                                    font: .title2
                                )
                            )
                        }

                        viewModel.subtitle.map { ModeledText(model: .standard(
                            $0,
                            font: .body3Regular,
                            textColor: .foreground60
                        )) }
                    }.frame(minHeight: minLabelHeight)

                    viewModel.trailingButton.map {
                        ButtonView(
                            model: $0,
                            horizontalPadding: Metrics.trailingButtonHorizontalPadding
                        )
                    }
                }

                // Content
                if let contentViewModel = viewModel.content {
                    switch contentViewModel {
                    case let drillListModel as CardModelCardContentDrillList:
                        CardContentDrillList(viewModel: drillListModel)
                    default:
                        fatalError("Unexpected card content model \(viewModel)")
                    }
                }
            }
            .padding(style: viewModel.style, hasContent: viewModel.content != nil)
        }
    }

}

struct CardContentViewBitcoinPriceCard: View {

    // MARK: - Private Types

    // MARK: - Public Properties

    var viewModel: CardModel

    // MARK: - View

    var body: some View {
        VStack(spacing: 0) {
            // Content
            if let contentViewModel = viewModel.content {
                switch contentViewModel {
                case let priceCardModel as CardModelCardContentBitcoinPrice:
                    CardContentBitcoinPrice(viewModel: priceCardModel)
                default:
                    fatalError("Unexpected card content model \(viewModel)")
                }
            }
        }
        .padding(style: viewModel.style, hasContent: true)
    }

}

// MARK: -

struct CardImage: View {

    var viewModel: CardModelCardImage

    var body: some View {
        switch viewModel {
        case let staticImage as CardModelCardImageStaticImage:
            Image(uiImage: staticImage.icon.uiImage)
                .if(staticImage.iconTint == .warning) { image in
                    image.foregroundColor(Color.warningForeground)
                }

        case let hwRecoveryProgress as CardModelCardImageDynamicImageHardwareReplacementStatusProgress:
            ZStack {
                CircularProgressView(
                    progress: hwRecoveryProgress.progress,
                    direction: .counterclockwise,
                    remainingDuration: TimeInterval(hwRecoveryProgress.remainingSeconds),
                    progressColor: .containerHighlightForeground,
                    strokeWidth: 3
                )

                Image(uiImage: Icon.bitkeydeviceraisedsmall.uiImage)
            }
            .frame(width: 40, height: 40)

        default:
            fatalError("Unsupported card image type")
        }
    }

}

private extension View {
    @ViewBuilder
    func padding(style: CardModel.CardStyle, hasContent: Bool) -> some View {
        switch style {
        case _ as CardModel.CardStyleOutline:
            if hasContent {
                self
                    .padding(.top, 20)
                    .padding(.horizontal, 20)
                    .padding(.bottom, 12)
            } else {
                self
                    .padding(20)
            }

        case _ as CardModel.CardStyleGradient:
            self
                .padding(.horizontal, 16)
                .padding(.vertical, 12)

        default:
            fatalError("Unexpected card style: \(style)")
        }
    }
}
