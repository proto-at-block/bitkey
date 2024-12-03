import Shared
import SwiftUI

// MARK: -

struct CardContentBitcoinPrice: View {

    // MARK: - Public Properties

    var viewModel: CardModelCardContentBitcoinPrice

    // MARK: - View

    var body: some View {
        HStack(spacing: 0) {
            Image(uiImage: .bitcoinOrange)
            Spacer()
                .frame(width: 4)
            ModeledText(model: .standard(
                "Bitcoin price",
                font: .body3Bold,
                textColor: Color.bitcoinPrimary
            ))

            Spacer()

            ZStack {
                if viewModel.isLoading {
                    ModeledText(model: .standard(
                        "Updated 00:00am",
                        font: .body4Regular,
                        textAlignment: .trailing,
                        textColor: Color.clear
                    ))
                    .loadingBackground()
                } else {
                    ModeledText(model: .standard(
                        viewModel.lastUpdated,
                        font: .body4Regular,
                        textAlignment: .trailing,
                        textColor: Color.foreground30
                    ))
                }
            }.animation(.spring, value: viewModel)

            Spacer().frame(width: 2)

            Image(uiImage: .smallIconCaretRight)
                .resizable()
                .foregroundColor(.foreground30)
                .frame(width: 16, height: 16)
        }

        HStack(alignment: .bottom, spacing: 0) {
            VStack(alignment: .leading, spacing: 4) {
                Spacer().frame(height: 14)

                ZStack {
                    if viewModel.isLoading {
                        ModeledText(model: .standard(
                            "$00,000.00",
                            font: .body1Bold,
                            textColor: .clear
                        ))
                        .fixedSize(horizontal: true, vertical: false)
                        .loadingBackground()
                    } else {
                        ModeledText(model: .standard(viewModel.price, font: .body1Bold))
                            .fixedSize(horizontal: true, vertical: false)
                    }
                }.animation(.spring, value: viewModel)

                ZStack {
                    HStack(alignment: .center, spacing: 0) {
                        Image(uiImage: .smallIconArrowUp)
                            .resizable()
                            .rotationEffect(.degrees(Double(viewModel.priceDirection.orientation)))
                            .if(viewModel.isLoading) { view in
                                view.foregroundColor(.clear)
                            }
                            .if(!viewModel.isLoading) { view in
                                view.foregroundColor(.foreground60)
                            }
                            .frame(width: 16, height: 16)

                        if viewModel.isLoading {
                            ModeledText(model: .standard(
                                "0.00% today",
                                font: .body3Regular,
                                textColor: .clear
                            ))
                            .fixedSize(horizontal: true, vertical: false)
                        } else {
                            ModeledText(model: .standard(
                                viewModel.priceChange,
                                font: .body3Regular
                            ))
                            .fixedSize(horizontal: true, vertical: false)
                        }
                    }.if(viewModel.isLoading) { view in
                        view.loadingBackground()
                    }
                }.animation(.spring, value: viewModel)
            }

            Spacer().frame(width: 16)

            ZStack {
                if viewModel.isLoading {
                    Image(uiImage: .sparklinePlaceholder)
                        .resizable()
                } else {
                    SparklineView(data: viewModel.data.map { $0.second?.doubleValue ?? 0 })
                }
            }.frame(height: 40)
                .padding([.trailing], 8)
                .padding([.leading], 46)
                .animation(.spring, value: viewModel)
        }
        Spacer().frame(height: 6)
    }
}

// MARK: -

private extension View {

    func loadingBackground() -> some View {
        background(Color.loadingBackground)
            .clipShape(RoundedCorners(radius: 8, corners: .allCorners))
    }
}

struct SparklineView: View {
    let data: [Double]

    var body: some View {
        GeometryReader { geometry in
            let path = self.createPath(in: geometry.frame(in: .local))
            ZStack {
                path.stroke(Color.black.opacity(0.1), lineWidth: 3)

                if let lastPoint = self.getLastPoint(in: geometry.frame(in: .local)) {
                    ZStack(alignment: .center) {
                        RadialGradient(
                            gradient: Gradient(colors: [
                                Color.bitcoinPrimary.opacity(0.2),
                                Color.clear,
                            ]),
                            center: .center,
                            startRadius: 0,
                            endRadius: 24
                        )
                        Circle()
                            .fill(Color.bitcoinPrimary)
                            .frame(width: 8, height: 8)
                        Circle()
                            .fill(.white)
                            .frame(width: 4, height: 4)
                    }
                    .position(lastPoint)
                }
            }
        }
    }

    private func createPath(in rect: CGRect) -> Path {
        var path = Path()
        // if the data list is empty, return an empty Path
        guard !data.isEmpty else { return path }

        let maxValue = data.max() ?? 0
        let minValue = data.min() ?? 0
        let yRange = maxValue - minValue
        let stepX = rect.width / CGFloat(data.count - 1)
        let points = data.enumerated().map { index, value in
            CGPoint(
                x: CGFloat(index) * stepX,
                y: rect.height - CGFloat((value - minValue) / yRange) * rect.height
            )
        }

        path.move(to: points[0])

        // Add lines to the rest of the points
        for index in 1 ..< points.count {
            let currentPoint = points[index]
            let previousPoint = points[index - 1]
            let targetPoint = CGPoint(
                x: (previousPoint.x + currentPoint.x) / 2,
                y: (previousPoint.y + currentPoint.y) / 2
            )
            path.addQuadCurve(to: targetPoint, control: previousPoint)
        }

        let lastY = points.last?.y ?? 0.0
        path.addLine(to: CGPoint(x: rect.width, y: lastY))

        return path
    }

    private func getLastPoint(in rect: CGRect) -> CGPoint? {
        guard !data.isEmpty else { return nil }

        let maxValue = data.max() ?? 0
        let minValue = data.min() ?? 0
        let yRange = maxValue - minValue
        let stepX = rect.width / CGFloat(data.count - 1)
        let stepY = rect.height / CGFloat(yRange)

        return CGPoint(
            x: CGFloat(data.count - 1) * stepX,
            y: rect.height - CGFloat(data.last! - minValue) * stepY
        )
    }
}
