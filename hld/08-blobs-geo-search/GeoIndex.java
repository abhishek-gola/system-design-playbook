import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Places bucketed by geohash prefix, which is all a geospatial index needs to
 * be when the objects do not move.
 *
 * In production this map is a database index or a Redis key per cell. Nothing
 * about the shape changes: the cell is the shard key, a lookup is exact-match
 * on a string, and the expensive part — the distance calculation — only ever
 * runs on the handful of rows a cell lookup returned.
 *
 * The precision is the one tuning decision. Cells that are too large mean you
 * scan thousands of places to return ten; cells that are too small mean the
 * nine cells you search do not cover your radius and you have to search
 * twenty-five. Five characters is roughly 5km across, which suits a "nearby
 * restaurants" radius of one or two kilometres.
 */
public final class GeoIndex {

    public record Hit(Place place, double metres) {
    }

    private final int precision;
    private final Map<String, List<Place>> cells = new LinkedHashMap<>();

    public GeoIndex(int precision) {
        this.precision = precision;
    }

    public void add(Place place) {
        String cell = GeoHash.encode(place.lat(), place.lon(), precision);
        cells.computeIfAbsent(cell, k -> new ArrayList<>()).add(place);
    }

    /**
     * The version that looks right and is wrong. It only ever considers places
     * in the caller's own cell, so anything just over a boundary is invisible
     * no matter how close it is.
     */
    public List<Hit> naiveSingleCell(double lat, double lon, double radiusMetres) {
        String cell = GeoHash.encode(lat, lon, precision);
        return filterByExactDistance(candidatesIn(cell), lat, lon, radiusMetres);
    }

    /** The version that works: nine cells, then an exact distance filter. */
    public List<Hit> nearby(double lat, double lon, double radiusMetres) {
        String home = GeoHash.encode(lat, lon, precision);
        List<Place> candidates = new ArrayList<>(candidatesIn(home));
        for (String neighbour : GeoHash.neighbours(home)) {
            candidates.addAll(candidatesIn(neighbour));
        }
        return filterByExactDistance(candidates, lat, lon, radiusMetres);
    }

    public int candidatesScannedByNaive(double lat, double lon) {
        return candidatesIn(GeoHash.encode(lat, lon, precision)).size();
    }

    public int candidatesScannedByNineCell(double lat, double lon) {
        String home = GeoHash.encode(lat, lon, precision);
        int total = candidatesIn(home).size();
        for (String neighbour : GeoHash.neighbours(home)) {
            total += candidatesIn(neighbour).size();
        }
        return total;
    }

    public String cellFor(double lat, double lon) {
        return GeoHash.encode(lat, lon, precision);
    }

    public int cellCount() {
        return cells.size();
    }

    private List<Place> candidatesIn(String cell) {
        List<Place> found = cells.get(cell);
        return found == null ? new ArrayList<>() : found;
    }

    /**
     * The cell lookup is a coarse filter and nothing more. A geohash cell is a
     * rectangle and a radius query is a circle, so some of what the cells
     * return is genuinely outside the radius. Haversine settles it, and it only
     * runs on the small candidate set — which is the entire point of doing the
     * cell lookup first.
     */
    private static List<Hit> filterByExactDistance(List<Place> candidates, double lat, double lon, double radiusMetres) {
        List<Hit> hits = new ArrayList<>();
        for (Place place : candidates) {
            double metres = GeoHash.haversineMetres(lat, lon, place.lat(), place.lon());
            if (metres <= radiusMetres) {
                hits.add(new Hit(place, metres));
            }
        }
        hits.sort((a, b) -> {
            int byDistance = Double.compare(a.metres(), b.metres());
            return byDistance != 0 ? byDistance : a.place().name().compareTo(b.place().name());
        });
        return hits;
    }
}
