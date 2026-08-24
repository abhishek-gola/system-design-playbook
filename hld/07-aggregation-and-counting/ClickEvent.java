/**
 * One ad click.
 *
 * The only field that carries real weight is eventTimeMs: the moment the click
 * happened on the user's device, not the moment it reached us. Everything in
 * this folder that is interesting comes from those two times being different.
 *
 * In a real pipeline this is a record on a Kafka topic partitioned by adId, so
 * that every click for one ad lands on one partition and one operator instance
 * owns its running count. Partitioning by userId instead would spread each ad's
 * clicks across every partition and force a shuffle before you could aggregate,
 * which is the choice interviewers poke at.
 */
public record ClickEvent(String adId, String userId, long eventTimeMs) {
}
