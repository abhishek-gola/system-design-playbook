/**
 * Distinct counts in kilobytes instead of gigabytes.
 *
 * The intuition, which is what an interviewer wants rather than the algebra:
 * hash every value and look at how many leading zeros the hash has. In a good
 * hash, half the values start with a zero bit, a quarter start with two zeros,
 * one in 2^k starts with k zeros. So if the longest run of leading zeros you
 * have ever seen is 12, you have probably seen about 2^12 distinct values. One
 * number, any cardinality.
 *
 * That single estimate has enormous variance — one unlucky value ruins it. So
 * split the hash: use the top p bits to pick one of m = 2^p registers, and
 * track the longest run of zeros per register. Now you have m independent
 * estimates and you average them. HyperLogLog uses a harmonic mean, because
 * that is what damps the outliers that a plain mean does not.
 *
 * Error is 1.04/sqrt(m), and it is entirely a function of the register count.
 * That is a nice property to state out loud: the accuracy does not degrade as
 * cardinality grows. A kilobyte of registers gives you the same three percent
 * whether you are counting a thousand viewers or a hundred million.
 *
 * The other property worth naming, because it is why this and not a HashSet:
 * two sketches merge by taking the per-register maximum. Every shard sketches
 * its own slice, you union them at query time, and there is no shuffle. Try
 * that with exact counting and you are shipping the whole key space around.
 *
 * A 32-bit hash is used here to keep the code short. It starts colliding around
 * a hundred million distinct values, at which point the large-range correction
 * this implementation omits would also start to matter. Production uses 64-bit
 * hashes, and Redis's PFADD uses a sparse encoding for small cardinalities so
 * you do not pay for 12KB of registers to count nine things.
 */
public final class HyperLogLog {

    private final int p;
    private final int m;
    private final byte[] registers;

    public HyperLogLog(int p) {
        if (p < 4 || p > 16) {
            throw new IllegalArgumentException("p must be between 4 and 16");
        }
        this.p = p;
        this.m = 1 << p;
        this.registers = new byte[m];
    }

    public void add(String value) {
        int h = Hashing.mix32(value.hashCode());

        // Top p bits choose the register. Using the top bits rather than a
        // modulo keeps the register choice and the leading-zero count reading
        // disjoint parts of the hash, which they have to be for the estimates
        // to be independent.
        int register = h >>> (32 - p);

        int remainder = h << p;
        int maxRank = 32 - p + 1;
        int rank = Math.min(Integer.numberOfLeadingZeros(remainder) + 1, maxRank);

        if (rank > registers[register]) {
            registers[register] = (byte) rank;
        }
    }

    public long estimate() {
        double harmonic = 0.0;
        int emptyRegisters = 0;
        for (byte r : registers) {
            harmonic += Math.pow(2.0, -r);
            if (r == 0) {
                emptyRegisters++;
            }
        }

        double alpha = 0.7213 / (1.0 + 1.079 / m);
        double raw = alpha * m * m / harmonic;

        // Small-range correction. With few distinct values most registers are
        // still empty, and counting the empties is a better estimator than the
        // harmonic mean. This is linear counting, and it is the reason a
        // HyperLogLog gives an exact answer for tiny inputs.
        if (raw <= 2.5 * m && emptyRegisters > 0) {
            return Math.round(m * Math.log((double) m / emptyRegisters));
        }
        return Math.round(raw);
    }

    /** Merging is a per-register maximum, which is why sketches shard so well. */
    public void mergeFrom(HyperLogLog other) {
        if (other.p != this.p) {
            throw new IllegalArgumentException("sketches must have the same register count to merge");
        }
        for (int i = 0; i < m; i++) {
            if (other.registers[i] > registers[i]) {
                registers[i] = other.registers[i];
            }
        }
    }

    public int registerCount() {
        return m;
    }

    public int sizeInBytes() {
        return m;
    }

    public double standardErrorPercent() {
        return 100.0 * 1.04 / Math.sqrt(m);
    }
}
