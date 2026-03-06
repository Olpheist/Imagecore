package com.green.imagecore.controller;

import com.green.imagecore.config.SecurityConfig;
import com.green.imagecore.config.RequestLogFilter;
import com.green.imagecore.entities.Role;
import com.green.imagecore.entities.RoleType;
import com.green.imagecore.service.LogService;
import com.green.imagecore.service.RoleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RoleController.class)
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-that-is-long-enough-for-hmac")
class RoleControllerTest extends BaseControllerTest {

    @MockitoBean
    private RoleService roleService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void getRoles_asAdmin_returns200() throws Exception {
        Role adminRole = new Role();
        adminRole.setId(1L);
        adminRole.setName(RoleType.ADMIN);

        Role clinicianRole = new Role();
        clinicianRole.setId(2L);
        clinicianRole.setName(RoleType.CLINICIAN);

        when(roleService.findAll()).thenReturn(List.of(adminRole, clinicianRole));

        mockMvc.perform(get("/api/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("ADMIN"))
                .andExpect(jsonPath("$[1].name").value("CLINICIAN"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void getRoles_asUser_returns403() throws Exception {
        mockMvc.perform(get("/api/roles"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getRoles_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/roles"))
                .andExpect(status().isUnauthorized());
    }
}