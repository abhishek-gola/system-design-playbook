import java.util.ArrayList;
import java.util.List;

/**
 * Turning two dimensions into one.
 *
 * The problem an interviewer is really asking about: an index on latitude and
 * an index on longitude cannot answer "within 5km of here" efficiently. A
 * B-tree gives you one ordered dimension, so the best it can do is scan a
 * stripe of the world matching the latitude range and check every row in it
 * against the longitude. At city density that stripe is enormous.
 *
 * A geohash interleaves the bits of latitude and longitude and encodes the
 * result in base 32. Each character narrows the box: one character is a chunk
 * of a continent, five characters is roughly 5km across, eight is street level.
 * Because the bits interleave, two points that are close together usually share
 * a long prefix — so a prefix match becomes a bounding box query, an ordinary
 * string index does the work, and sharding by prefix keeps nearby data on the
 * same node.
 *
 * "Usually" is doing real work in that sentence, and it is the catch worth
 * raising yourself. Two points either side of a cell boundary can be ten metres
 * apart and share no prefix at all, because the boundary is where the high bits
 * flip. That is why every real proximity query searches the target cell plus
 * its eight neighbours and then filters by exact distance, and why quadtrees,
 * S2 and H3 exist.
 */
public final class GeoHash {

    /** No a, i, l or o: they are the characters people mistype and misread. */
    private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";

    public record Box(double minLat, double maxLat, double minLon, double maxLon) {

        public double centreLat() {
            return (minLat + maxLat) / 2;
        }

        public double centreLon() {
            return (minLon + maxLon) / 2;
        }

        public double latHeight() {
            return maxLat - minLat;
        }

        public double lonWidth() {
            return maxLon - minLon;
        }
    }

    private GeoHash() {
    }

    /**
     * Binary search on both axes at once, longitude first, alternating. Each
     * bit says "upper half" or "lower half" of the range still in play, and
     * every five bits become one base-32 character.
     */
    public static String encode(double lat, double lon, int precision) {
        double minLat = -90, maxLat = 90, minLon = -180, maxLon = 180;
        StringBuilder out = new StringBuilder();
        boolean longitudeTurn = true;
        int bitsFilled = 0;
        int value = 0;

        while (out.length() < precision) {
            if (longitudeTurn) {
                double mid = (minLon + maxLon) / 2;
                if (lon >= mid) {
                    value = value * 2 + 1;
                    minLon = mid;
                } else {
                    value = value * 2;
                    maxLon = mid;
                }
            } else {
                double mid = (minLat + maxLat) / 2;
                if (lat >= mid) {
                    value = value * 2 + 1;
                    minLat = mid;
                } else {
                    value = value * 2;
                    maxLat = mid;
                }
            }
            longitudeTurn = !longitudeTurn;

            if (++bitsFilled == 5) {
                out.append(BASE32.charAt(value));
                bitsFilled = 0;
                value = 0;
            }
        }
        return out.toString();
    }

    /** The same walk backwards, which recovers the box rather than a point. A geohash is an area, never a location. */
    public static Box decode(String hash) {
        double minLat = -90, maxLat = 90, minLon = -180, maxLon = 180;
        boolean longitudeTurn = true;

        for (int i = 0; i < hash.length(); i++) {
            int value = BASE32.indexOf(hash.charAt(i));
            for (int bit = 4; bit >= 0; bit--) {
                boolean upperHalf = ((value >> bit) & 1) == 1;
                if (longitudeTurn) {
                    double mid = (minLon + maxLon) / 2;
                    if (upperHalf) {
                        minLon = mid;
                    } else {
                        maxLon = mid;
                    }
                } else {
                    double mid = (minLat + maxLat) / 2;
                    if (upperHalf) {
                        minLat = mid;
                    } else {
                        maxLat = mid;
                    }
                }
                longitudeTurn = !longitudeTurn;
            }
        }
        return new Box(minLat, maxLat, minLon, maxLon);
    }

    /**
     * The eight cells around this one, found by stepping one box width in each
     * direction from the centre and re-encoding.
     *
     * Real libraries use base-32 neighbour lookup tables so this is pure string
     * manipulation with no floating point anywhere. The version here is easier
     * to read and behaves the same away from the poles and the antimeridian,
     * which is a limitation worth admitting rather than hiding — it is also one
     * of the reasons S2 and H3 exist, since they cover the sphere without the
     * seams a lat/lon grid has.
     */
    public static List<String> neighbours(String hash) {
        Box box = decode(hash);
        int precision = hash.length();
        List<String> out = new ArrayList<>();

        for (int dLat = -1; dLat <= 1; dLat++) {
            for (int dLon = -1; dLon <= 1; dLon++) {
                if (dLat == 0 && dLon == 0) {
                    continue;
                }
                double lat = box.centreLat() + dLat * box.latHeight();
                double lon = box.centreLon() + dLon * box.lonWidth();
                if (lat > 90 || lat < -90) {
                    continue;
                }
                if (lon > 180) {
                    lon -= 360;
                }
                if (lon < -180) {
                    lon += 360;
                }
                String neighbour = encode(lat, lon, precision);
                if (!neighbour.equals(hash) && !out.contains(neighbour)) {
                    out.add(neighbour);
                }
            }
        }
        return out;
    }

    /** Great-circle distance. The exact filter that runs after the cell lookup has narrowed the candidates. */
    public static double haversineMetres(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusMetres = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * earthRadiusMetres * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
