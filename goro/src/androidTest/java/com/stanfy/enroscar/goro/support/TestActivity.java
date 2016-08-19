package com.stanfy.enroscar.goro.support;

import android.app.Activity;
import android.os.Bundle;

import com.stanfy.enroscar.goro.BoundGoro;
import com.stanfy.enroscar.goro.Goro;
import com.stanfy.enroscar.goro.GoroService;

import java.util.concurrent.CountDownLatch;

import rx.Observable;
import rx.Scheduler;
import rx.functions.Action1;
import rx.functions.Actions;

public class TestActivity extends Activity {

  private final BoundGoro goro = Goro.bindAndAutoReconnectWith(this);

  final CountDownLatch resultSync = new CountDownLatch(1);
  String result;

  final CountDownLatch errorSync = new CountDownLatch(1);
  Throwable error;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    GoroService.setup(this, Goro.create());

    Scheduler scheduler = new RxGoro(goro).scheduler("test-queue");
    Observable.just("ok")
        .subscribeOn(scheduler)
        .subscribe(new Action1<String>() {
          @Override
          public void call(String s) {
            result = "ok";
            resultSync.countDown();
          }
        });

    Observable.error(new RuntimeException("test error"))
        .subscribeOn(scheduler)
        .subscribe(Actions.empty(), new Action1<Throwable>() {
          @Override
          public void call(Throwable throwable) {
            error = throwable;
            errorSync.countDown();
          }
        });
  }

  @Override
  protected void onStart() {
    super.onStart();
    goro.bind();
  }

  @Override
  protected void onStop() {
    super.onStop();
    goro.unbind();
  }

}
