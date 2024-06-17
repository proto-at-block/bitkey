import CoreImage.CIFilterBuiltins
import Foundation
import Shared
import SwiftUI

// MARK: -

public struct AddressQrCodeView: View {

    // MARK: - Private Types

    private enum Metrics {
        static let qrCodeAndAddressHorizontalPadding = 36.f
    }

    // MARK: - Public Properties

    public var viewModel: AddressQrCodeBodyModel

    // MARK: - View

    public var body: some View {
        GeometryReader { reader in
            ZStack(alignment: Alignment(horizontal: .center, vertical: .top)) {
                ScrollView(.vertical) {
                    Spacer(minLength: DesignSystemMetrics.verticalPadding)
                    VStack(alignment: .center) {
                        ZStack {
                            switch viewModel.content {
                            case let contentModel as AddressQrCodeBodyModelContentQrCode:
                                AddressQrCodeContentView(
                                    viewModel: contentModel,
                                    qrCodeAndAddressHorizontalPadding: Metrics
                                        .qrCodeAndAddressHorizontalPadding,
                                    qrCodeSize: reader.size.width
                                        - (DesignSystemMetrics.horizontalPadding * 2)
                                        - (Metrics.qrCodeAndAddressHorizontalPadding * 2)
                                )

                            case let errorModel as AddressQrCodeBodyModelContentError:
                                AddressQrCodeErrorView(errorModel: errorModel)

                            default:
                                fatalError(
                                    "Unexpected address qr code content model: \(viewModel.content)"
                                )
                            }
                        }
                    }
                    .frame(
                        minHeight: reader.size.height - DesignSystemMetrics
                            .toolbarHeight - DesignSystemMetrics.verticalPadding
                    )
                    .padding(.vertical, DesignSystemMetrics.verticalPadding)
                    .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
                }

                ToolbarView(viewModel: viewModel.toolbarModel)
                    .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
                    .background(Color.background)
            }
            .padding(.top, reader.safeAreaInsets.top == 0 ? DesignSystemMetrics.verticalPadding : 0)
        }
    }
}

// MARK: -

private struct AddressQrCodeContentView: View {
    var viewModel: AddressQrCodeBodyModelContentQrCode
    var qrCodeAndAddressHorizontalPadding: CGFloat
    var qrCodeSize: CGFloat

    var body: some View {
        VStack(alignment: .center, spacing: 24) {
            VStack(spacing: 24) {
                // QR Code, either loading or showing the generated code
                ZStack(alignment: .center) {
                    if let addressQrImageUrl = viewModel.addressQrImageUrl,
                       let fallbackAddressQrCodeModel = viewModel.fallbackAddressQrCodeModel
                    {
                        AddressQrCodeContentQrView(
                            addressQrImageUrl: addressQrImageUrl,
                            fallbackAddressQrCodeModel: fallbackAddressQrCodeModel
                        )
                    } else {
                        RotatingLoadingIcon(size: .avatar, tint: .black)
                    }
                }
                .frame(width: qrCodeSize, height: qrCodeSize)

                // Address text
                ModeledText(
                    model: .standard(
                        .string(from: viewModel.addressDisplayString, font: .body2Mono),
                        font: .body2Mono,
                        textAlignment: .center,
                        textColor: .foreground
                    )
                )
            }
            .padding(.vertical, 24)
            .padding(.horizontal, qrCodeAndAddressHorizontalPadding)
            .overlay(
                RoundedRectangle(cornerRadius: 24)
                    .stroke(Color.foreground10, lineWidth: 2)
            )
            .onTapGesture {
                viewModel.onCopyClick()
            }
            ModeledText(
                model: .standard(
                    "This address only accepts Bitcoin (BTC). Sending other assets will result in permanent loss of funds.",
                    font: .body4Regular,
                    textAlignment: .center,
                    textColor: .foreground60
                )
            )

            // Share and copy buttons
            HStack(spacing: 16) {
                ButtonView(model: viewModel.shareButtonModel)
                ButtonView(model: viewModel.copyButtonModel)
            }
        }
    }
}

// MARK: -

private struct AddressQrCodeErrorView: View {
    let errorModel: AddressQrCodeBodyModelContentError
    var body: some View {
        VStack(spacing: 4) {
            Image(uiImage: .largeIconWarningFilled)
                .foregroundColor(.primary)
                .padding(.bottom, 8)
            ModeledText(model: .standard(errorModel.title, font: .title1, textAlignment: .center))
            ModeledText(model: .standard(
                errorModel.subline,
                font: .body1Regular,
                textAlignment: .center
            ))
        }
    }
}

// MARK: -

private struct AddressQrCodeContentQrView: View {
    let addressQrImageUrl: String
    let fallbackAddressQrCodeModel: QrCodeModel
    var body: some View {
        if let url = URL(string: addressQrImageUrl) {
            AsyncUrlImageView(
                url: url,
                size: .avatar,
                opacity: 1.0,
                fallbackContent: {
                    FallbackAddressQrCodeContentQrView(addressQrCode: fallbackAddressQrCodeModel)
                }
            )
            .aspectRatio(contentMode: .fit)
        } else {
            FallbackAddressQrCodeContentQrView(addressQrCode: fallbackAddressQrCodeModel)
        }
    }
}

// MARK: -

private struct FallbackAddressQrCodeContentQrView: View {
    let addressQrCode: QrCodeModel
    var body: some View {
        if let uiImage = makeQRCodeImage(from: addressQrCode.data) {
            Image(uiImage: uiImage)
                .interpolation(.none)
                .resizable()
        } else {
            AddressQrCodeErrorView(errorModel: .init(title: "", subline: ""))
        }
    }

    private func makeQRCodeImage(from string: String) -> UIImage? {
        let context = CIContext()
        let filter = CIFilter.qrCodeGenerator()
        let data = Data(string.utf8)
        filter.setValue(data, forKey: "inputMessage")

        if let outputImage = filter.outputImage,
           let cgimg = context.createCGImage(outputImage, from: outputImage.extent)
        {
            return UIImage(cgImage: cgimg)
        }

        return nil
    }
}
