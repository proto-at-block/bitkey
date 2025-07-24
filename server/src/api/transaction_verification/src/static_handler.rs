use axum::{http::Uri, response::IntoResponse};
use rust_embed::RustEmbed;

#[derive(RustEmbed)]
#[folder = "resources"]
struct LocalResources;

pub fn get_template() -> String {
    secure_site::static_handler::get_embedded_template::<LocalResources>("tx-verify.html")
}

pub async fn static_handler(uri: Uri) -> impl IntoResponse {
    secure_site::static_handler::serve_with_fallback::<LocalResources>(uri).await
}
