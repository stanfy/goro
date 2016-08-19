package com.stanfy.enroscar.goro.support;

import android.test.ActivityInstrumentationTestCase2;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class RxGoroAndroidTest extends ActivityInstrumentationTestCase2<TestActivity> {

  public RxGoroAndroidTest() {
    super(TestActivity.class);
  }

  public void testSimple() throws InterruptedException {
    TestActivity activity = getActivity();
    assertThat(activity.resultSync.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(activity.result).isEqualTo("ok");
  }

}
