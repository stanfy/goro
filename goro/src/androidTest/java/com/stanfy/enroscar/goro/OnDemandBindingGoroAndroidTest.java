package com.stanfy.enroscar.goro;

import android.annotation.TargetApi;
import android.app.Application;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.test.ApplicationTestCase;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class OnDemandBindingGoroAndroidTest extends ApplicationTestCase<Application> {

  private OnDemandBindingGoro goro;

  public OnDemandBindingGoroAndroidTest() {
    super(Application.class);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    createApplication();
    new Handler(Looper.getMainLooper()).post(new Runnable() {
      @Override
      public void run() {
        GoroService.setup(getContext(), Goro.createWithDelegate(AsyncTask.THREAD_POOL_EXECUTOR));
      }
    });
    this.goro = (OnDemandBindingGoro) Goro.bindOnDemandWith(getApplication());
  }

  public void testSchedule() throws InterruptedException {
    final CountDownLatch sync = new CountDownLatch(1);
    Callable<String> task = new Callable<String>() {
      @Override
      public String call() {
        return "done";
      }
    };
    final Object[] result = new Object[1];
    goro.schedule(task)
        .subscribe(new FutureObserver<String>() {
          @Override
          public void onSuccess(final String value) {
            result[0] = value;
            sync.countDown();
          }

          @Override
          public void onError(final Throwable error) {
            result[0] = error;
            sync.countDown();
          }
        });
    assertThat(sync.await(2, TimeUnit.SECONDS)).describedAs("Operation timed out").isTrue();
    assertThat(result).containsOnly("done");
    assertThat(goro.delegate()).describedAs("Service must be unbound").isNull();
  }

  public void testExecutor() throws Exception {
    final CountDownLatch sync = new CountDownLatch(1);
    Runnable task = new Runnable() {
      @Override
      public void run() {
        sync.countDown();
      }
    };
    goro.getExecutor("test").execute(task);
    assertThat(sync.await(2, TimeUnit.SECONDS)).describedAs("Operation timed out").isTrue();
    assertThat(goro.delegate()).describedAs("Service must be unbound").isNull();
  }

}
