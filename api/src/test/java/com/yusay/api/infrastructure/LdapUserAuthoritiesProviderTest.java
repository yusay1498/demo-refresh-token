package com.yusay.api.infrastructure;

import com.yusay.api.application.exception.UserAuthoritiesUnavailableException;
import com.yusay.api.config.LdapProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapClient;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.ldap.userdetails.LdapAuthoritiesPopulator;

import javax.naming.ldap.Rdn;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LdapUserAuthoritiesProviderTest {
    private static final String USER_DN_PATTERN = "uid={0},ou=people,dc=example,dc=com";

    @Mock
    LdapClient ldapClient;

    @Mock
    LdapClient.SearchSpec searchSpec;

    @Mock
    LdapClient.MappedSearchSpec<DirContextOperations> mappedSearchSpec;

    @Mock
    DirContextOperations userContext;

    @Mock
    LdapAuthoritiesPopulator authoritiesPopulator;

    @Mock
    GrantedAuthority authority;

    private LdapUserAuthoritiesProvider provider;

    @BeforeEach
    void setUp() {
        LdapProperties ldapProperties = new LdapProperties();
        ldapProperties.setUserDnPattern(USER_DN_PATTERN);
        provider = new LdapUserAuthoritiesProvider(ldapProperties, ldapClient, authoritiesPopulator);
        doReturn(searchSpec).when(ldapClient).search();
    }

    @Test
    void givenExistingUser_whenFindAuthorities_thenUsesUserDnAndReturnsPopulatedAuthorities() {
        String username = "alice,ou=admins";
        String userDn = USER_DN_PATTERN.replace("{0}", Rdn.escapeValue(username));
        doReturn(searchSpec).when(searchSpec).name(userDn);
        doReturn(mappedSearchSpec).when(searchSpec)
            .map(org.mockito.ArgumentMatchers.<ContextMapper<DirContextOperations>>any());
        doReturn(Optional.of(userContext)).when(mappedSearchSpec).optional();
        doReturn(List.of(authority)).when(authoritiesPopulator).getGrantedAuthorities(userContext, username);
        doReturn("ROLE_ADMINS").when(authority).getAuthority();

        assertThat(provider.findAuthorities(username)).containsExactly("ROLE_ADMINS");

        verify(searchSpec).name(userDn);
        verify(authoritiesPopulator).getGrantedAuthorities(userContext, username);
    }

    @Test
    void givenMissingUserEntry_whenFindAuthorities_thenReportsAuthoritiesUnavailable() {
        doReturn(searchSpec).when(searchSpec).name("uid=missing,ou=people,dc=example,dc=com");
        doReturn(mappedSearchSpec).when(searchSpec)
            .map(org.mockito.ArgumentMatchers.<ContextMapper<DirContextOperations>>any());
        doReturn(Optional.empty()).when(mappedSearchSpec).optional();

        assertThatThrownBy(() -> provider.findAuthorities("missing"))
            .isInstanceOf(UserAuthoritiesUnavailableException.class)
            .hasCauseInstanceOf(org.springframework.ldap.NameNotFoundException.class);

        verify(authoritiesPopulator, never()).getGrantedAuthorities(any(), any());
    }

    @Test
    void givenInvalidUserDnPattern_whenFindAuthorities_thenRejectsConfiguration() {
        LdapProperties invalidProperties = new LdapProperties();
        invalidProperties.setUserDnPattern("ou=people,dc=example,dc=com");
        LdapUserAuthoritiesProvider invalidProvider =
            new LdapUserAuthoritiesProvider(invalidProperties, ldapClient, authoritiesPopulator);

        assertThatThrownBy(() -> invalidProvider.findAuthorities("alice"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("LDAP user DN pattern must contain {0}");
    }
}
