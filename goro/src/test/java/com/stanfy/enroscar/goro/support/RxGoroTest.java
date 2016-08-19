package com.stanfy.enroscar.goro.support;

import com.stanfy.enroscar.goro.BuildConfig;
import com.stanfy.enroscar.goro.TestingQueues;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import rx.Observable;
import rx.Scheduler;
import rx.Subscription;
import rx.observers.TestSubscriber;

import static com.stanfy.enroscar.goro.GoroImplTest.createGoroWith;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 19)
public class RxGoroTest {

  private RxGoro rxGoro;
  private TestingQueues queues = new TestingQueues();

  @Before
  public void init() {
    rxGoro = new RxGoro(createGoroWith(queues));
  }

  @Test
  public void schedule() {
    TestSubscriber<String> subscriber = new TestSubscriber<>();
    Observable.just("ok")
        .subscribeOn(rxGoro.scheduler("test"))
        .subscribe(subscriber);
    subscriber.assertNoValues();
    subscriber.assertNotCompleted();

    queues.executeAll();
    subscriber.assertNoErrors();
    subscriber.assertCompleted();
    subscriber.assertValue("ok");
  }

  @Test
  public void unsubscribe() {
    TestSubscriber<String> subscriber = new TestSubscriber<>();
    Observable.just("ok")
        .subscribeOn(rxGoro.scheduler())
        .subscribe(subscriber)
        .unsubscribe();

    queues.executeAll();
    subscriber.assertNoErrors();
    subscriber.assertNoValues();
    subscriber.assertNotCompleted();
  }

}
