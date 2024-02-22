use std::env;
use std::fs::File;
use std::io::Read;

use serde_json::Value;
use wiremock::matchers::{body_json, header, method, path};
use wiremock::{Mock, MockServer, ResponseTemplate};

const ASM_PATH: &str = "secrets-manager/";

pub async fn create_mock(
    server: &MockServer,
    target_header: &str,
    request_body: &str,
    status: u16,
    response_filename: &str,
) {
    let request_json: Value =
        serde_json::from_str(request_body).expect("Failed to parse request json");

    let responder = ResponseTemplate::new(status)
        .set_body_raw(response_body(response_filename), "application/json");

    Mock::given(method("POST"))
        .and(path(ASM_PATH))
        .and(header("X-Amz-Target", target_header))
        .and(body_json(request_json))
        .respond_with(responder)
        .expect(1..)
        .named(response_filename)
        .mount(server)
        .await;
}

pub fn mock_url(server: &MockServer) -> String {
    format!("{}/{}", &server.uri(), ASM_PATH)
}

fn response_body(response_filename: &str) -> String {
    let current_dir = env::current_dir()
        .unwrap()
        .into_os_string()
        .into_string()
        .unwrap();

    let file_path = format!("{current_dir}/src/tests/resources/mocks/{response_filename}");
    let mut file = File::open(file_path).expect("Failed to open file");
    let mut response_body = String::new();
    file.read_to_string(&mut response_body)
        .expect("Failed to read file");

    response_body
}
