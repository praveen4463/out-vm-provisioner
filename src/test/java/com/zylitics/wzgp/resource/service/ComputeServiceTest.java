package com.zylitics.wzgp.resource.service;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.Compute.Images;
import com.google.api.services.compute.Compute.Instances;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.ImageList;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceList;
import com.google.api.services.compute.model.InstancesSetLabelsRequest;
import com.google.api.services.compute.model.InstancesSetMachineTypeRequest;
import com.google.api.services.compute.model.InstancesSetServiceAccountRequest;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.Operation;
import com.google.common.collect.ImmutableList;
import com.zylitics.wzgp.resource.APICoreProperties;
import com.zylitics.wzgp.resource.APICoreProperties.GridDefault;
import com.zylitics.wzgp.resource.BuildProperty;
import com.zylitics.wzgp.resource.executor.ResourceExecutor;
import com.zylitics.wzgp.resource.util.ResourceUtil;
import com.zylitics.wzgp.test.dummy.DummyAPICoreProperties;
import com.zylitics.wzgp.test.dummy.DummyRequestGridCreate;
import com.zylitics.wzgp.test.dummy.FakeCompute;

public class ComputeServiceTest {
  
  private static final BuildProperty BUILD_PROP = new DummyRequestGridCreate().getBuildProperties();
  private static final String ZONE = "unknown-zone-a-1";
  private static final String INSTANCE_NAME = "instance-a-1";

  private static final Compute COMPUTE = new FakeCompute().get();
  
  private static final APICoreProperties API_CORE_PROPS = new DummyAPICoreProperties();
  
  @TestFactory
  Stream<DynamicTest> computeServiceTest() throws Exception {
    String project = API_CORE_PROPS.getProjectId();
    GridDefault gridDefault = API_CORE_PROPS.getGridDefault();
    
    return Stream.of(
          dynamicTest("verify instance start provides valid arguments to execute", () -> {
            ResourceExecutor executor = mock(ResourceExecutor.class);
            when(executor.executeWithReattempt(any(Instances.Start.class), eq(BUILD_PROP)))
                .then(ivocation -> {
                  Instances.Start start = ivocation.getArgument(0);
                  if (!(start.getInstance().equals(INSTANCE_NAME) && start.getZone().equals(ZONE)
                      && start.getProject().equals(project))) {
                    throw new RuntimeException("invalid parameter given to Instances.Start.");
                  }
                  return getOperation(INSTANCE_NAME);
            });
            ComputeService computeSrv = new ComputeService(COMPUTE, executor, API_CORE_PROPS);
            Operation operation = computeSrv.startInstance(INSTANCE_NAME, ZONE, BUILD_PROP);
            assertEquals(INSTANCE_NAME, operation.getName());
          }),
          
          dynamicTest("verify instance stop provides valid arguments to execute", () -> {
            ResourceExecutor executor = mock(ResourceExecutor.class);
            when(executor.executeWithReattempt(any(Instances.Stop.class), eq(BUILD_PROP)))
                .then(ivocation -> {
                  Instances.Stop stop = ivocation.getArgument(0);
                  if (!(stop.getInstance().equals(INSTANCE_NAME) && stop.getZone().equals(ZONE)
                      && stop.getProject().equals(project))) {
                    throw new RuntimeException("invalid parameter given to Instances.Stop.");
                  }
                  return getOperation(INSTANCE_NAME);
            });
            ComputeService computeSrv = new ComputeService(COMPUTE, executor, API_CORE_PROPS);
            Operation operation = computeSrv.stopInstance(INSTANCE_NAME, ZONE, BUILD_PROP);
            assertEquals(INSTANCE_NAME, operation.getName());
          }),
          
          dynamicTest("verify instance delete provides valid arguments to execute", () -> {
            ResourceExecutor executor = mock(ResourceExecutor.class);
            when(executor.executeWithReattempt(any(Instances.Delete.class), eq(BUILD_PROP)))
                .then(ivocation -> {
                  Instances.Delete delete = ivocation.getArgument(0);
                  if (!(delete.getInstance().equals(INSTANCE_NAME) && delete.getZone().equals(ZONE)
                      && delete.getProject().equals(project))) {
                    throw new RuntimeException("invalid parameter given to Instances.Delete.");
                  }
                  return getOperation(INSTANCE_NAME);
            });
            ComputeService computeSrv = new ComputeService(COMPUTE, executor, API_CORE_PROPS);
            Operation operation = computeSrv.deleteInstance(INSTANCE_NAME, ZONE, BUILD_PROP);
            assertEquals(INSTANCE_NAME, operation.getName());
          }),
          
          dynamicTest("verify instance get provides valid arguments to execute", () -> {
            ResourceExecutor executor = mock(ResourceExecutor.class);
            when(executor.executeWithReattempt(any(Instances.Get.class), eq(BUILD_PROP)))
                .then(ivocation -> {
                  Instances.Get get = ivocation.getArgument(0);
                  if (!(get.getInstance().equals(INSTANCE_NAME) && get.getZone().equals(ZONE)
                      && get.getProject().equals(project))) {
                    throw new RuntimeException("invalid parameter given to Instances.Get.");
                  }
                  return new Instance().setName(INSTANCE_NAME);
            });
            ComputeService computeSrv = new ComputeService(COMPUTE, executor, API_CORE_PROPS);
            Instance instance = computeSrv.getInstance(INSTANCE_NAME, ZONE, BUILD_PROP);
            assertEquals(INSTANCE_NAME, instance.getName());
          }),
          
          dynamicTest("verify set machine-type provides valid arguments to execute", () -> {
            ResourceExecutor executor = mock(ResourceExecutor.class);
            when(executor.executeWithReattempt(any(Instances.SetMachineType.class), eq(BUILD_PROP)))
                .then(ivocation -> {
                  Instances.SetMachineType setMachineType = ivocation.getArgument(0);
                  InstancesSetMachineTypeRequest request =
                      ((InstancesSetMachineTypeRequest) setMachineType.getJsonContent());
                  String machine = ResourceUtil.getResourceNameFromUrl(request.getMachineType());
                  if (!(setMachineType.getInstance().equals(INSTANCE_NAME) 
                      && setMachineType.getZone().equals(ZONE)
                      && setMachineType.getProject().equals(project)
                      && machine.equals(gridDefault.getMachineType()))) {
                    throw new RuntimeException(
                        "invalid parameter given to Instances.SetMachineType.");
                  }
                  return getOperation(INSTANCE_NAME);
            });
            ComputeService computeSrv = new ComputeService(COMPUTE, executor, API_CORE_PROPS);
            Operation operation = computeSrv.setMachineType(INSTANCE_NAME
                , gridDefault.getMachineType(), ZONE, BUILD_PROP);
            assertEquals(INSTANCE_NAME, operation.getName());
          }),
          
          dynamicTest("verify set service-account provides valid arguments to execute", () -> {
            ResourceExecutor executor = mock(ResourceExecutor.class);
            when(executor.executeWithReattempt(any(Instances.SetServiceAccount.class)
                , eq(BUILD_PROP))).then(ivocation -> {
                  Instances.SetServiceAccount setSrvAcc = ivocation.getArgument(0);
                  InstancesSetServiceAccountRequest request =
                      ((InstancesSetServiceAccountRequest) setSrvAcc.getJsonContent());
                  String srvAccEmail = request.getEmail();
                  if (!(setSrvAcc.getInstance().equals(INSTANCE_NAME) 
                      && setSrvAcc.getZone().equals(ZONE)
                      && setSrvAcc.getProject().equals(project)
                      && srvAccEmail.equals(gridDefault.getServiceAccount()))) {
                    throw new RuntimeException(
                        "invalid parameter given to Instances.SetServiceAccount.");
                  }
                  return getOperation(INSTANCE_NAME);
            });
            ComputeService computeSrv = new ComputeService(COMPUTE, executor, API_CORE_PROPS);
            Operation operation = computeSrv.setServiceAccount(INSTANCE_NAME
                , gridDefault.getServiceAccount(), ZONE, BUILD_PROP);
            assertEquals(INSTANCE_NAME, operation.getName());
          }),
          
          dynamicTest("verify set labels provides valid arguments to execute", () -> {
            ResourceExecutor executor = mock(ResourceExecutor.class);
            when(executor.executeWithReattempt(any(Instances.SetLabels.class)
                , eq(BUILD_PROP))).then(ivocation -> {
                  Instances.SetLabels setLabels = ivocation.getArgument(0);
                  InstancesSetLabelsRequest request =
                      ((InstancesSetLabelsRequest) setLabels.getJsonContent());
                  Map<String, String> labels = request.getLabels();
                  if (!(setLabels.getInstance().equals(INSTANCE_NAME) 
                      && setLabels.getZone().equals(ZONE)
                      && setLabels.getProject().equals(project)
                      && labels.equals(gridDefault.getLabels()))) {
                    throw new RuntimeException(
                        "invalid parameter given to Instances.SetLabels.");
                  }
                  return getOperation(INSTANCE_NAME);
            });
            ComputeService computeSrv = new ComputeService(COMPUTE, executor, API_CORE_PROPS);
            Operation operation = computeSrv.setLabels(INSTANCE_NAME, gridDefault.getLabels()
                , ZONE, BUILD_PROP);
            assertEquals(INSTANCE_NAME, operation.getName());
          }),
          
          dynamicTest("verify set metadata provides valid arguments to execute", () -> {
            ResourceExecutor executor = mock(ResourceExecutor.class);
            when(executor.executeWithReattempt(any(Instances.SetMetadata.class), eq(BUILD_PROP)))
                .then(ivocation -> {
                  Instances.SetMetadata setMetadata = ivocation.getArgument(0);
                  Metadata metadata = ((Metadata) setMetadata.getJsonContent());
                  if (!(setMetadata.getInstance().equals(INSTANCE_NAME) 
                      && setMetadata.getZone().equals(ZONE)
                      && setMetadata.getProject().equals(project)
                      && metadata.equals(ResourceUtil.getGCPMetadata(gridDefault.getMetadata())))) {
                    throw new RuntimeException("invalid parameter given to Instances.SetMetadata.");
                  }
                  return getOperation(INSTANCE_NAME);
            });
            ComputeService computeSrv = new ComputeService(COMPUTE, executor, API_CORE_PROPS);
            Operation operation = computeSrv.setMetadata(INSTANCE_NAME, gridDefault.getMetadata()
                , ZONE, BUILD_PROP);
            assertEquals(INSTANCE_NAME, operation.getName());
          }),
          
          dynamicTest("verify image get from family provides valid arguments to execute", () -> {
            String family = "win2008-base";
            ResourceExecutor executor = mock(ResourceExecutor.class);
            when(executor.executeWithReattempt(any(Images.GetFromFamily.class), eq(BUILD_PROP)))
                .then(ivocation -> {
                  Images.GetFromFamily getFromFamily = ivocation.getArgument(0);
                  if (!(getFromFamily.getProject().equals(project)
                      && getFromFamily.getFamily().equals(family))) {
                    throw new RuntimeException("invalid parameter given to Images.GetFromFamily.");
                  }
                  return new Image().setFamily(family);
            });
            ComputeService computeSrv = new ComputeService(COMPUTE, executor, API_CORE_PROPS);
            Image image = computeSrv.getImageFromFamily(family, BUILD_PROP);
            assertEquals(family, image.getFamily());
          }),
          
          dynamicTest("verify image list provides valid arguments to execute", () -> {
            String filter = "label.os=win7";
            long maxResult = 1;
            ResourceExecutor executor = mock(ResourceExecutor.class);
            when(executor.executeWithReattempt(any(Images.List.class), eq(BUILD_PROP)))
                .then(ivocation -> {
                  Images.List list = ivocation.getArgument(0);
                  if (!(list.getProject().equals(project)
                      && list.getFilter().equals(filter)
                      && list.getMaxResults().equals(maxResult))) {
                    throw new RuntimeException("invalid parameter given to Images.List.");
                  }
                  return new ImageList().setItems(ImmutableList.of(new Image()));
            });
            ComputeService computeSrv = new ComputeService(COMPUTE, executor, API_CORE_PROPS);
            List<Image> images = computeSrv.listImages(filter, maxResult, BUILD_PROP);
            assertEquals(maxResult, images.size());
          }),
          
          dynamicTest("verify instance list provides valid arguments to execute", () -> {
            String filter = "label.os=win7";
            long maxResult = 1;
            ResourceExecutor executor = mock(ResourceExecutor.class);
            when(executor.executeWithReattempt(any(Instances.List.class), eq(BUILD_PROP)))
                .then(ivocation -> {
                  Instances.List list = ivocation.getArgument(0);
                  if (!(list.getProject().equals(project)
                      && list.getZone().equals(ZONE)
                      && list.getFilter().equals(filter)
                      && list.getMaxResults().equals(maxResult))) {
                    throw new RuntimeException("invalid parameter given to Instances.List.");
                  }
                  return new InstanceList().setItems(ImmutableList.of(new Instance()));
            });
            ComputeService computeSrv = new ComputeService(COMPUTE, executor, API_CORE_PROPS);
            List<Instance> instances = computeSrv.listInstances(filter, maxResult, ZONE
                , BUILD_PROP);
            assertEquals(maxResult, instances.size());
          })
        );
  }
  
  private Operation getOperation(String name) {
    return new Operation().setName(name);
  }
  
}
