import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The version the follow-up question is asking for.
 *
 * Each subscriber gets its own bounded queue and its own thread. publish()
 * hands the event to every queue and returns — a slow subscriber can no longer
 * hold up the publisher or its peers.
 *
 * The bound is the important part. An unbounded queue is not backpressure, it
 * is an OutOfMemoryError with extra steps. Once the queue is bounded you are
 * forced to answer "and what happens when it's full", which is the real
 * question underneath the pattern.
 */
public class AsyncTopic implements AutoCloseable {

    private static final Event POISON = new Event("__stop__", "", -1);

    private final String name;
    private final List<Channel> channels = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<Event> deadLetters = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public AsyncTopic(String name) {
        this.name = name;
    }

    public void subscribe(Subscriber subscriber, int queueCapacity, OverflowPolicy policy) {
        Channel channel = new Channel(subscriber, queueCapacity, policy);
        channels.add(channel);
        channel.start();
    }

    public void publish(Event event) {
        for (Channel channel : channels) {
            channel.offer(event);
        }
    }

    /** Waits for every subscriber to finish what it has queued. Idempotent. */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        for (Channel channel : channels) {
            channel.stop();
        }
        for (Channel channel : channels) {
            channel.join();
        }
    }

    public List<Event> deadLetters() {
        return new ArrayList<>(deadLetters);
    }

    public void report() {
        System.out.println("  topic '" + name + "' after close:");
        for (Channel c : channels) {
            System.out.println("    " + pad(c.subscriber.name())
                    + " policy=" + pad(c.policy.name())
                    + " delivered=" + c.delivered.get()
                    + " dropped=" + c.dropped.get()
                    + " failed=" + c.failed.get());
        }
        if (!deadLetters.isEmpty()) {
            System.out.println("    dead-letter queue holds " + deadLetters.size()
                    + " event(s) — somebody has to drain this, and in the interview"
                    + " you should say who");
        }
    }

    private static String pad(String s) {
        return (s + "                    ").substring(0, 20);
    }

    // ------------------------------------------------------------------

    private final class Channel {
        private final Subscriber subscriber;
        private final OverflowPolicy policy;
        private final BlockingQueue<Event> queue;
        private final Thread worker;

        private final AtomicInteger delivered = new AtomicInteger();
        private final AtomicInteger dropped   = new AtomicInteger();
        private final AtomicInteger failed    = new AtomicInteger();

        Channel(Subscriber subscriber, int capacity, OverflowPolicy policy) {
            this.subscriber = subscriber;
            this.policy = policy;
            this.queue = new ArrayBlockingQueue<>(capacity);
            this.worker = new Thread(this::run, "sub-" + subscriber.name());
            this.worker.setDaemon(true);
        }

        void start() { worker.start(); }

        void offer(Event event) {
            switch (policy) {
                case DROP_NEWEST -> {
                    if (!queue.offer(event)) dropped.incrementAndGet();
                }
                case BLOCK -> {
                    try {
                        queue.put(event);                 // the publisher waits here
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                case DEAD_LETTER -> {
                    if (!queue.offer(event)) {
                        deadLetters.add(event);
                        dropped.incrementAndGet();
                    }
                }
            }
        }

        private void run() {
            while (true) {
                Event event;
                try {
                    event = queue.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (event == POISON) return;

                // Same per-subscriber isolation as the synchronous Topic. A
                // handler that throws must not kill its own worker thread — do
                // that and the subscriber silently stops receiving anything,
                // which is far worse than a logged failure.
                try {
                    subscriber.onEvent(event);
                    delivered.incrementAndGet();
                } catch (RuntimeException e) {
                    failed.incrementAndGet();
                }
            }
        }

        void stop() {
            try {
                queue.put(POISON);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        void join() {
            try {
                worker.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
