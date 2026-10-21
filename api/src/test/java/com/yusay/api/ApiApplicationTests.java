package com.yusay.api;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import jakarta.servlet.RequestDispatcher;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-key-for-unit-tests-minimum-32-chars!!",
        "app.jwt.issuer=test-issuer",
        "app.jwt.ttl=PT1H",
        "app.ldap.url=ldap://localhost:389",
        "app.ldap.group-search-base=ou=groups",
        "app.ldap.group-search-filter=(member={0})",
        "app.ldap.user-dn-pattern=uid={0},ou=users"
})
class ApiApplicationTests {
        @Autowired
        MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

        @Test
        void givenErrorDispatchWithBadRequestStatus_whenDispatchedToError_thenPreservesBadRequestStatus() throws Exception {
                RequestPostProcessor errorDispatch = request -> {
                        request.setDispatcherType(DispatcherType.ERROR);
                        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 400);
                        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/auth/refresh");
                        return request;
                };

                mockMvc.perform(get("/error").with(errorDispatch))
                                .andExpect(status().isBadRequest());
        }

}
