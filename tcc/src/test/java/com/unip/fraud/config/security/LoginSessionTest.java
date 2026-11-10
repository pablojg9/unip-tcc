package com.unip.fraud.config.security;

import com.unip.fraud.adapter.in.AuthController;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.context.TestPropertySource;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Base64;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.test.context.TestExecutionListeners(org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class)
@SpringJUnitWebConfig(LoginSessionTest.Config.class)
@TestPropertySource(properties = {"models.security.admin-user=admin", "models.security.admin-password=test-password"})
class LoginSessionTest {
  @Configuration @EnableWebSecurity @Import({SecurityConfig.class, AuthController.class})
  @org.springframework.web.servlet.config.annotation.EnableWebMvc
  static class Config {}
  @Autowired WebApplicationContext context;

  @Test void restoresSessionAndInvalidatesOnLogout() throws Exception {
    var mvc = MockMvcBuilders.webAppContextSetup(context)
        .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    var csrf = mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andReturn();
    var cookie = csrf.getResponse().getCookie("XSRF-TOKEN");
    var login = mvc.perform(post("/auth/login").cookie(cookie)
        .header("X-XSRF-TOKEN", cookie.getValue())
        .header("Authorization", "Basic " + Base64.getEncoder().encodeToString("admin:test-password".getBytes())))
        .andExpect(status().isOk()).andReturn();
    var session = (MockHttpSession) login.getRequest().getSession(false);
    mvc.perform(get("/auth/me").session(session)).andExpect(status().isOk())
        .andExpect(jsonPath("$.canManageModels").value(true));
    mvc.perform(post("/auth/logout").session(session).cookie(cookie)
        .header("X-XSRF-TOKEN", cookie.getValue())).andExpect(status().isNoContent());
    org.junit.jupiter.api.Assertions.assertTrue(session.isInvalid());
    mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
    mvc.perform(post("/auth/login")).andExpect(status().isForbidden());
  }
}
