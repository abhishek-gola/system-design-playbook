public class Address {
    private final String line;
    private final String pincode;

    public Address(String line, String pincode) {
        this.line = line;
        this.pincode = pincode;
    }

    public String line()    { return line; }
    public String pincode() { return pincode; }

    @Override
    public String toString() { return line + " " + pincode; }
}
