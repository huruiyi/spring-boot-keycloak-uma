package com.example.umaadmin.service;

import com.example.umaadmin.data.PermissionModelRepository;
import com.example.umaadmin.model.PermissionModel;
import com.example.umaadmin.model.PermissionRuleModel;
import com.example.umaadmin.model.SystemEndpointModel;
import com.example.umaadmin.model.UmaResourceModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionAdminServiceTest {

  @Test
  void generateDefaultEndpointsCreatesMissingMappingsFromUmaPermissions() {
    PermissionModel model = new PermissionModel();
    model.setResources(List.of(
        new UmaResourceModel("order", List.of("/api/orders/*"), List.of("view", "approve")),
        new UmaResourceModel("report", List.of("/api/reports"), List.of("view"))
    ));
    model.setPermissions(List.of(
        new PermissionRuleModel("perm-order-view", "order", "view", "policy-user"),
        new PermissionRuleModel("perm-order-approve", "order", "approve", "policy-manager"),
        new PermissionRuleModel("perm-report-view", "report", "view", "policy-user")
    ));
    model.setEndpoints(List.of(
        new SystemEndpointModel("custom order view", "GET", "/api/orders", "order#view")
    ));

    InMemoryRepository repository = new InMemoryRepository(model);
    PermissionAdminService service = new PermissionAdminService(repository);

    int generated = service.generateDefaultEndpoints();

    assertThat(generated).isEqualTo(2);
    assertThat(repository.saved).isTrue();
    assertThat(model.getEndpoints())
        .extracting(SystemEndpointModel::permission)
        .containsExactly("order#view", "order#approve", "report#view");
    assertThat(model.getEndpoints())
        .filteredOn(endpoint -> endpoint.permission().equals("order#approve"))
        .singleElement()
        .satisfies(endpoint -> {
          assertThat(endpoint.method()).isEqualTo("POST");
          assertThat(endpoint.path()).isEqualTo("/api/orders/approve");
        });
  }

  @Test
  void generateDefaultEndpointsFallsBackToResourceScopes() {
    PermissionModel model = new PermissionModel();
    model.setResources(List.of(
        new UmaResourceModel("report", List.of("/api/reports/*"), List.of("view", "export"))
    ));
    model.setPermissions(List.of());

    InMemoryRepository repository = new InMemoryRepository(model);
    PermissionAdminService service = new PermissionAdminService(repository);

    int generated = service.generateDefaultEndpoints();

    assertThat(generated).isEqualTo(2);
    assertThat(model.getEndpoints())
        .extracting(SystemEndpointModel::permission)
        .containsExactly("report#view", "report#export");
    assertThat(model.getEndpoints())
        .filteredOn(endpoint -> endpoint.permission().equals("report#export"))
        .singleElement()
        .satisfies(endpoint -> {
          assertThat(endpoint.method()).isEqualTo("GET");
          assertThat(endpoint.path()).isEqualTo("/api/reports/export");
        });
  }

  private static class InMemoryRepository implements PermissionModelRepository {

    private final PermissionModel model;
    private boolean saved;

    private InMemoryRepository(PermissionModel model) {
      this.model = model;
    }

    @Override
    public PermissionModel get() {
      return model;
    }

    @Override
    public void save(PermissionModel model) {
      saved = true;
    }
  }
}
