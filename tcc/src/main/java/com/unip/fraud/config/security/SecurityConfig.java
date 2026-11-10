package com.unip.fraud.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public UserDetailsService userDetailsService(
      @Value("${models.security.admin-user}") final String username,
      @Value("${models.security.admin-password}") final String password,
      @Value("${models.security.viewer-user:}") final String viewerUsername,
      @Value("${models.security.viewer-password:}") final String viewerPassword,
      final PasswordEncoder encoder) {
    final var users = new InMemoryUserDetailsManager(
        User.withUsername(username)
            .password(encoder.encode(password))
            .roles("MODEL_ADMIN")
            .build()
    );
    if (!viewerUsername.isBlank() && !viewerPassword.isBlank()) {
      if (viewerUsername.equals(username)) throw new IllegalArgumentException("Usernames must differ");
      users.createUser(User.withUsername(viewerUsername).password(encoder.encode(viewerPassword)).roles("VIEWER").build());
    }
    return users;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {
    final var csrfRepository = org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfRepository.setCookiePath("/");
    return http
        .csrf(csrf -> csrf
            .csrfTokenRepository(csrfRepository)
            .csrfTokenRequestHandler(new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler()))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers("/auth/me", "/auth/login").authenticated()
            .requestMatchers(HttpMethod.POST, "/models/**").hasRole("MODEL_ADMIN")
            .anyRequest().permitAll()
        )
        .logout(logout -> logout.logoutUrl("/auth/logout")
            .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204))
            .deleteCookies("JSESSIONID"))
        .httpBasic(basic -> basic.authenticationEntryPoint((request, response, exception) -> {
          response.setStatus(401);
          response.setContentType("application/json");
          response.getWriter().write("{\"message\":\"Usuário ou senha inválidos.\"}");
        }))
        .build();
  }
}
