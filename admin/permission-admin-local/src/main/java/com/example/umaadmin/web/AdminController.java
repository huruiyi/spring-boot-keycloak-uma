package com.example.umaadmin.web;

import com.example.umaadmin.model.PermissionRuleModel;
import com.example.umaadmin.model.PermissionModel;
import com.example.umaadmin.model.PolicyModel;
import com.example.umaadmin.model.RealmRoleModel;
import com.example.umaadmin.model.SystemEndpointModel;
import com.example.umaadmin.model.UmaResourceModel;
import com.example.umaadmin.model.UserModel;
import com.example.umaadmin.service.PermissionAdminService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminController {

  private final PermissionAdminService service;

  public AdminController(PermissionAdminService service) {
    this.service = service;
  }

  @GetMapping("/")
  public String dashboard(Model model) {
    model.addAttribute("model", service.model());
    return "dashboard";
  }

  @GetMapping("/roles")
  public String roles(Model model) {
    model.addAttribute("model", service.model());
    model.addAttribute("form", new RoleForm());
    return "roles";
  }

  @PostMapping("/roles")
  public String addRole(@Valid @ModelAttribute("form") RoleForm form, BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("model", service.model());
      return "roles";
    }
    service.addRole(new RealmRoleModel(form.getName(), form.getDescription()));
    redirectAttributes.addFlashAttribute("message", "角色已保存");
    return "redirect:/roles";
  }

  @PostMapping("/roles/delete")
  public String deleteRole(@RequestParam String name, RedirectAttributes redirectAttributes) {
    service.deleteRole(name);
    redirectAttributes.addFlashAttribute("message", "角色已删除");
    return "redirect:/roles";
  }

  @GetMapping("/users")
  public String users(Model model) {
    model.addAttribute("model", service.model());
    model.addAttribute("form", new UserForm());
    return "users";
  }

  @PostMapping("/users")
  public String addUser(@Valid @ModelAttribute("form") UserForm form, BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("model", service.model());
      return "users";
    }
    service.addUser(new UserModel(form.getUsername(), form.getEmail(), form.getPassword(), form.getRealmRoles()));
    redirectAttributes.addFlashAttribute("message", "用户已保存");
    return "redirect:/users";
  }

  @PostMapping("/users/delete")
  public String deleteUser(@RequestParam String username, RedirectAttributes redirectAttributes) {
    service.deleteUser(username);
    redirectAttributes.addFlashAttribute("message", "用户已删除");
    return "redirect:/users";
  }

  @GetMapping("/resources")
  public String resources(@RequestParam(required = false) String name, Model model) {
    PermissionModel permissionModel = service.model();
    ResourceForm form = new ResourceForm();
    boolean editing = name != null && !name.isBlank();
    if (editing) {
      permissionModel.getResources().stream()
          .filter(resource -> resource.name().equals(name))
          .findFirst()
          .ifPresent(resource -> {
            form.setOriginalName(resource.name());
            form.setName(resource.name());
            form.setUris(String.join(",", resource.uris()));
            form.setScopes(String.join(",", resource.scopes()));
          });
    }
    model.addAttribute("model", permissionModel);
    model.addAttribute("form", form);
    model.addAttribute("editing", editing);
    return "resources";
  }

  @PostMapping("/resources")
  public String addResource(@Valid @ModelAttribute("form") ResourceForm form, BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("model", service.model());
      model.addAttribute("editing", form.getOriginalName() != null && !form.getOriginalName().isBlank());
      return "resources";
    }
    service.saveResource(
        form.getOriginalName(),
        new UmaResourceModel(form.getName(), FormSupport.splitCsv(form.getUris()), FormSupport.splitCsv(form.getScopes()))
    );
    redirectAttributes.addFlashAttribute("message", "UMA Resource 已保存");
    return "redirect:/resources";
  }

  @PostMapping("/resources/delete")
  public String deleteResource(@RequestParam String name, RedirectAttributes redirectAttributes) {
    service.deleteResource(name);
    redirectAttributes.addFlashAttribute("message", "UMA Resource 已删除");
    return "redirect:/resources";
  }

  @GetMapping("/policies")
  public String policies(@RequestParam(required = false) String name, Model model) {
    PermissionModel permissionModel = service.model();
    PolicyForm form = new PolicyForm();
    boolean editing = name != null && !name.isBlank();
    if (editing) {
      permissionModel.getPolicies().stream()
          .filter(policy -> policy.name().equals(name))
          .findFirst()
          .ifPresent(policy -> {
            form.setName(policy.name());
            form.setType(policy.type());
            form.setRealmRole(policy.realmRole());
            form.setDescription(policy.description());
          });
    }
    model.addAttribute("model", permissionModel);
    model.addAttribute("form", form);
    model.addAttribute("editing", editing);
    return "policies";
  }

  @PostMapping("/policies")
  public String addPolicy(@Valid @ModelAttribute("form") PolicyForm form, BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("model", service.model());
      model.addAttribute("editing", form.getName() != null && !form.getName().isBlank());
      return "policies";
    }
    service.addPolicy(new PolicyModel(form.getName(), form.getType(), form.getRealmRole(), form.getDescription()));
    redirectAttributes.addFlashAttribute("message", "策略已保存");
    return "redirect:/policies";
  }

  @PostMapping("/policies/delete")
  public String deletePolicy(@RequestParam String name, RedirectAttributes redirectAttributes) {
    service.deletePolicy(name);
    redirectAttributes.addFlashAttribute("message", "策略已删除");
    return "redirect:/policies";
  }

  @GetMapping("/permissions")
  public String permissions(@RequestParam(required = false) String name, Model model) {
    PermissionModel permissionModel = service.model();
    PermissionForm form = new PermissionForm();
    boolean editing = name != null && !name.isBlank();
    if (editing) {
      permissionModel.getPermissions().stream()
          .filter(permission -> permission.name().equals(name))
          .findFirst()
          .ifPresent(permission -> {
            form.setName(permission.name());
            form.setResource(permission.resource());
            form.setScope(permission.scope());
            form.setPolicies(permission.policies());
          });
    }
    model.addAttribute("model", permissionModel);
    model.addAttribute("form", form);
    model.addAttribute("editing", editing);
    return "permissions";
  }

  @PostMapping("/permissions")
  public String addPermission(@Valid @ModelAttribute("form") PermissionForm form, BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("model", service.model());
      model.addAttribute("editing", form.getName() != null && !form.getName().isBlank());
      return "permissions";
    }
    service.addPermission(new PermissionRuleModel(form.getName(), form.getResource(), form.getScope(), form.getPolicies()));
    redirectAttributes.addFlashAttribute("message", "权限规则已保存");
    return "redirect:/permissions";
  }

  @PostMapping("/permissions/delete")
  public String deletePermission(@RequestParam String name, RedirectAttributes redirectAttributes) {
    service.deletePermission(name);
    redirectAttributes.addFlashAttribute("message", "权限规则已删除");
    return "redirect:/permissions";
  }

  @GetMapping("/endpoints")
  public String endpoints(Model model) {
    model.addAttribute("model", service.model());
    model.addAttribute("form", new EndpointForm());
    return "endpoints";
  }

  @PostMapping("/endpoints")
  public String addEndpoint(@Valid @ModelAttribute("form") EndpointForm form, BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("model", service.model());
      return "endpoints";
    }
    service.addEndpoint(new SystemEndpointModel(form.getName(), form.getMethod().toUpperCase(), form.getPath(), form.getPermission()));
    redirectAttributes.addFlashAttribute("message", "接口权限已保存");
    return "redirect:/endpoints";
  }

  @PostMapping("/endpoints/delete")
  public String deleteEndpoint(@RequestParam String method, @RequestParam String path, RedirectAttributes redirectAttributes) {
    service.deleteEndpoint(method, path);
    redirectAttributes.addFlashAttribute("message", "接口权限已删除");
    return "redirect:/endpoints";
  }

  @PostMapping("/endpoints/generate-defaults")
  public String generateDefaultEndpoints(RedirectAttributes redirectAttributes) {
    int generated = service.generateDefaultEndpoints();
    redirectAttributes.addFlashAttribute("message", "Generated " + generated + " default endpoint permissions");
    return "redirect:/endpoints";
  }
}
