import DatadogTrace
import Shared

class DatadogSpanImpl: DatadogSpan {
    var resourceName: String? {
        didSet {
            if let name = resourceName {
                span.setOperationName(name)
            }
        }
    }

    let span: OTSpan

    init(span: OTSpan) {
        self.span = span
    }

    func setTag(key: String, value: String) {
        span.setTag(key: key, value: value)
    }

    func finish() {
        span.finish()
    }

    func finish(cause: KotlinThrowable) {
        span.setError(
            kind: cause.description,
            message: cause.message ?? "",
            stack: KotlinArrayIterator(cause.getStackTrace()).map { $0 as String }
                .joined(separator: "\n"),
            file: ""
        )

        span.finish()
    }
}

private class KotlinArrayIterator<T: AnyObject>: Sequence, IteratorProtocol {
    typealias Element = T

    let inner: KotlinArray<T>
    var index: Int32 = 0

    init(_ array: KotlinArray<T>) {
        inner = array
    }

    func next() -> T? {
        guard index < inner.size else {
            return nil
        }

        let result = inner.get(index: index)
        index = index + 1
        return result
    }
}
