use opentelemetry::trace::TraceError;
use opentelemetry_sdk::{trace::Tracer, Resource};
use opentelemetry_semantic_conventions::resource::SERVICE_NAME;

pub fn init_tracer(resource: Resource) -> Result<Tracer, TraceError> {
    opentelemetry::global::set_text_map_propagator(opentelemetry_jaeger::Propagator::default());

    let mut pipeline = opentelemetry_jaeger::new_agent_pipeline();
    if let Some(service_name) = resource.get(SERVICE_NAME) {
        pipeline = pipeline.with_service_name(service_name.to_string());
    }
    if cfg!(target_os = "macos") {
        // Mac OS has a low packet size limit
        pipeline = pipeline
            .with_auto_split_batch(true)
            .with_max_packet_size(9216)
    }
    pipeline
        .with_trace_config(
            opentelemetry_sdk::trace::config()
                .with_resource(resource)
                .with_sampler(opentelemetry_sdk::trace::Sampler::AlwaysOn),
        )
        .install_batch(opentelemetry_sdk::runtime::Tokio)
}
