package org.civictracker.coordinator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.civictracker.coordinator.config.CoordinatorRuntime;
import org.civictracker.coordinator.run.RunDriver;
import org.civictracker.coordinator.run.Runs;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RunDriverTests {
  @Test
  void driverRequiresExplicitEnablementAndThenCallsTheSameReconciler() {
    var runs = mock(Runs.class);
    var runtime = mock(CoordinatorRuntime.class);
    var context =
        new ApplicationContextRunner()
            .withBean(Runs.class, () -> runs)
            .withBean(CoordinatorRuntime.class, () -> runtime)
            .withUserConfiguration(RunDriver.class);
    context.run(c -> assertEquals(0, c.getBeansOfType(RunDriver.class).size()));
    context
        .withPropertyValues("coordinator.driver.enabled=false")
        .run(c -> assertEquals(0, c.getBeansOfType(RunDriver.class).size()));
    context
        .withPropertyValues("coordinator.driver.enabled=true")
        .run(c -> {
          c.getBean(RunDriver.class).drive();
          verify(runs).tick();
          verify(runtime).validate();
        });
  }

  @Test
  void oneDriverFailureDoesNotStopLaterScheduledReconciliation() {
    var runs = mock(Runs.class);
    var runtime = mock(CoordinatorRuntime.class);
    doThrow(new IllegalStateException("Synthetic database failure")).doNothing().when(runs).tick();
    var driver = new RunDriver(runs, runtime);
    assertDoesNotThrow(driver::drive);
    assertDoesNotThrow(driver::drive);
    verify(runs, times(2)).tick();
  }
}
