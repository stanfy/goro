package com.stanfy.enroscar.goro;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowApplication;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.android.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = Build.VERSION_CODES.LOLLIPOP)
public abstract class BaseBindingGoroTest {

  /** Mock service instance of Goro. */
  Goro serviceInstance;

  Activity context;
  ShadowActivity shadowContext;

  IBinder binder;
  ComponentName serviceCompName;
  TestingQueues testingQueues;

  @Before
  public void init() {
    context = Robolectric.setupActivity(Activity.class);
    shadowContext = Shadows.shadowOf(context);

    testingQueues = new TestingQueues();
    serviceInstance = spy(new Goro.GoroImpl(testingQueues));

    serviceCompName = new ComponentName(context, GoroService.class);
    GoroService service = new GoroService();
    binder = new GoroService.GoroBinderImpl(serviceInstance, service.new GoroTasksListener());
    ShadowApplication.getInstance()
        .setComponentNameAndServiceForBindService(
            serviceCompName,
            binder
        );
    reset(serviceInstance);
  }

  protected abstract ServiceConnection serviceConnection();
  protected abstract Goro goro();

  protected void doBinding() { }

  @SuppressWarnings("unchecked")
  protected final Callable<String> okTask() throws Exception {
    Callable<String> task = mock(Callable.class);
    doReturn("ok").when(task).call();
    return task;
  }

  protected final void assertBinding() {
    Intent startedService = shadowContext.getNextStartedService();
    assertThat(startedService).isNotNull();
    assertThat(startedService).hasComponent(context, GoroService.class);
    Intent boundService = shadowContext.getNextStartedService();
    assertThat(boundService).isNotNull();
    assertThat(boundService).hasComponent(context, GoroService.class);

    verify(serviceConnection()).onServiceConnected(serviceCompName, binder);
  }

  @Test
  public void addListenerShouldRecord() {
    GoroListener listener = mock(GoroListener.class);
    goro().addTaskListener(listener);
    doBinding();
    assertBinding();
    verify(serviceInstance).addTaskListener(listener);
  }

  @Test
  public void scheduleShouldRecordDefaultQueue() {
    Callable<?> task = mock(Callable.class);
    goro().schedule(task);
    doBinding();
    assertBinding();
    verify(serviceInstance).schedule(Goro.DEFAULT_QUEUE, task);
  }

  @Test
  public void scheduleShouldRequestQueueName() {
    Callable<?> task = mock(Callable.class);
    goro().schedule("1", task);
    doBinding();
    assertBinding();
    verify(serviceInstance).schedule("1", task);
  }

  @Test
  public void scheduleShouldReturnFuture() {
    Future<?> future = goro().schedule(mock(Callable.class));
    assertThat(future).isNotNull();
  }

  @Test
  public void afterBindingAddRemoveListenerShouldBeDelegated() {
    doBinding();
    GoroListener listener = mock(GoroListener.class);
    goro().addTaskListener(listener);
    goro().removeTaskListener(listener);
    InOrder order = inOrder(serviceInstance);
    order.verify(serviceInstance).addTaskListener(listener);
    order.verify(serviceInstance).removeTaskListener(listener);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void observersSuccess() throws Exception {
    Callable<String> task = okTask();
    FutureObserver<String> observer = mock(FutureObserver.class);
    goro().schedule(task).subscribe(observer);

    doBinding();
    verify(serviceInstance).schedule(Goro.DEFAULT_QUEUE, task);
    testingQueues.executeAll();
    verify(observer).onSuccess("ok");
  }

  @SuppressWarnings("unchecked")
  @Test
  public void observersError() throws Exception {
    Callable<String> task = mock(Callable.class);
    Exception e = new Exception();
    doThrow(e).when(task).call();
    FutureObserver<String> observer = mock(FutureObserver.class);
    goro().schedule(task).subscribe(observer);

    doBinding();
    testingQueues.executeAll();
    verify(observer).onError(e);
  }

  @Test
  public void getOnFuture() throws Exception {
    final Future<String> f = goro().schedule(okTask());
    final String[] result = new String[1];
    final Exception[] error = new Exception[1];
    Thread t = new Thread() {
      @Override
      public void run() {
        try {
          result[0] = f.get();
        } catch (Exception e) {
          error[0] = e;
        }
      }
    };
    t.start();
    assertThat(result).containsNull();
    doBinding();
    testingQueues.executeAll();
    t.join(1000);
    if (error[0] != null) {
      throw new AssertionError(error[0]);
    }
    assertThat(result).containsOnly("ok");
  }

  @Test
  public void getOnFutureWithTimeout() throws Exception {
    final Future<String> f = goro().schedule(okTask());
    final String[] result = new String[1];
    final Exception[] error = new Exception[1];
    Thread t = new Thread() {
      @Override
      public void run() {
        try {
          result[0] = f.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
          error[0] = e;
        }
      }
    };
    t.start();
    assertThat(result).containsNull();
    doBinding();
    testingQueues.executeAll();
    t.join(1000);
    if (error[0] != null) {
      throw new AssertionError(error[0]);
    }
    assertThat(result).containsOnly("ok");
  }

  @Test
  public void clearShouldDelegate() {
    doBinding();
    goro().clear("clearedQueue");
    verify(serviceInstance).clear("clearedQueue");
  }

  @Test
  public void clearShouldBeAppliedAfterBinding() {
    Callable<?> task1 = mock(Callable.class);
    goro().schedule("clearedQueue", task1);
    goro().clear("clearedQueue");
    Callable<?> task2 = mock(Callable.class);
    goro().schedule("clearedQueue", task2);
    doBinding();
    InOrder order = inOrder(serviceInstance);
    order.verify(serviceInstance).schedule("clearedQueue", task1);
    order.verify(serviceInstance).clear("clearedQueue");
    order.verify(serviceInstance).schedule("clearedQueue", task2);
  }

}
