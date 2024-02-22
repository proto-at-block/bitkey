use std::io;

use opentelemetry::trace::{SpanId, TraceContextExt};
use serde::ser::{SerializeMap, Serializer as _};
use serde_json::{Map, Value};
use time::format_description::well_known::Rfc3339;
use time::OffsetDateTime;
use tracing::{Event, Subscriber};
use tracing_log::NormalizeEvent;
use tracing_opentelemetry::OtelData;
use tracing_serde::AsSerde;
use tracing_subscriber::fmt::format::Writer;
use tracing_subscriber::fmt::{FmtContext, FormatEvent, FormatFields};
use tracing_subscriber::registry::{LookupSpan, SpanRef};

/// Custom Json formatter written based off of discussion in
/// https://github.com/tokio-rs/tracing/issues/1531
/// It behaves similarly to the built-in Json formatter with the following configuration
///   tracing_subscriber::fmt::fmt()
///       .json()
///       .with_line_number(true)
///       .with_file(true)
///       .with_thread_names(true)
///       .flatten_event(true)
///
/// It differs in that it will also parse the trace ID and span ID from the OpenTelemetry context
/// and place it in the "trace_id" and "span_id" top-level fields, so that the logs can then be
/// correlated with traces in Datadog.
/// reference: https://docs.rs/tracing-subscriber/0.3.16/tracing_subscriber/fmt/format/struct.Json.html
pub struct JsonTraceIdFormat {
    get_time: fn() -> OffsetDateTime,
}

impl Default for JsonTraceIdFormat {
    fn default() -> JsonTraceIdFormat {
        JsonTraceIdFormat {
            get_time: OffsetDateTime::now_utc,
        }
    }
}

impl<S, N> FormatEvent<S, N> for JsonTraceIdFormat
where
    S: Subscriber + for<'lookup> LookupSpan<'lookup>,
    N: for<'writer> FormatFields<'writer> + 'static,
{
    fn format_event(
        &self,
        ctx: &FmtContext<'_, S, N>,
        mut writer: Writer<'_>,
        event: &Event<'_>,
    ) -> std::fmt::Result
    where
        S: Subscriber + for<'a> LookupSpan<'a>,
    {
        let normalized_meta = event.normalized_metadata();
        let meta = normalized_meta.as_ref().unwrap_or_else(|| event.metadata());
        let timestamp = (self.get_time)()
            .format(&Rfc3339)
            .unwrap_or_else(|_| String::from("unable to format time"));

        let mut visit = || {
            let mut serializer = serde_json::Serializer::new(WriteAdaptor::new(&mut writer));

            let mut serializer = serializer.serialize_map(None)?;
            serializer.serialize_entry("timestamp", &timestamp)?;
            serializer.serialize_entry("level", &meta.level().as_serde())?;

            // Flatten event fields
            let mut visitor = tracing_serde::SerdeMapVisitor::new(serializer);
            event.record(&mut visitor);
            serializer = visitor.take_serializer()?;

            serializer.serialize_entry("target", meta.target())?;

            if let Some(filename) = meta.file() {
                serializer.serialize_entry("filename", filename)?;
            }
            if let Some(line_number) = meta.line() {
                serializer.serialize_entry("line_number", &line_number)?;
            }

            let current_thread = std::thread::current();
            if let Some(thread_name) = current_thread.name() {
                serializer.serialize_entry("threadName", thread_name)?;
            }

            if let Some(ref span_ref) = ctx.lookup_current() {
                if let Some(trace_info) = lookup_trace_info(span_ref) {
                    serializer.serialize_entry("span_id", &trace_info.span_id)?;
                    serializer.serialize_entry("trace_id", &trace_info.trace_id)?;
                    // Duplicate trace_id to dd.trace_id as that is Datadog's canonical field name.
                    let mut map = Map::new();
                    map.insert(String::from("trace_id"), Value::String(trace_info.trace_id));
                    serializer.serialize_entry("dd", &map)?;
                }
            }

            serializer.end()
        };

        visit().map_err(|_| std::fmt::Error)?;
        writeln!(writer)
    }
}

struct TraceInfo {
    pub trace_id: String,
    pub span_id: String,
}

fn lookup_trace_info<S>(span_ref: &SpanRef<S>) -> Option<TraceInfo>
where
    S: Subscriber + for<'a> LookupSpan<'a>,
{
    span_ref.extensions().get::<OtelData>().map(|o| {
        // Look up trace ID from the current span builder, or if missing, from its
        // parent context.
        let trace_id = o
            .builder
            .trace_id
            .unwrap_or_else(|| o.parent_cx.span().span_context().trace_id());
        let span_id = o.builder.span_id.unwrap_or(SpanId::INVALID);

        TraceInfo {
            // OpenTelemetry TraceId and SpanId properties differ from Datadog conventions.
            // Therefore it’s necessary to translate TraceId and SpanId from their
            // OpenTelemetry formats (a 128bit unsigned int and 64bit unsigned int
            // represented as a 32-hex-character and 16-hex-character lowercase string,
            // respectively) into their Datadog Formats(a 64bit unsigned int).
            // https://docs.datadoghq.com/tracing/other_telemetry/connect_logs_and_traces/opentelemetry
            // https://github.com/open-telemetry/opentelemetry-rust/blob/main/opentelemetry-datadog/src/exporter/model/v05.rs#L169-L176
            trace_id: (u128::from_be_bytes(trace_id.to_bytes()) as u64).to_string(),
            span_id: u64::from_be_bytes(span_id.to_bytes()).to_string(),
        }
    })
}

/// A bridge between `fmt::Write` and `io::Write`.
///
/// This is used by the timestamp formatting implementation for the `time`
/// crate and by the JSON formatter. In both cases, this is needed because
/// `tracing-subscriber`'s `FormatEvent`/`FormatTime` traits expect a
/// `fmt::Write` implementation, while `serde_json::Serializer` and `time`'s
/// `format_into` methods expect an `io::Write`.
pub struct WriteAdaptor<'a> {
    fmt_write: &'a mut dyn std::fmt::Write,
}

impl<'a> WriteAdaptor<'a> {
    pub fn new(fmt_write: &'a mut dyn std::fmt::Write) -> Self {
        Self { fmt_write }
    }
}

impl<'a> io::Write for WriteAdaptor<'a> {
    fn write(&mut self, buf: &[u8]) -> io::Result<usize> {
        let s =
            std::str::from_utf8(buf).map_err(|e| io::Error::new(io::ErrorKind::InvalidData, e))?;

        self.fmt_write
            .write_str(s)
            .map_err(|e| io::Error::new(io::ErrorKind::Other, e))?;

        Ok(s.as_bytes().len())
    }

    fn flush(&mut self) -> io::Result<()> {
        Ok(())
    }
}

#[cfg(test)]
mod test {
    use std::io;
    use std::sync::{Arc, Mutex, MutexGuard, TryLockError};

    use opentelemetry::trace::TracerProvider as _;
    use opentelemetry_sdk::trace::TracerProvider;
    use opentelemetry_stdout::SpanExporter;
    use time::{format_description::well_known::Rfc3339, OffsetDateTime};
    use tracing::info_span;
    use tracing::subscriber::with_default;
    use tracing_subscriber::fmt::format::JsonFields;
    use tracing_subscriber::{
        fmt::{writer::MakeWriter, SubscriberBuilder},
        prelude::__tracing_subscriber_SubscriberExt,
    };

    use crate::json::JsonTraceIdFormat;

    fn subscriber() -> SubscriberBuilder<JsonFields, JsonTraceIdFormat> {
        SubscriberBuilder::default()
            .fmt_fields(JsonFields::default())
            .event_format(JsonTraceIdFormat {
                get_time: || OffsetDateTime::parse("2023-01-02T11:22:33Z", &Rfc3339).unwrap(),
            })
    }

    #[test]
    fn json() {
        let expected = r#"{"timestamp":"2023-01-02T11:22:33Z","level":"INFO","message":"some json test","target":"wallet_telemetry::json::test","filename":"src/wallet-telemetry/src/json.rs","line_number":153,"threadName":"json::test::json"}"#;
        let subscriber = subscriber();
        test_json(expected, subscriber, || {
            info_span!("json_span", answer = 42, number = 3).in_scope(|| {
                tracing::info!("some json test");
            });
        });
    }

    #[test]
    fn json_nested_span() {
        let expected = r#"{"timestamp":"2023-01-02T11:22:33Z","level":"INFO","message":"some json test","target":"wallet_telemetry::json::test","filename":"src/wallet-telemetry/src/json.rs","line_number":153,"threadName":"json::test::json_nested_span"}"#;
        let subscriber = subscriber();
        test_json(expected, subscriber, || {
            info_span!("json_span").in_scope(|| {
                info_span!("json_span").in_scope(|| {
                    info_span!("json_span", answer = 42, number = 3).in_scope(|| {
                        tracing::info!("some json test");
                    });
                });
            });
        });
    }

    fn test_json<T>(
        expected: &str,
        builder: tracing_subscriber::fmt::SubscriberBuilder<JsonFields, JsonTraceIdFormat>,
        producer: impl FnOnce() -> T,
    ) {
        let make_writer = MockMakeWriter::default();
        let exporter = SpanExporter::default();
        let provider = TracerProvider::builder()
            .with_simple_exporter(exporter)
            .build();
        let tracer = provider.tracer("test");
        let layer = tracing_opentelemetry::layer().with_tracer(tracer);
        let subscriber = builder
            .with_writer(make_writer.clone())
            .finish()
            .with(layer);

        with_default(subscriber, producer);

        let buf = make_writer.buf();
        let actual = std::str::from_utf8(&buf[..]).unwrap();
        println!("{}", actual);
        let mut expected =
            serde_json::from_str::<std::collections::BTreeMap<&str, serde_json::Value>>(expected)
                .unwrap();
        let expect_line_number = expected.remove("line_number").is_some();
        let mut actual: std::collections::BTreeMap<&str, serde_json::Value> =
            serde_json::from_str(actual).unwrap();
        let line_number = actual.remove("line_number");
        if expect_line_number {
            assert_eq!(line_number.map(|x| x.is_number()), Some(true));
        } else {
            assert!(line_number.is_none());
        }
        let span_id = actual.remove("span_id");
        assert_eq!(
            span_id.map(
                |x| x.is_string() && u64::from_str_radix(x.as_str().unwrap(), 10).unwrap() != 0
            ),
            Some(true)
        );
        let trace_id = actual.remove("trace_id");
        assert_eq!(
            trace_id.clone().map(
                |x| x.is_string() && u64::from_str_radix(x.as_str().unwrap(), 10).unwrap() != 0
            ),
            Some(true)
        );
        let dd_value = actual.remove("dd").unwrap();
        let dd = dd_value.as_object().unwrap();
        let dd_trace_id = dd.get("trace_id").unwrap().to_string();
        assert_eq!(dd_trace_id, trace_id.unwrap().to_string());
        assert_eq!(actual, expected);
    }

    pub(crate) struct MockWriter {
        buf: Arc<Mutex<Vec<u8>>>,
    }

    impl MockWriter {
        pub(crate) fn new(buf: Arc<Mutex<Vec<u8>>>) -> Self {
            Self { buf }
        }

        pub(crate) fn map_error<Guard>(err: TryLockError<Guard>) -> io::Error {
            match err {
                TryLockError::WouldBlock => io::Error::from(io::ErrorKind::WouldBlock),
                TryLockError::Poisoned(_) => io::Error::from(io::ErrorKind::Other),
            }
        }

        pub(crate) fn buf(&self) -> io::Result<MutexGuard<'_, Vec<u8>>> {
            self.buf.try_lock().map_err(Self::map_error)
        }
    }

    impl io::Write for MockWriter {
        fn write(&mut self, buf: &[u8]) -> io::Result<usize> {
            self.buf()?.write(buf)
        }

        fn flush(&mut self) -> io::Result<()> {
            self.buf()?.flush()
        }
    }

    #[derive(Clone, Default)]
    pub(crate) struct MockMakeWriter {
        buf: Arc<Mutex<Vec<u8>>>,
    }

    impl MockMakeWriter {
        pub(crate) fn buf(&self) -> MutexGuard<'_, Vec<u8>> {
            self.buf.lock().unwrap()
        }
    }

    impl<'a> MakeWriter<'a> for MockMakeWriter {
        type Writer = MockWriter;

        fn make_writer(&'a self) -> Self::Writer {
            MockWriter::new(self.buf.clone())
        }
    }
}
