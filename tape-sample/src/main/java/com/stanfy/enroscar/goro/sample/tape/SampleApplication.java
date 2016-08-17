package com.stanfy.enroscar.goro.sample.tape;

import android.app.Application;
import android.content.Context;

import com.stanfy.enroscar.goro.Goro;
import com.stanfy.enroscar.goro.GoroService;

import javax.inject.Inject;

import dagger.ObjectGraph;

public class SampleApplication extends Application {

  private ObjectGraph objectGraph;

  @Inject Goro goro;

  @Override
  public void onCreate() {
    super.onCreate();
    objectGraph = ObjectGraph.create(new AppModule(this));
    objectGraph.inject(this);

    GoroService.setup(this, goro);
  }

  public static ObjectGraph graph(final Context context, final Object... modules) {
    SampleApplication app = (SampleApplication) context.getApplicationContext();
    return app.objectGraph.plus(modules);
  }

}
