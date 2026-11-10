package com.unip.fraud.adapter.in;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
  @GetMapping("/auth/csrf")
  public java.util.Map<String, String> csrf(org.springframework.security.web.csrf.CsrfToken token) {
    return java.util.Map.of("token", token.getToken());
  }

  @org.springframework.web.bind.annotation.PostMapping("/auth/login")
  public UserView login(Authentication authentication, jakarta.servlet.http.HttpServletRequest request,
      jakarta.servlet.http.HttpServletResponse response) {
    if (request.getSession(false) != null) request.changeSessionId();
    var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    new org.springframework.security.web.context.HttpSessionSecurityContextRepository().saveContext(context, request, response);
    return me(authentication);
  }

  @GetMapping("/auth/me")
  public UserView me(Authentication authentication) {
    return new UserView(authentication.getName(), authentication.getAuthorities().stream()
        .anyMatch(authority -> authority.getAuthority().equals("ROLE_MODEL_ADMIN")));
  }
  public record UserView(String username, boolean canManageModels) {}
}
