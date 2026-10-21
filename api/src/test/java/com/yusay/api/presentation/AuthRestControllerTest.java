package com.yusay.api.presentation;

import com.yusay.api.application.AuthApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthRestController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthRestControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthApplicationService authApplicationService;

    @Test
    void givenValidCredentials_whenSignIn_thenReturnsLegacyFieldsAndRefreshToken() throws Exception {
        doReturn(new AuthApplicationService.TokenSet(
            "access-token",
            "refresh-token",
            "cid",
            List.of("ROLE_USER"),
            "Bearer",
            900
        )).when(authApplicationService).signIn("cid", "password");

        mockMvc.perform(post("/auth/signIn")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"cid","password":"password"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("access-token"))
            .andExpect(jsonPath("$.username").value("cid"))
            .andExpect(jsonPath("$.authorities[0]").value("ROLE_USER"))
            .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void givenValidRefreshToken_whenRefresh_thenReturnsRotatedTokenSet() throws Exception {
        AuthApplicationService.TokenSet tokens = new AuthApplicationService.TokenSet(
            "new-access-token",
            "new-refresh-token",
            "cid",
            List.of("ROLE_USER"),
            "Bearer",
            900
        );
        doReturn(new AuthApplicationService.RefreshSucceeded(tokens))
            .when(authApplicationService).refresh("old-refresh-token");

        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"refreshToken":"old-refresh-token"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("new-access-token"))
            .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));
    }

    @Test
    void givenRejectedRefreshToken_whenRefresh_thenReturnsUnauthorized() throws Exception {
        doReturn(new AuthApplicationService.RefreshRejected())
            .when(authApplicationService).refresh(anyString());

        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"refreshToken":"invalid-refresh-token"}
                    """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void givenRefreshToken_whenSignOut_thenReturnsNoContent() throws Exception {
        mockMvc.perform(post("/auth/signOut")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"refreshToken":"refresh-token"}
                    """))
            .andExpect(status().isNoContent());

        verify(authApplicationService).signOut("refresh-token");
    }

    @Test
    void givenTransactionCannotBeCreated_whenRefresh_thenReturnsServiceUnavailable() throws Exception {
        doThrow(new CannotCreateTransactionException("database unavailable"))
            .when(authApplicationService).refresh("refresh-token");

        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"refreshToken":"refresh-token"}
                    """))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    void givenDatabaseConnectionFailure_whenRefresh_thenReturnsServiceUnavailable() throws Exception {
        doThrow(new DataAccessResourceFailureException("database unavailable"))
            .when(authApplicationService).refresh("refresh-token");

        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"refreshToken":"refresh-token"}
                    """))
            .andExpect(status().isServiceUnavailable());
    }
}
