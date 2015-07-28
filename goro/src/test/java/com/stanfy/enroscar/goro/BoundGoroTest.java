package com.stanfy.enroscar.goro;

import android.content.ComponentName;
import android.content.ServiceConnection;

import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for BoundGoro.
 */
public class BoundGoroTest extends BaseBindingGoroTest {

  /** Implementation. */
  private BoundGoro.BoundGoroImpl goro;

  private BoundGoro.OnUnexpectedDisconnection disconnection;

  @Override
  public void init() {
    super.init();
    disconnection = mock(BoundGoro.OnUnexpectedDisconnection.class);
    goro = (BoundGoro.BoundGoroImpl) Goro.bindWith(context, disconnection);
    goro = spy(goro);
  }

  @Override
  protected ServiceConnection serviceConnection() {
    return goro;
  }

  @Override
  protected Goro goro() {
    return goro;
  }

  @Override
  protected void doBinding() {
    goro.bind();
  }

  @Test
  public void removeListenerShouldRemoveFromRecords() {
    GoroListener listener = mock(GoroListener.class);
    goro.addTaskListener(listener);
    goro.removeTaskListener(listener);
    goro.bind();
    assertBinding();
    verify(serviceInstance, never()).addTaskListener(listener);
  }

  @Test
  public void cancelFutureBeforeBindingShouldRemoveRecordedTask() {
    Future<?> future = goro.schedule(mock(Callable.class));
    future.cancel(true);
    goro.bind();
    assertBinding();
    verify(serviceInstance, never()).schedule(anyString(), any(Callable.class));
  }

  @Test
  public void getExecutorShouldReturnExecutorWrapper() {
    Executor executor = goro.getExecutor("q");
    assertThat(executor).isNotNull();
    Runnable task = mock(Runnable.class);
    executor.execute(task);
    verify(task, never()).run();

    //noinspection NullableProblems
    Executor direct = new Executor() {
      @Override
      public void execute(Runnable command) {
        command.run();
      }
    };
    doReturn(direct).when(serviceInstance).getExecutor(anyString());

    goro.bind();

    verify(task).run();
  }

  @Test
  public void throwingTimeoutWithoutBinding() throws Exception {
    final Future<String> f = goro.schedule(okTask());
    final String[] result = new String[1];
    final Exception[] error = new Exception[1];
    Thread t = new Thread() {
      @Override
      public void run() {
        try {
          result[0] = f.get(5, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
          error[0] = e;
        }
      }
    };
    t.start();
    t.join(1000);
    assertThat(result).containsNull();
    assertThat(error[0]).isInstanceOf(TimeoutException.class);
  }

  @Test
  public void clearShouldNotBePostponedAfterDelegation() {
    goro = (BoundGoro.BoundGoroImpl) Goro.bindAndAutoReconnectWith(context);
    goro.bind();
    goro.clear("a");
    goro.onServiceDisconnected(new ComponentName("any", "any"));
    verify(serviceInstance).clear("a");
  }

  @Test
  public void disconnectionHandlerIsInvoked() {
    goro.bind();
    assertBinding();
    goro.onServiceDisconnected(new ComponentName("test", "test"));
    verify(disconnection).onServiceDisconnected(goro);
  }

  @Test
  public void autoReconnection() {
    goro = (BoundGoro.BoundGoroImpl) spy(Goro.bindAndAutoReconnectWith(context));
    goro.bind();
    assertBinding();
    reset(goro);
    goro.onServiceDisconnected(new ComponentName("test", "test"));
    assertBinding();
  }

  @Test
  public void afterBindingScheduleShouldBeDelegated() {
    goro.bind();
    Callable<?> task = mock(Callable.class);
    ObservableFuture<?> future = mock(ObservableFuture.class);
    doReturn(future).when(serviceInstance).schedule("2", task);
    assertThat((Object) goro.schedule("2", task)).isSameAs(future);
  }

}
