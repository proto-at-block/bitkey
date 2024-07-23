import DatadogTrace
import Shared

public class DatadogTracerImpl: DatadogTracer {

    public init() {}

    public func buildSpan(spanName: String) -> DatadogSpan {
        let span = Tracer.shared().startSpan(operationName: spanName)
        return DatadogSpanImpl(span: span)
    }

    public func buildSpan(
        spanName: String,
        parentSpan: DatadogSpan
    ) -> DatadogSpan {
        let spanImpl = parentSpan as! DatadogSpanImpl
        let span = Tracer.shared().startSpan(
            operationName: spanName,
            childOf: spanImpl.span.context
        )
        return DatadogSpanImpl(span: span)
    }

    public func inject(span: DatadogSpan) -> TracerHeaders {
        let spanImpl = span as! DatadogSpanImpl
        let ddHeadersWriter = HTTPHeadersWriter(sampleRate: 100)
        let w3cHeadersWriter = W3CHTTPHeadersWriter(sampleRate: 100)
        var headers: [String: String] = [:]

        Tracer.shared().inject(spanContext: spanImpl.span.context, writer: ddHeadersWriter)
        Tracer.shared().inject(spanContext: spanImpl.span.context, writer: w3cHeadersWriter)

        for (headerField, value) in ddHeadersWriter.traceHeaderFields {
            headers[headerField] = value
        }

        for (headerField, value) in w3cHeadersWriter.traceHeaderFields {
            headers[headerField] = value
        }

        return TracerHeaders(headers: headers)
    }
}
