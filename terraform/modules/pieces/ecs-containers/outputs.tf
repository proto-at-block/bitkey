output "containers" {
  value = [
    module.main_container.json_map_object,
    # If a task definition is created without environment variables, ECS will return
    # an empty list for environment when queried. Set it to empty list here to prevent
    # the no-op diff.
    merge({ environment : [] }, module.fluentbit_container.json_map_object),
    module.datadog_container.json_map_object,
  ]
}
