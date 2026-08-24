public enum LogLevel {
    DEBUG, INFO, WARN, ERROR;

    public boolean atLeast(LogLevel threshold) {
        return this.ordinal() >= threshold.ordinal();
    }
}
