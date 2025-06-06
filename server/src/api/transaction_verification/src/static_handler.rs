use axum::http::{header, Uri};
use axum::response::{Html, IntoResponse, Response};
use rust_embed::RustEmbed;

#[derive(RustEmbed)]
#[folder = "resources"]
struct Resources;

// Helper function to get embedded template content
pub fn get_template() -> String {
    Resources::get("tx-verify.html")
        .map(|content| {
            std::str::from_utf8(&content.data)
                .unwrap_or_default()
                .to_string()
        })
        .unwrap_or_else(|| "Template not found".to_string())
}

// We use a wildcard matcher ("/static/*path") to match against everything
// within our defined assets directory. This is the directory on our Resources
// struct above, where folder = "resources/static/".
pub async fn static_handler(uri: Uri) -> impl IntoResponse {
    let path = uri.path().trim_start_matches('/').to_string();

    StaticFile(path)
}

// Finally, we use a fallback route for anything that didn't match.
fn not_found() -> Html<&'static str> {
    Html("<h1>404</h1><p>Not Found</p>")
}

pub struct StaticFile<T>(pub T);

impl<T> IntoResponse for StaticFile<T>
where
    T: Into<String>,
{
    fn into_response(self) -> Response {
        let path = self.0.into();

        match Resources::get(path.as_str()) {
            Some(content) => {
                let mime = mime_guess::from_path(path).first_or_octet_stream();
                ([(header::CONTENT_TYPE, mime.as_ref())], content.data).into_response()
            }
            None => not_found().into_response(),
        }
    }
}
