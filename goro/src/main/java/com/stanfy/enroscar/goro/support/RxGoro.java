package com.stanfy.enroscar.goro.support;

import com.stanfy.enroscar.goro.FutureObserver;
import com.stanfy.enroscar.goro.Goro;
import com.stanfy.enroscar.goro.ObservableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.subscriptions.BooleanSubscription;

/**
 * Integration point for RxJava.
 * @author Roman Mazur - Stanfy (http://stanfy.com)
 */
public class RxGoro {

  /** Goro instance. */
  private final Goro goro;

  public RxGoro(final Goro goro) {
    this.goro = goro;
  }

  /** @return wrapped {@link Goro} instance */
  public Goro wrappedGoro() { return goro; }

  /**
   * @see Goro#schedule(Callable)
   */
  public <T> Observable<T> schedule(final Callable<T> task) {
    return schedule(Goro.DEFAULT_QUEUE, task);
  }

  /**
   * @see Goro#schedule(String, Callable)
   */
  public <T> Observable<T> schedule(final String queue, final Callable<T> task) {
    return Observable.create(new Observable.OnSubscribe<T>() {
      @Override
      public void call(final Subscriber<? super T> subscriber) {
        goro.schedule(queue, task).subscribe(new FutureObserver<T>() {
          @Override
          public void onSuccess(final T value) {
            if (!subscriber.isUnsubscribed()) {
              subscriber.onNext(value);
              subscriber.onCompleted();
            }
          }

          @Override
          public void onError(Throwable error) {
            if (!subscriber.isUnsubscribed()) {
              subscriber.onError(error);
            }
          }
        });
      }
    });
  }

  /**
   * Create a new scheduler that will post actions to the specified queue.
   * @param queueName name of the queue to use for scheduling actions
   * @return scheduler instance
   */
  public Scheduler scheduler(String queueName) {
    return new GoroScheduler(goro, queueName);
  }

  /**
   * Create a new scheduler that will post actions to the default queue.
   * @return scheduler instance using {@link Goro#DEFAULT_QUEUE}
   */
  public Scheduler scheduler() {
    return new GoroScheduler(goro, Goro.DEFAULT_QUEUE);
  }

  /** Rx scheduler implementation. */
  private static class GoroScheduler extends Scheduler {

    /** Goro instance. */
    private final Goro goro;
    /** Name of Goro queue used to schedule tasks. */
    private final String queueName;

    public GoroScheduler(final Goro goro, final String queueName) {
      this.goro = goro;
      this.queueName = queueName;
    }

    @Override
    public Worker createWorker() {
      return new GoroWorker();
    }

    private class GoroWorker extends Worker {

      private final List<ObservableFuture<?>> futures = new ArrayList<>(3);
      private final BooleanSubscription subscription = new BooleanSubscription();

      @Override
      public Subscription schedule(final Action0 action) {
        final ObservableFuture<Void> future = goro.schedule(queueName, new Callable<Void>() {
          @Override
          public Void call() {
            if (!isUnsubscribed()) {
              action.call();
            }
            return null;
          }
        });

        synchronized (futures) {
          futures.add(future);
        }

        future.subscribe(new FutureObserver<Void>() {
          @Override
          public void onSuccess(Void value) {
            synchronized (futures) {
              futures.remove(future);
            }
          }

          @Override
          public void onError(Throwable error) {
            synchronized (futures) {
              futures.remove(future);
            }
          }
        });

        return new Subscription() {
          @Override
          public void unsubscribe() {
            future.cancel(true);
          }

          @Override
          public boolean isUnsubscribed() {
            return future.isDone() || future.isCancelled();
          }
        };
      }

      @Override
      public Subscription schedule(final Action0 action, final long delayTime, final TimeUnit unit) {
        throw new UnsupportedOperationException("Goro scheduler does not support time-based scheduling");
      }

      @Override
      public void unsubscribe() {
        synchronized (futures) {
          if (!futures.isEmpty()) {
            for (ObservableFuture<?> f : futures) {
              f.cancel(true);
            }
            futures.clear();
          }
          subscription.unsubscribe();
        }
      }

      @Override
      public boolean isUnsubscribed() {
        return subscription.isUnsubscribed();
      }
    }
  }
}
