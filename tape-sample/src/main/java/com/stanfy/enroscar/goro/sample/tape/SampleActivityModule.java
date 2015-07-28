package com.stanfy.enroscar.goro.sample.tape;

import android.content.Context;

import com.stanfy.enroscar.goro.BoundGoro;
import com.stanfy.enroscar.goro.Goro;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/** Module for SampleActivity. */
@Module(
    injects = SampleActivity.class,
    addsTo = AppModule.class
)
class SampleActivityModule {

  private final Context context;

  SampleActivityModule(final SampleActivity activity) {
    this.context = activity;
  }

  @Provides @Singleton
  BoundGoro boundGoro() {
    return Goro.bindAndAutoReconnectWith(context);
  }

}
