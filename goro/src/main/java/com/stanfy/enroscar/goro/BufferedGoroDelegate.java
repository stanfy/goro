package com.stanfy.enroscar.goro;

import android.util.Log;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.stanfy.enroscar.goro.GoroFuture.IMMEDIATE;
import static com.stanfy.enroscar.goro.Util.checkMainThread;

/**
 * Either delegates a call to a wrapped Goro or buffers invocation data.
 */
abstract class BufferedGoroDelegate extends Goro {

  private static final boolean DEBUG = false;

  /** Delegate instance. */
  private Goro delegate;

  /** Temporal array of listeners that must be added after getting service connection. */
  private final BaseListenersHandler scheduledListeners = new BaseListenersHandler(2);

  /** Postponed data. */
  private final ArrayList<Postponed> postponed = new ArrayList<>(7);

  /** Protects service instance. */
  private final Object lock = new Object();

  protected boolean updateDelegate(final Goro delegate) {
    synchronized (lock) {
      if (DEBUG) {
        Log.v("Goro", "updateDelegate(" + delegate + ") " + this, new Throwable());
      }
      if (this.delegate != delegate) {
        if (this.delegate != null && delegate != null) {
          throw new GoroException("Got a new delegate " + delegate
              + " while being attached to another " + this.delegate);
        }
        this.delegate = delegate;

        if (this.delegate != null) {
          // Delegate listeners.
          if (!scheduledListeners.taskListeners.isEmpty()) {
            for (GoroListener listener : scheduledListeners.taskListeners) {
              delegate.addTaskListener(listener);
            }
            scheduledListeners.taskListeners.clear();
          }

          // Delegate tasks.
          if (!postponed.isEmpty()) {
            for (Postponed p : postponed) {
              p.act(delegate);
            }
            postponed.clear();
          }
        }

        return true;
      }
      return false;
    }
  }

  /** Use in tests only. */
  final Goro delegate() {
    return delegate;
  }

  @Override
  public void addTaskListener(final GoroListener listener) {
    // Main thread => no sync.
    Goro goro = delegate;
    if (goro != null) {
      goro.addTaskListener(listener);
    } else {
      scheduledListeners.addTaskListener(listener);
    }
  }

  @Override
  public void removeTaskListener(final GoroListener listener) {
    // Main thread => no sync.
    Goro goro = delegate;
    if (goro != null) {
      goro.removeTaskListener(listener);
    } else {
      if (!scheduledListeners.removeTaskListener(listener)) {
        // Delegate later.
        postponed.add(new Postponed() {
          @Override
          public void act(final Goro goro) {
            goro.removeTaskListener(listener);
          }
        });
      }
    }
  }

  @Override
  public final <T> ObservableFuture<T> schedule(Callable<T> task) {
    return schedule(DEFAULT_QUEUE, task);
  }

  @Override
  public <T> ObservableFuture<T> schedule(String queueName, Callable<T> task) {
    synchronized (lock) {
      if (delegate != null) {
        return delegate.schedule(queueName, task);
      } else {
        BoundFuture<T> future = new BoundFuture<>(queueName, task);
        postponed.add(future);
        return future;
      }
    }
  }

  @Override
  public Executor getExecutor(final String queueName) {
    synchronized (lock) {
      if (delegate != null) {
        return delegate.getExecutor(queueName);
      }
      return new PostponeExecutor(queueName);
    }
  }

  @Override
  protected void removeTasksInQueue(final String queueName) {
    synchronized (lock) {
      if (delegate != null) {
        delegate.clear(queueName);
      } else {
        postponed.add(new ClearAction(queueName));
      }
    }
  }

  boolean cancelPostponed(final Postponed p) {
    synchronized (lock) {
      return postponed.remove(p);
    }
  }

  /** Some postponed action. */
  private interface Postponed {
    void act(Goro goro);
  }

  /** Postponed clear call. */
  private static final class ClearAction implements Postponed {
    /** Queue name. */
    private final String queueName;

    ClearAction(final String queueName) {
      this.queueName = queueName;
    }

    @Override
    public void act(final Goro goro) {
      goro.clear(queueName);
    }
  }

  /** Postponed action passed to an executor. */
  private static final class ExecutorAction implements Postponed {
    /** Queue name. */
    final String queue;
    /** Runnable action. */
    final Runnable command;

    ExecutorAction(final String queue, final Runnable command) {
      this.queue = queue;
      this.command = command;
    }

    @Override
    public void act(final Goro goro) {
      goro.getExecutor(queue).execute(command);
    }
  }

  /** Executor implementation. */
  private final class PostponeExecutor implements Executor {

    /** Queue name. */
    private final String queueName;

    private PostponeExecutor(final String queueName) {
      this.queueName = queueName;
    }

    @Override
    public void execute(@SuppressWarnings("NullableProblems") final Runnable command) {
      synchronized (lock) {
        if (delegate != null) {
          delegate.getExecutor(queueName).execute(command);
        } else {
          postponed.add(new ExecutorAction(queueName, command));
        }
      }
    }
  }

  /** Postponed scheduled future. */
  private final class BoundFuture<T> implements ObservableFuture<T>, Postponed {

    /** Queue name. */
    final String queue;
    /** Task instance. */
    final Callable<T> task;

    /** Attached Goro future. */
    private GoroFuture<T> goroFuture;

    /** Cancel flag. */
    private boolean canceled;

    /** Observers list. */
    private PendingObserversList pendingObservers;

    private BoundFuture(final String queue, final Callable<T> task) {
      this.queue = queue;
      this.task = task;
    }

    @Override
    public synchronized void act(final Goro goro) {
      goroFuture = (GoroFuture<T>) goro.schedule(queue, task);
      if (pendingObservers != null) {
        pendingObservers.execute();
        pendingObservers = null;
      }
      notifyAll();
    }

    @Override
    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
      if (goroFuture != null) {
        return goroFuture.cancel(mayInterruptIfRunning);
      }
      if (!canceled) {
        cancelPostponed(this);
        pendingObservers = null;
        canceled = true;
      }
      notifyAll();
      return true;
    }

    @Override
    public synchronized boolean isCancelled() {
      if (goroFuture != null) {
        return goroFuture.isCancelled();
      }
      return canceled;
    }

    @Override
    public synchronized boolean isDone() {
      return canceled || goroFuture != null && goroFuture.isDone();
    }

    @Override
    public synchronized T get() throws InterruptedException, ExecutionException {
      // delegate
      if (goroFuture != null) {
        return goroFuture.get();
      }
      if (checkMainThread()) {
        throw new GoroException("Blocking main thread here will lead to a deadlock");
      }

      if (canceled) {
        throw new CancellationException("Task was canceled");
      }

      // wait for act() or cancel()
      wait();

      // either we got a delegate
      if (goroFuture != null) {
        return goroFuture.get();
      }
      // or we are canceled
      if (canceled) {
        throw new CancellationException("Task was canceled");
      }

      // wtf?
      throw new IllegalStateException("get() is unblocked but there is neither result"
          + " nor cancellation");
    }

    @Override
    public synchronized T get(final long timeout, @SuppressWarnings("NullableProblems") final TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      // delegate
      if (goroFuture != null) {
        return goroFuture.get(timeout, unit);
      }
      if (checkMainThread()) {
        throw new GoroException("Blocking main thread here will lead to a deadlock");
      }

      if (canceled) {
        throw new CancellationException("Task was canceled");
      }

      // wait for act() or cancel()
      wait(unit.toMillis(timeout));

      // either we got a delegate
      if (goroFuture != null) {
        return goroFuture.get();
      }
      // or we are canceled
      if (canceled) {
        throw new CancellationException("Task was canceled");
      }

      // otherwise it's a timeout
      throw new TimeoutException();
    }

    @Override
    public synchronized void subscribe(final Executor executor, final FutureObserver<T> observer) {
      if (goroFuture != null) {
        goroFuture.subscribe(executor, observer);
        return;
      }
      if (canceled) {
        return;
      }

      if (pendingObservers == null) {
        pendingObservers = new PendingObserversList();
      }
      pendingObservers.add(new GoroFuture.ObserverRunnable<>(observer, null), executor);
    }

    @Override
    public void subscribe(final FutureObserver<T> observer) {
      subscribe(IMMEDIATE, observer);
    }

    /** List of pending observers. */
    private final class PendingObserversList extends ExecutionObserversList {
      @Override
      protected void executeObserver(final Executor executor, final Runnable what) {
        GoroFuture.ObserverRunnable runnable = (GoroFuture.ObserverRunnable) what;
        runnable.future = goroFuture;
        goroFuture.observers.add(what, executor);
      }
    }
  }

}
