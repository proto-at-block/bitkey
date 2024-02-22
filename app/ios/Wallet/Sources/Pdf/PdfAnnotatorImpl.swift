import PDFKit
import Shared


// MARK: - PdfAnnotatorFactoryImpl
public final class PdfAnnotatorFactoryImpl: Shared.PdfAnnotatorFactory {
    public func createBlocking(pdfTemplateData: OkioByteString) -> PdfAnnotationResult<PdfAnnotator> {
        guard let pdf = PDFDocument(data: pdfTemplateData.toData()) else {
            return PdfAnnotationResultErr(error: PdfAnnotationError.InvalidData())
        }
        return PdfAnnotationResultOk(value: PdfAnnotatorImpl(pdf: pdf))
    }
}

// MARK: - PdfAnnotatorImpl
public final class PdfAnnotatorImpl: Shared.PdfAnnotator {

    private var pdf: PDFDocument
    private var links: [Link]

    init(pdf: PDFDocument) {
        self.pdf = pdf
        self.links = []
    }

    // MARK: - PdfAnnotator

    public func addTextBlocking(text: TextAnnotation, pageNum: Int32, frame: PdfFrame, url: String?) -> PdfAnnotationResult<KotlinUnit> {
        let pageNum = Int(pageNum)
        guard let page = pdf.page(at: pageNum) else {
            return PdfAnnotationResultErr(error: PdfAnnotationError.InvalidPage())
        }

        guard let font = UIFont(name: text.font, size: CGFloat(text.fontSize)) else {
            return PdfAnnotationResultErr(error: PdfAnnotationError.InvalidFont())
        }

        if let url = url, url.count > 0 {
            guard let url = URL(string: url) else {
                return PdfAnnotationResultErr(error: PdfAnnotationError.InvalidURL())
            }

            self.links.append(Link(url: url, pageNum: pageNum, frame: frame))
        }

        let annotation = PDFAnnotation(
            bounds: frame.asBounds(),
            forType: .freeText,
            withProperties: nil)
        annotation.font = font
        annotation.fontColor = text.color.asUiColor()
        annotation.color = .clear
        annotation.contents = text.contents

        page.addAnnotation(annotation)

        return PdfAnnotationResultOk(value: KotlinUnit())
    }

    public func addImageDataBlocking(data: OkioByteString, pageNum: Int32, frame: PdfFrame, url: String?) -> PdfAnnotationResult<KotlinUnit> {
        let pageNum = Int(pageNum)
        guard let page = pdf.page(at: pageNum) else {
            return PdfAnnotationResultErr(error: PdfAnnotationError.InvalidPage())
        }

        guard let image = UIImage(data: data.toData()) else {
            return PdfAnnotationResultErr(error: PdfAnnotationError.InvalidImage())
        }

        if let url = url, url.count > 0 {
            guard let url = URL(string: url) else {
                return PdfAnnotationResultErr(error: PdfAnnotationError.InvalidURL())
            }

            self.links.append(Link(url: url, pageNum: pageNum, frame: frame))
        }

        let annotation = ImageStampAnnotation(
            image: image,
            bounds: frame.asBounds())

        page.addAnnotation(annotation)

        return PdfAnnotationResultOk(value: KotlinUnit())
    }

    public func serializeBlocking() -> PdfAnnotationResult<OkioByteString> {
        if case .failure = flatten() {
            return PdfAnnotationResultErr(error: PdfAnnotationError.SerializeFailed())
        }
        guard let data = pdf.dataRepresentation() else {
            return PdfAnnotationResultErr(error: PdfAnnotationError.SerializeFailed())
        }

        return PdfAnnotationResultOk(value: OkioKt.ByteString(data: data))
    }

    // MARK: - Private Methods

    /// Add a URL link annotation at page with frame.
    /// Will silently fail if the page number is invalid. All page numbers were
    /// validated when added to the links property.
    fileprivate func addLink(link: Link) {
        guard let page = pdf.page(at: link.pageNum) else {
            return
        }

        let annotation = PDFAnnotation(
            bounds: link.frame.asBounds(),
            forType: .link,
            withProperties: nil)
        annotation.action = PDFActionURL(url: link.url)

        page.addAnnotation(annotation)
    }

    fileprivate func flatten() -> Result<(), FlattenAnnotationError> {
        let writeOptions = if #available(iOS 16.0, *) {
            [PDFDocumentWriteOption.burnInAnnotationsOption: true]
        } else {
            [:]
        }
        guard let data = pdf.dataRepresentation(options: writeOptions) else {
            return .failure(FlattenAnnotationError())
        }
        guard let pdf = PDFDocument(data: data) else {
            return .failure(FlattenAnnotationError())
        }

        self.pdf = pdf

        for link in self.links {
            addLink(link: link)
        }
        self.links = []

        return .success(())
    }

    // MARK: - Internal Types

    struct LoadingError: Error {}
    struct FlattenAnnotationError: Error {}
    struct Link {
        var url: URL
        var pageNum: Int
        var frame: PdfFrame
    }

    // MARK: - KotlinAutoCloseable

    public func close() {
        // noop, the loaded pdf document will be deallocated when this is autoreleased.
        // This is here to allow consistent usage of the shared API.
    }

}


extension PdfFrame {
    func asBounds() -> CGRect {
        return CGRect(
            x: Int(x),
            y: Int(y),
            width: Int(width),
            height: Int(height))
    }
}


extension PdfColor {
    func asUiColor() -> UIColor {
        return UIColor(
            red: CGFloat(r),
            green: CGFloat(g),
            blue: CGFloat(b),
            alpha: 1.0)
    }
}


fileprivate class ImageStampAnnotation: PDFAnnotation {
    let image: UIImage

    init(image: UIImage, bounds: CGRect) {
        self.image = image
        super.init(bounds: bounds, forType: .stamp, withProperties: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func draw(with box: PDFDisplayBox, in context: CGContext) {
        guard let cgImage = self.image.cgImage else {
            return
        }

        context.draw(cgImage, in: bounds)
    }
}
