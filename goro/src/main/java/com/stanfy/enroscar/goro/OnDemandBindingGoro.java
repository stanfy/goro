package com.stanfy.enroscar.goro;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/**
 * Implementation of {@link Goro} that binds to a {@link GoroService}
 * when a new job should be scheduled, passes the job to the service and unbinds asap.
 */
class OnDemandBindingGoro extends BufferedGoroDelegate implements ServiceConnection {

  private final Context context;
  private boolean bindRequested;

  OnDemandBindingGoro(final Context context) {
    this.context = context;
  }

  private synchronized void bindIfRequired() {
    if (!bindRequested) {
      bindRequested = true;
      GoroService.bind(context, this);
    }
  }

  private synchronized void unbindIfRequired() {
    if (bindRequested && updateDelegate(null)) {
      bindRequested = false;
      GoroService.unbind(context, this);
    }
  }

  @Override
  public final void addTaskListener(final GoroListener listener) {
    super.addTaskListener(listener);
    bindIfRequired();
  }

  @Override
  public final void removeTaskListener(final GoroListener listener) {
    super.removeTaskListener(listener);
    bindIfRequired();
  }

  @Override
  public final <T> ObservableFuture<T> schedule(final String queueName, final Callable<T> task) {
    ObservableFuture<T> result = super.schedule(queueName, task);
    bindIfRequired();
    return result;
  }

  @Override
  public final Executor getExecutor(final String queueName) {
    Executor executor = super.getExecutor(queueName);
    bindIfRequired();
    return executor;
  }

  @Override
  protected final void removeTasksInQueue(final String queueName) {
    super.removeTasksInQueue(queueName);
    bindIfRequired();
  }

  @Override
  public void onServiceConnected(final ComponentName name, final IBinder binder) {
    updateDelegate(Goro.from(binder));
    unbindIfRequired();
  }

  @Override
  public void onServiceDisconnected(final ComponentName name) {
    unbindIfRequired();
  }

}
