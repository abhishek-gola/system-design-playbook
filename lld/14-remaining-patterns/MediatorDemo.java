import java.util.ArrayList;
import java.util.List;

/**
 * Mediator: turn an n-squared mesh of references into n spokes.
 *
 * Without it, every participant holds a reference to every other one, and
 * adding a fifth means editing four existing classes. With it, each one knows
 * only the hub.
 *
 * The cost, which you should name: the hub accumulates everybody's coordination
 * logic and slowly becomes a god object. That is a real failure mode, not a
 * theoretical one, and it is why Mediator is rarer than it looks like it should
 * be.
 */
public class MediatorDemo {

    interface ChatRoom {
        void join(Participant participant);
        void send(String from, String message);
    }

    static class Participant {
        final String name;
        private ChatRoom room;
        final List<String> inbox = new ArrayList<>();

        Participant(String name) { this.name = name; }

        void setRoom(ChatRoom room) { this.room = room; }
        void say(String message)    { room.send(name, message); }
        void receive(String line)   { inbox.add(line); }
    }

    static class Room implements ChatRoom {
        private final List<Participant> members = new ArrayList<>();

        @Override
        public void join(Participant participant) {
            members.add(participant);
            participant.setRoom(this);
        }

        @Override
        public void send(String from, String message) {
            for (Participant member : members) {
                if (!member.name.equals(from)) {
                    member.receive(from + ": " + message);
                }
            }
        }
    }

    public static void show() {
        Room room = new Room();
        Participant anita = new Participant("anita");
        Participant raj = new Participant("raj");
        Participant sam = new Participant("sam");
        room.join(anita);
        room.join(raj);
        room.join(sam);

        anita.say("standup in five");
        raj.say("on my way");

        System.out.println("    raj's inbox:  " + raj.inbox);
        System.out.println("    sam's inbox:  " + sam.inbox);
        System.out.println("    Nobody holds a reference to anybody else. A fourth member is");
        System.out.println("    one join() call and no edits to the existing three.");
    }
}
