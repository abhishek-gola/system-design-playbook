/**
 * A static business, in the Yelp sense: it has a location and the location does
 * not change.
 *
 * That "does not change" is the whole reason Yelp and Uber are different
 * problems even though both are "find things near me". Static places are a read
 * problem — index once, query a lot. Drivers pinging their location every four
 * seconds are a write problem with a geospatial index attached, and the answer
 * there is to keep current positions in memory or Redis with a short TTL and
 * not persist every ping. Saying that unprompted skips ten minutes of being led
 * towards it.
 */
public record Place(String name, double lat, double lon) {
}
