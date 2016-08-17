package com.stanfy.enroscar.goro.sample.tape.tasks;

import android.content.Context;

import com.stanfy.enroscar.goro.Goro;
import com.stanfy.enroscar.goro.GoroService;
import com.stanfy.enroscar.goro.sample.tape.AppModule;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module(
    injects = {TapeHandler.class},
    addsTo = AppModule.class,
    complete = false
)
class QueueHandlerModule {

  private final Context context;

  QueueHandlerModule(final Context context) {
    this.context = context;
  }

  @Provides @Singleton
  Context context() {
    return context;
  }

}
