/**
 * A document that survived cheap retrieval on one shard, carrying the score
 * that got it through and the shard it came from.
 *
 * Keeping the shard id on the candidate is not decoration. When a coordinator
 * merges results and one shard is consistently supplying all of them, that is
 * either a genuinely skewed corpus or a bug in the routing, and you want to be
 * able to see which.
 */
public record Candidate(Doc doc, double cheapScore, int shardId) {
}
