/**
 * One data point on the ingest path: a named metric, the host it came from, the
 * tenant that owns the host, when it happened, and the value.
 *
 * Immutable and tiny on purpose. Every field here is a candidate shard key, and
 * the whole exercise in this folder is that the choice between them decides
 * whether the system works at ten times the load.
 */
public record MetricWrite(String metric,
                          String host,
                          String tenant,
                          long timestampMillis,
                          double value) {
}
