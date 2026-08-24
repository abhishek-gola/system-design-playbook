/** Value type: "e4" in, file and rank out. Immutable, so it is safe to store. */
public final class Square {
    private final String name;

    public Square(String name) {
        if (name.length() != 2) throw new IllegalArgumentException("bad square: " + name);
        this.name = name;
    }

    public String name() { return name; }

    @Override
    public boolean equals(Object other) {
        return other instanceof Square s && s.name.equals(name);
    }

    @Override
    public int hashCode() { return name.hashCode(); }

    @Override
    public String toString() { return name; }
}
