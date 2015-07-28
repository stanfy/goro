package com.stanfy.enroscar.goro;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;

/**
 * Handles tasks in multiple queues using a worker service.
 * Exposes methods to explicitly control binding to and unbinding from the service.
 * @see com.stanfy.enroscar.goro.GoroService
 */
public abstract class BoundGoro extends BufferedGoroDelegate {

  /** Bind to {@link com.stanfy.enroscar.goro.GoroService}. */
  public abstract void bind();

  /** Unbind from {@link com.stanfy.enroscar.goro.GoroService}. */
  public abstract void unbind();

  /** A callback invoked when worker service is stopped from outside. */
  public interface OnUnexpectedDisconnection {
    void onServiceDisconnected(BoundGoro goro);
  }

  /** Implementation. */
  static class BoundGoroImpl extends BoundGoro implements ServiceConnection {

    /** Instance of the context used to bind to GoroService. */
    private final Context context;

    /** Disconnection handler. */
    private final OnUnexpectedDisconnection disconnectionHandler;

    BoundGoroImpl(final Context context, final OnUnexpectedDisconnection disconnectionHandler) {
      this.context = context;
      this.disconnectionHandler = disconnectionHandler;
    }

    @Override
    public void bind() {
      GoroService.bind(context, this);
    }

    @Override
    public void unbind() {
      if (updateDelegate(null)) {
        GoroService.unbind(context, this);
      }
    }

    @Override
    public void onServiceConnected(final ComponentName name, final IBinder binder) {
      updateDelegate(Goro.from(binder));
    }

    @Override
    public void onServiceDisconnected(final ComponentName name) {
      if (updateDelegate(null)) {
        /*
          It's the case when service was stopped by a system server.
          It can happen when user presses a stop button in application settings (in running apps section).
          Sometimes this happens on application update.
         */
        if (disconnectionHandler == null) {
          bind();
        } else {
          disconnectionHandler.onServiceDisconnected(this);
        }
      }
    }

  }

}
