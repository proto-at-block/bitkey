pub use utoipa::openapi::OpenApi;
pub use utoipa_swagger_ui::Url;

pub type SwaggerEndpoint = (Url<'static>, OpenApi);
