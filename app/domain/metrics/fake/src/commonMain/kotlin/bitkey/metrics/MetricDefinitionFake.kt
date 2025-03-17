package bitkey.metrics

data object MetricDefinitionFake : MetricDefinition {
  override val name: MetricName = MetricName("test_metric")

  sealed class Variants(override val name: String) : Variant<MetricDefinitionFake> {
    data object Variant1 : Variants("test_variant_1")

    data object Variant2 : Variants("test_variant_2")
  }
}
