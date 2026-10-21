package com.yusay.api.infrastructure;

import com.yusay.api.application.exception.UserAuthoritiesUnavailableException;
import com.yusay.api.application.port.UserAuthoritiesProvider;
import com.yusay.api.config.LdapProperties;
import org.springframework.ldap.NamingException;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapOperations;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.ldap.userdetails.LdapAuthoritiesPopulator;
import org.springframework.stereotype.Component;

import javax.naming.ldap.Rdn;
import java.util.List;
import java.util.Objects;

@Component
public class LdapUserAuthoritiesProvider implements UserAuthoritiesProvider {
    private final LdapProperties ldapProperties;
    private final LdapOperations ldapOperations;
    private final LdapAuthoritiesPopulator authoritiesPopulator;

    public LdapUserAuthoritiesProvider(
        LdapProperties ldapProperties,
        LdapOperations ldapOperations,
        LdapAuthoritiesPopulator authoritiesPopulator
    ) {
        this.ldapProperties = ldapProperties;
        this.ldapOperations = ldapOperations;
        this.authoritiesPopulator = authoritiesPopulator;
    }

    @Override
    public List<String> findAuthorities(String username) {
        try {
            DirContextOperations userContext = ldapOperations.lookupContext(userDn(username));
            return authoritiesPopulator.getGrantedAuthorities(userContext, username).stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .toList();
        } catch (NamingException exception) {
            throw new UserAuthoritiesUnavailableException(username, exception);
        }
    }

    private String userDn(String username) {
        String userDnPattern = ldapProperties.getUserDnPattern();
        if (userDnPattern == null || !userDnPattern.contains("{0}")) {
            throw new IllegalStateException("LDAP user DN pattern must contain {0}");
        }
        return userDnPattern.replace("{0}", Rdn.escapeValue(username));
    }
}
