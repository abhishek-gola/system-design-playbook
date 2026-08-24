import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Splitting a file into chunks, two ways.
 *
 * Chunking buys you two things at once, which is why it turns up in every file
 * sync design: uploads become resumable, because a failed upload only loses the
 * chunk in flight, and uploads become deduplicated, because you can hash each
 * chunk and skip the ones the server already holds.
 *
 * The second benefit is the one that depends on how you cut. Fixed-size
 * chunking cuts every 4MB regardless of content, which is trivial to implement
 * and falls apart the moment anybody inserts bytes near the start of a file:
 * every subsequent boundary shifts, every chunk hash changes, and a one-byte
 * edit re-uploads the entire file.
 *
 * Content-defined chunking cuts where the *content* says to. Slide a window
 * over the bytes, hash the window, and declare a boundary whenever the low bits
 * of that hash happen to be zero. Boundaries are then a property of the bytes
 * around them rather than of their position, so inserting data at the start
 * shifts the first boundary and leaves every later one exactly where it was.
 * That is the whole idea, and it is what rsync and every backup product built
 * since have been doing.
 */
public final class Chunker {

    public record Chunk(int offset, int length, String hash) {
    }

    private Chunker() {
    }

    public static List<Chunk> fixedSize(byte[] data, int chunkSize) {
        List<Chunk> out = new ArrayList<>();
        for (int i = 0; i < data.length; i += chunkSize) {
            int length = Math.min(chunkSize, data.length - i);
            out.add(new Chunk(i, length, hashOf(data, i, length)));
        }
        return out;
    }

    /**
     * @param windowSize how many bytes the boundary decision looks at
     * @param mask       a value like 0x3F; a boundary is declared when
     *                   (windowHash and mask) is zero, so the average chunk is
     *                   mask+1 bytes long
     * @param minChunk   floor, so a run of unlucky bytes cannot produce
     *                   thousands of tiny chunks and drown you in metadata
     * @param maxChunk   ceiling, so a run of lucky bytes cannot produce one
     *                   enormous chunk and destroy resumability
     */
    public static List<Chunk> contentDefined(byte[] data, int windowSize, int mask, int minChunk, int maxChunk) {
        List<Chunk> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < data.length; i++) {
            int length = i - start + 1;

            boolean boundary = false;
            if (length >= minChunk && i >= windowSize - 1) {
                boundary = (windowHash(data, i - windowSize + 1, windowSize) & mask) == 0;
            }

            if (boundary || length >= maxChunk || i == data.length - 1) {
                out.add(new Chunk(start, length, hashOf(data, start, length)));
                start = i + 1;
            }
        }
        return out;
    }

    /**
     * Recomputed from scratch at every position, which is O(n * windowSize).
     * Production uses a genuine rolling hash — a Rabin fingerprint or buzhash —
     * where advancing one byte is a shift, an xor to add the entering byte and
     * an xor to remove the leaving one, so the whole scan is O(n). That is an
     * optimisation of this, not a different idea, and doing it the slow and
     * obviously correct way here keeps the interesting part visible.
     */
    private static int windowHash(byte[] data, int from, int length) {
        int h = 0;
        for (int j = from; j < from + length; j++) {
            h = h * 31 + (data[j] & 0xff);
        }
        // Mix, so the low bits we test depend on the whole window rather than
        // mostly on the last byte. Without this, boundaries cluster.
        h ^= (h >>> 16);
        h *= 0x85ebca6b;
        h ^= (h >>> 13);
        return h;
    }

    /**
     * The chunk's identity. Truncated to six bytes here purely so the demo
     * output fits on a line; dedup in production keeps the full digest, because
     * a collision means silently serving one customer another customer's bytes.
     * That is the argument for a cryptographic hash rather than a fast one.
     */
    private static String hashOf(byte[] data, int offset, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(data, offset, length);
            byte[] full = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(String.format("%02x", full[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every JVM is required to ship SHA-256", e);
        }
    }
}
