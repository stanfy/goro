package com.stanfy.enroscar.goro;

import android.content.ServiceConnection;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Callable;

import static org.mockito.Mockito.*;

/**
 * Tests for OnDemandBindingGoro.
 */
public class OnDemandBindingGoroTest extends BaseBindingGoroTest {

  private OnDemandBindingGoro goro;

  @Before
  public void init() {
    super.init();
    goro = spy((OnDemandBindingGoro) Goro.bindOnDemandWith(context));
  }

  @Override
  protected ServiceConnection serviceConnection() {
    return goro;
  }

  @Override
  protected Goro goro() {
    return goro;
  }

  @Test
  public void scheduleShouldBeDelegated() throws Exception {
    Callable<?> task = mock(Callable.class);
    goro.schedule("2", task);
    testingQueues.executeAll();
    verify(task).call();
  }

}
