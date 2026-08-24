public class Request {
    private final String route;
    private final String clientKey;

    public Request(String route, String clientKey) {
        this.route = route;
        this.clientKey = clientKey;
    }

    public String route()     { return route; }
    public String clientKey() { return clientKey; }
}
