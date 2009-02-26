/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.remoting.core;

import java.util.LinkedList;
import java.util.concurrent.Executor;
import org.jboss.xnio.log.Logger;

/**
 * An executor that always runs all tasks in order, using a delegate executor to run the tasks.
 * <p/>
 * More specifically, any call B to the {@link #execute(Runnable)} method that happens-after another call A to the
 * same method, will result in B's task running after A's.
 */
final class OrderedExecutor implements Executor {
    private static final Logger log = Logger.getLogger(OrderedExecutor.class);

    // @protectedby tasks
    private final LinkedList<Runnable> tasks = new LinkedList<Runnable>();
    // @protectedby tasks
    private boolean running;
    private final Executor parent;
    private final Runnable runner;

    /**
     * Construct a new instance.
     *
     * @param parent the parent executor
     */
    OrderedExecutor(final Executor parent) {
        this.parent = parent;
        runner = new Runnable() {
            public void run() {
                for (;;) {
                    final Runnable task;
                    synchronized(tasks) {
                        task = tasks.poll();
                        if (task == null) {
                            running = false;
                            return;
                        }
                    }
                    try {
                        task.run();
                    } catch (Throwable t) {
                        log.error(t, "Runnable task %s failed", task);
                    }
                }
            }
        };
    }

    /**
     * Run a task.
     *
     * @param command the task to run.
     */
    public void execute(Runnable command) {
        synchronized(tasks) {
            tasks.add(command);
            if (! running) {
                running = true;
                boolean ok = false;
                try {
                    parent.execute(runner);
                    ok = true;
                } finally {
                    if (! ok) {
                        running = false;
                    }
                }
            }
        }
    }
}
