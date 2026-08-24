/**
 * Both caches here are cache-aside: the application asks the cache, and on a
 * miss the application loads from the database and puts the value back. The
 * cache never talks to the database itself.
 *
 * That is the default you should name in an interview, and the reason is
 * failure behaviour rather than performance — if a cache-aside Redis dies, the
 * system degrades to "slow", because the application still knows how to reach
 * the database. In write-through the cache is on the critical path for
 * correctness, so when it dies you are down, not slow.
 */
public interface Cache {
    String get(String key);

    String name();
}
