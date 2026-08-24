import java.util.Random;

/**
 * Frequency estimates for heavy hitters, in fixed memory.
 *
 * A d-by-w grid of counters and d hash functions. To record a key, increment
 * one cell in each row. To estimate it, read one cell from each row and take
 * the minimum.
 *
 * The minimum is the trick. Every cell a key touches holds that key's true
 * count plus whatever other keys collided into the same cell, so every cell is
 * an overestimate. Take the smallest one and you get the reading least polluted
 * by collisions. Which means the error is one-sided, and that is the sentence
 * to say out loud: a Count-Min Sketch never undercounts. It will tell you a
 * rare key is more common than it is; it will never tell you a heavy hitter is
 * rare. For "find me the abusive IPs" or "find me the trending videos", one-
 * sided error in that direction is exactly the direction you want it in.
 *
 * The other half of the answer is where the error goes. Overestimation is
 * roughly the stream length divided by the width, spread across every key. For
 * a heavy hitter with millions of views that is noise. For a key in the tail
 * with three views it can be a large relative error, and the honest framing is
 * that the sketch is accurate about the things it is for and inaccurate about
 * everything else.
 *
 * Width controls the error, depth controls the probability of exceeding it.
 * Widening the rows is what you do when the estimates are off; adding rows is
 * what you do when you need a stronger guarantee that they are not.
 */
public final class CountMinSketch {

    private final int depth;
    private final int width;
    private final long[][] table;
    private final int[] seeds;
    private long totalObserved = 0;

    public CountMinSketch(int depth, int width) {
        this.depth = depth;
        this.width = width;
        this.table = new long[depth][width];
        this.seeds = new int[depth];
        // A fixed seed, so the demo prints identical numbers every run. In
        // production the seeds are fixed too, for a different reason: every
        // shard has to hash a key the same way or the sketches cannot merge.
        Random rnd = new Random(1234);
        for (int i = 0; i < depth; i++) {
            seeds[i] = rnd.nextInt();
        }
    }

    public void add(String key, long count) {
        for (int row = 0; row < depth; row++) {
            table[row][bucket(key, row)] += count;
        }
        totalObserved += count;
    }

    public long estimate(String key) {
        long min = Long.MAX_VALUE;
        for (int row = 0; row < depth; row++) {
            min = Math.min(min, table[row][bucket(key, row)]);
        }
        return min;
    }

    private int bucket(String key, int row) {
        // floorMod rather than %, because the hash is signed and a negative
        // index here would be a very annoying afternoon.
        return Math.floorMod(Hashing.hash(key, seeds[row]), width);
    }

    public long totalObserved() {
        return totalObserved;
    }

    public int depth() {
        return depth;
    }

    public int width() {
        return width;
    }

    public int sizeInBytes() {
        return depth * width * Long.BYTES;
    }
}
