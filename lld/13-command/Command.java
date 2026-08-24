/**
 * Two methods, and a name you can write to a log.
 *
 * describe() is not decoration. A command you cannot print is a command you
 * cannot audit, and "what did this user actually do" is the question that makes
 * the pattern worth its weight outside of undo.
 */
public interface Command {

    void execute();

    void undo();

    String describe();
}
