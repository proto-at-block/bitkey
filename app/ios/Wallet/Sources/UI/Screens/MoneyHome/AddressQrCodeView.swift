import CoreImage.CIFilterBuiltins
import Foundation
import Shared
import SwiftUI

// MARK: -

public struct AddressQrCodeView: View {

    // MARK: - Private Properties

    private let contentHorizontalPadding = 36.f

    // MARK: - Public Properties

    public var viewModel: AddressQrCodeBodyModel

    // MARK: - View

    public var body: some View {
        GeometryReader { reader in
            VStack {
                ToolbarView(viewModel: viewModel.toolbarModel)
                    .padding(.horizontal, DesignSystemMetrics.horizontalPadding)

                Spacer()

                switch viewModel.content {
                case let contentModel as AddressQrCodeBodyModelContentQrCode:
                    qrCodeContent(contentModel: contentModel, contentWidth: reader.size.width - contentHorizontalPadding)
                        .frame(width: reader.size.width - contentHorizontalPadding)

                case let errorModel as AddressQrCodeBodyModelContentError:
                    errorContent(errorModel: errorModel)
                        .frame(width: reader.size.width - contentHorizontalPadding)

                default:
                    fatalError("Unexpected address qr code content model: \(viewModel.content)")
                }

                Spacer()
                Spacer()
            }
            .padding(.top, reader.safeAreaInsets.top == 0 ? DesignSystemMetrics.verticalPadding : 0)
        }
    }

    private func qrCodeContent(
        contentModel: AddressQrCodeBodyModelContentQrCode,
        contentWidth: CGFloat
    ) -> some View {
        VStack(alignment: .center, spacing: 24) {
            if let addressQrCode = contentModel.addressQrCode {
                Image(uiImage: generateQRCode(from: addressQrCode.data))
                    .interpolation(.none)
                    .resizable()
                    .frame(width: contentWidth, height: contentWidth)
            } else {
                VStack(alignment: .center) {
                    Spacer()
                    RotatingLoadingIcon(size: .regular, tint: .black)
                    Spacer()
                }.frame(width: contentWidth, height: contentWidth)
            }

            ModeledText(
                model: .standard(contentModel.address ?? "...", font: .body2Mono, textAlignment: .center, textColor: .foreground60)
            )

            HStack(spacing: 16) {
                ButtonView(model: contentModel.shareButtonModel)
                ButtonView(model: contentModel.copyButtonModel)
            }
        }
    }

    private func errorContent(errorModel: AddressQrCodeBodyModelContentError) -> some View {
        VStack(spacing: 4) {
            Image(uiImage: .largeIconWarningFilled)
                .foregroundColor(.primary)
                .padding(.bottom, 8)
            ModeledText(model: .standard(errorModel.title, font: .title1, textAlignment: .center))
            ModeledText(model: .standard(errorModel.subline, font: .body1Regular, textAlignment: .center))
        }
    }

    private func generateQRCode(from string: String) -> UIImage {
        let context = CIContext()
        let filter = CIFilter.qrCodeGenerator()
        let data = Data(string.utf8)
        filter.setValue(data, forKey: "inputMessage")

        if let outputImage = filter.outputImage {
            if let cgimg = context.createCGImage(outputImage, from: outputImage.extent) {
                return UIImage(cgImage: cgimg)
            }
        }

        return UIImage(systemName: "xmark.circle") ?? UIImage()
    }

}
