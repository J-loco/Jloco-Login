package org.starloco.locos.net;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs tasks one at a time, in submission order, on a shared executor (virtual threads). Each connection
 * has one, so its packets are handled in order while blocking database calls stay off the Netty event loop.
 */
public final class SerialExecutor implements Executor {

    private static final Logger log = LoggerFactory.getLogger(SerialExecutor.class);

    private final Executor delegate;
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();

    public SerialExecutor(Executor delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(Runnable task) {
        tasks.add(task);
        schedule();
    }

    private void schedule() {
        if (scheduled.compareAndSet(false, true)) {
            delegate.execute(this::drain);
        }
    }

    private void drain() {
        try {
            Runnable task;
            while ((task = tasks.poll()) != null) {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    log.error("Unexpected error while handling a packet", e);
                }
            }
        } finally {
            scheduled.set(false);
            if (!tasks.isEmpty()) {
                schedule();
            }
        }
    }
}
