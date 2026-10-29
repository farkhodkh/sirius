package world.sirius.ops.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import world.sirius.ops.config.ResourceServerConfig;
import world.sirius.ops.service.ContainerService;
import world.sirius.ops.service.ContainerView;

@WebMvcTest(OpsController.class)
@Import(ResourceServerConfig.class)
class OpsControllerSecurityTest {

    @Autowired MockMvc mvc;
    @MockitoBean ContainerService containers;

    static final String ID = "0123456789ab";

    @Test
    void withoutTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/ops/containers")).andExpect(status().isUnauthorized());
    }

    @Test
    void ordinaryUserIs403() throws Exception {
        mvc.perform(get("/api/v1/ops/containers").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/ops/containers/" + ID + "/restart")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSeesContainers() throws Exception {
        when(containers.list()).thenReturn(List.of(new ContainerView(ID, "/sirius-redis", "redis:7-alpine", "running", "Up")));

        mvc.perform(get("/api/v1/ops/containers").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].names").value("/sirius-redis"))
                .andExpect(jsonPath("$[0].state").value("running"));
    }

    @Test
    void adminCanRestart() throws Exception {
        mvc.perform(post("/api/v1/ops/containers/" + ID + "/restart")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isAccepted());
        verify(containers).restart(ID);
    }

    @Test
    void logLinesAreLimited() throws Exception {
        when(containers.tailLogs(eq(ID), eq(1000))).thenReturn("строка");

        mvc.perform(get("/api/v1/ops/containers/" + ID + "/logs?lines=5000")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
        verify(containers).tailLogs(ID, 1000);                      // 5000 урезано до 1000
    }
}
