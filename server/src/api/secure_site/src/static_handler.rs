use axum::{
    http::{header, Uri},
    response::{Html, IntoResponse, Response},
    routing::get,
    Router,
};
use rust_embed::RustEmbed;

#[derive(Clone)]
pub struct RouteState();

impl RouteState {
    pub fn secure_site_router(&self) -> Router {
        Router::new()
            .route("/static/*file", get(secure_static_handler))
            .with_state(self.to_owned())
    }
}

#[derive(RustEmbed)]
#[folder = "resources"]
pub struct CommonResources;

pub async fn secure_static_handler(uri: Uri) -> impl IntoResponse {
    let path = uri.path().trim_start_matches('/').to_string();
    serve_static_file::<CommonResources>(&path)
}

// Generic helper to create a static file response from any RustEmbed struct
fn serve_static_file<R>(path: &str) -> Response
where
    R: RustEmbed,
{
    match R::get(path) {
        Some(content) => {
            let mime = mime_guess::from_path(path).first_or_octet_stream();
            ([(header::CONTENT_TYPE, mime.as_ref())], content.data).into_response()
        }
        None => Html("<h1>404</h1><p>Not Found</p>").into_response(),
    }
}

// Generic helper to get template content from any RustEmbed struct
pub fn get_embedded_template<R>(template_name: &str) -> String
where
    R: RustEmbed,
{
    R::get(template_name)
        .map(|content| {
            std::str::from_utf8(&content.data)
                .unwrap_or_default()
                .to_string()
        })
        .unwrap_or_else(|| "Template not found".to_string())
}

pub fn inject_json_into_template(
    template: &str,
    script_id: &str,
    json_params: serde_json::Value,
) -> Result<String, String> {
    let params_json = serde_json::to_string(&json_params)
        .map_err(|e| format!("Failed to serialize JSON: {}", e))?;

    let pattern = format!(
        r#"<script type="application/json" id="{}">(\s*\{{[\s\S]*?\}})\s*</script>"#,
        regex::escape(script_id)
    );

    let replacement = format!(
        r#"<script type="application/json" id="{}">{}</script>"#,
        script_id, params_json
    );

    match regex::Regex::new(&pattern) {
        Ok(regex) => Ok(regex.replace(template, replacement.as_str()).to_string()),
        Err(_e) => {
            // Fallback to simple string replacement if regex fails
            let simple_placeholder = format!(
                r#"<script type="application/json" id="{}">{{}}</script>"#,
                script_id
            );
            Ok(template.replace(&simple_placeholder, &replacement))
        }
    }
}

// Generic static handler that tries local resources first, then common resources
pub async fn serve_with_fallback<L>(uri: Uri) -> impl IntoResponse
where
    L: RustEmbed,
{
    let path = uri.path().trim_start_matches('/').to_string();
    if L::get(&path).is_some() {
        return serve_static_file::<L>(&path);
    }
    serve_static_file::<CommonResources>(&path)
}

pub fn html_error<E: std::fmt::Display>(
    status: axum::http::StatusCode,
    error: E,
) -> (
    axum::http::StatusCode,
    [(axum::http::header::HeaderName, &'static str); 1],
    Html<String>,
) {
    (
        status,
        [(header::CONTENT_TYPE, "text/html; charset=utf-8")],
        Html(format!(
            "<html><body><h1>Error</h1><p>{}</p></body></html>",
            error
        )),
    )
}
