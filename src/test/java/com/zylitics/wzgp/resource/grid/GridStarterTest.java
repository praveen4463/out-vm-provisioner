package com.zylitics.wzgp.resource.grid;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.ServiceAccount;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.CompletedOperation;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.service.ComputeService;
import com.zylitics.wzgp.test.dummy.DummyRequestGridCreate;

public class GridStarterTest {
  
  private static final BuildProperty BUILD_PROP = new DummyRequestGridCreate().getBuildProperties();
  private static final String ZONE = "zone-1";
  private static final String GRID_NAME = "instance-1";
  private static final String GRID_MACHINE_TYPE = "machine-1";
  private static final String GRID_SERVICE_ACCOUNT = "srv@email.com";
  
  @Test
  @DisplayName("verify starter starts the grid and updates all updatable properties")
  void gridStartsAndAllUpdatablePropsUpdateTest() throws Exception {
    ResourceExecutor executor = mock(ResourceExecutor.class);
    ComputeService computeSrv = mock(ComputeService.class);
    GridProperty gridProp = mock(GridProperty.class);
    Instance gridInstance = getInstance();
    
    // setup gridProp, keep all updatable props that are different than what is set to the grid.
    when(gridProp.getMachineType()).thenReturn("different-machine-1");
    when(gridProp.getServiceAccount()).thenReturn("different-srv@unknown.com");
    when(gridProp.getCustomLabels()).thenReturn(ImmutableMap.of("is-production", "false"));
    when(gridProp.getMetadata()).thenReturn(ImmutableMap.of("screen-size", "10x10"));
    
    List<Operation> operations = new ArrayList<>(10);
    
    setupComputeAndExecutorStubbing(computeSrv, executor, gridProp, operations);
    
    GridStarter starter = new GridStarter(executor, computeSrv, BUILD_PROP, gridProp, gridInstance);
    CompletedOperation startOperationCompleted = starter.start();
    assertEquals("startInstance", startOperationCompleted.get().getDescription());
    
    // verify that all the 'named' operations completed.
    assertTrue(operations.stream()
        .filter(operation -> operation.getStatus().equals("DONE"))
        .anyMatch(operation -> operation.getDescription().equals("setMachineType")));
    
    assertTrue(operations.stream()
        .filter(operation -> operation.getStatus().equals("DONE"))
        .anyMatch(operation -> operation.getDescription().equals("setServiceAccount")));
    
    assertTrue(operations.stream()
        .filter(operation -> operation.getStatus().equals("DONE"))
        .anyMatch(operation -> operation.getDescription().equals("setLabels")));
    
    assertTrue(operations.stream()
        .filter(operation -> operation.getStatus().equals("DONE"))
        .anyMatch(operation -> operation.getDescription().equals("setMetadata")));
  }
  
  @Test
  @DisplayName("verify starter starts the grid and updates only supplied properties")
  void gridStartsAndOnlySuppliedPropsUpdateTest() throws Exception {
    ResourceExecutor executor = mock(ResourceExecutor.class);
    ComputeService computeSrv = mock(ComputeService.class);
    GridProperty gridProp = mock(GridProperty.class);
    Instance gridInstance = getInstance();
    
    // setup gridProp.
    when(gridProp.getMachineType()).thenReturn(GRID_MACHINE_TYPE); // keep same so no update occurs
    when(gridProp.getServiceAccount()).thenReturn(null);
    when(gridProp.getCustomLabels()).thenReturn(null);
    when(gridProp.getMetadata()).thenReturn(ImmutableMap.of("screen-size", "10x10"));
    
    List<Operation> operations = new ArrayList<>(10);
    
    setupComputeAndExecutorStubbing(computeSrv, executor, gridProp, operations);
    
    GridStarter starter = new GridStarter(executor, computeSrv, BUILD_PROP, gridProp, gridInstance);
    CompletedOperation startOperationCompleted = starter.start();
    assertEquals("startInstance", startOperationCompleted.get().getDescription());
    
    // verify that operations those were requested are only completed.
    assertTrue(operations.stream()
        .noneMatch(operation -> operation.getDescription().equals("setMachineType")));
    
    assertTrue(operations.stream()
        .noneMatch(operation -> operation.getDescription().equals("setServiceAccount")));
    
    assertTrue(operations.stream()
        .noneMatch(operation -> operation.getDescription().equals("setLabels")));
    
    assertTrue(operations.stream()
        .filter(operation -> operation.getStatus().equals("DONE"))
        .anyMatch(operation -> operation.getDescription().equals("setMetadata")));
  }
  
  private Instance getInstance() {
    // set all the properties required in starter.
    return new Instance()
        .setStatus("TERMINATED")
        .setName(GRID_NAME)
        .setZone(ZONE)
        .setMachineType(String.format("zones/%s/machineTypes/%s", ZONE, GRID_MACHINE_TYPE))
        .setServiceAccounts(ImmutableList.of(new ServiceAccount().setEmail(GRID_SERVICE_ACCOUNT)));
  }
  
  private Operation getOperation(String name, String description) {
    return new Operation()
        .setStatus("RUNNING")
        .setName(name)
        .setDescription(description);
  }
  
  private void setupComputeAndExecutorStubbing(ComputeService computeSrv, ResourceExecutor executor
      , GridProperty gridProp, List<Operation> operations) throws Exception {
    // set compute service for various calls by starter.
    
    // parameter should match to let this stubbing work, we're verifying that starter is sending
    // the correct parameters to service, params from gridProp are used because in any circumstance
    // grid's existing params aren't sent to service for update. Name of the method used
    // is set as returned Operation's description so that we later identify each operation.
    when(computeSrv.setMachineType(GRID_NAME, gridProp.getMachineType(), ZONE, BUILD_PROP))
        .then(inv -> {
          Operation operation = getOperation(GRID_NAME, "setMachineType");
          operations.add(operation);
          return operation;
        });
    
    when(computeSrv.setServiceAccount(GRID_NAME, gridProp.getServiceAccount(), ZONE, BUILD_PROP))
        .then(inv -> {
          Operation operation = getOperation(GRID_NAME, "setServiceAccount");
          operations.add(operation);
          return operation;
        });
    
    when(computeSrv.setLabels(GRID_NAME, gridProp.getCustomLabels(), ZONE, BUILD_PROP))
        .then(inv -> {
          Operation operation = getOperation(GRID_NAME, "setLabels");
          operations.add(operation);
          return operation;
        });
    
    when(computeSrv.setMetadata(GRID_NAME, gridProp.getMetadata(), ZONE, BUILD_PROP))
        .then(inv -> {
          Operation operation = getOperation(GRID_NAME, "setMetadata");
          operations.add(operation);
          return operation;
        });
    
    // finally for starting the instance, no need to add start operation since its wrapped in
    // CompletedOperation
    when(computeSrv.startInstance(GRID_NAME, ZONE, BUILD_PROP))
        .thenReturn(getOperation(GRID_NAME, "startInstance"));
    
    // stub executor to process wait completion for operations.
    // we'll mark each operation as 'DONE' just the same as executor would do, and can later check
    // that our named operations (kept here in List) are actually completed via starter.
    when(executor.blockUntilComplete(any(Operation.class), eq(BUILD_PROP)))
        .then(invocation -> {
          Operation operation = invocation.getArgument(0);
          operation.setStatus("DONE");
          return operation;
        });
  }
}
