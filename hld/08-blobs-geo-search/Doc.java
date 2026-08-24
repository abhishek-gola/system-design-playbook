/**
 * One indexed document. A post, in the Facebook Post Search framing.
 *
 * `likes` is here as a static quality signal: something known about the
 * document independent of the query, cheap to read, and good enough to decide
 * which few hundred documents deserve the expensive scorer. Recency, author
 * reputation and a spam score do the same job in a real system.
 */
public record Doc(int id, String author, String text, int likes) {
}
