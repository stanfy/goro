package com.stanfy.enroscar.goro;

import java.util.concurrent.Callable;

/** Logs queues state on every task listener event. */
final class Dumper implements GoroListener {

  private final Goro goro;

  @Override
  public void onTaskSchedule(Callable<?> task, String queue) {

  }

  @Override
  public void onTaskStart(Callable<?> task) {

  }

  @Override
  public void onTaskFinish(Callable<?> task, Object result) {

  }

  @Override
  public void onTaskCancel(Callable<?> task) {

  }

  @Override
  public void onTaskError(Callable<?> task, Throwable error) {

  }

}
