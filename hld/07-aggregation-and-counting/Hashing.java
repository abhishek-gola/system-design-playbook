/**
 * The bit mixing both sketches depend on.
 *
 * HyperLogLog and Count-Min Sketch are only as good as the hash underneath
 * them. HyperLogLog in particular reads the leading zeros of the hash and
 * treats that as evidence about cardinality, so a hash whose high bits move
 * predictably with the input produces confidently wrong answers. String's own
 * hashCode is a weak polynomial: "user-1" and "user-2" land 1 apart. Running it
 * through the murmur3 finaliser scatters those neighbouring values across the
 * whole 32-bit range, which is all either structure needs.
 *
 * In production you would use a real 64-bit hash (xxHash, murmur3_128) because
 * 32 bits starts colliding around a hundred thousand distinct keys. This is
 * kept short so the maths above it stays readable.
 */
public final class Hashing {

    private Hashing() {
    }

    /** The murmur3 finaliser: three shift-xors and two odd multiplies. */
    public static int mix32(int x) {
        x ^= (x >>> 16);
        x *= 0x85ebca6b;
        x ^= (x >>> 13);
        x *= 0xc2b2ae35;
        x ^= (x >>> 16);
        return x;
    }

    /**
     * A seeded hash, so the Count-Min Sketch can get d independent-looking
     * functions out of one implementation. Mixing the seed first stops nearby
     * seed values producing correlated rows.
     */
    public static int hash(String key, int seed) {
        return mix32(key.hashCode() ^ mix32(seed));
    }
}
