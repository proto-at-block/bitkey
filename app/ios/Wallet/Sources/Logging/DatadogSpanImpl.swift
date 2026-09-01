import DatadogTrace
import Foundation
import Shared

class DatadogSpanImpl: DatadogSpan {
    private var _resourceName: String?

    var resourceName: String? {
        get {
            mutationQueue.sync {
                _resourceName
            }
        }
        set {
            guard let name = newValue else {
                mutationQueue.sync {
                    _resourceName = nil
                }
                return
            }

            let copiedName = copiedString(name)
            mutationQueue.sync {
                _resourceName = copiedName
                span.setOperationName(copiedName)
            }
        }
    }

    var context: OTSpanContext {
        mutationQueue.sync {
            span.context
        }
    }

    private let mutationQueue = DispatchQueue(label: "world.bitkey.datadog-span")
    private let span: OTSpan

    init(span: OTSpan) {
        self.span = span
    }

    func setTag(key: String, value: String) {
        let copiedKey = copiedString(key)
        let copiedValue = copiedString(value)
        mutationQueue.sync {
            span.setTag(key: copiedKey, value: copiedValue)
        }
    }

    func finish() {
        mutationQueue.sync {
            span.finish()
        }
    }

    func finish(cause: KotlinThrowable) {
        let kind = copiedString(cause.description)
        let message = copiedString(cause.message ?? "")
        let stack = copiedString(
            KotlinArrayIterator(cause.getStackTrace()).map { $0 as String }
                .joined(separator: "\n")
        )

        mutationQueue.sync {
            span.setError(
                kind: kind,
                message: message,
                stack: stack,
                file: ""
            )

            span.finish()
        }
    }

    private func copiedString(_ string: String) -> String {
        String(decoding: string.utf8, as: UTF8.self)
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
