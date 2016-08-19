package com.stanfy.enroscar.goro.support;

import android.app.Activity;
import android.os.Bundle;

import com.stanfy.enroscar.goro.BoundGoro;
import com.stanfy.enroscar.goro.Goro;
import com.stanfy.enroscar.goro.GoroService;

import java.util.concurrent.CountDownLatch;

import rx.Observable;
import rx.functions.Action1;

public class TestActivity extends Activity {

  private final BoundGoro goro = Goro.bindAndAutoReconnectWith(this);

  final CountDownLatch resultSync = new CountDownLatch(1);
  String result;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    GoroService.setup(this, Goro.create());

    Observable.just("ok")
        .subscribeOn(new RxGoro(goro).scheduler("test-queue"))
        .subscribe(new Action1<String>() {
          @Override
          public void call(String s) {
            result = "ok";
            resultSync.countDown();
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
    goro.unbind();
  }

}
