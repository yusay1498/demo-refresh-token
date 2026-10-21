package com.yusay.api.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.ldap.core.support.BaseLdapPathContextSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.ldap.LdapBindAuthenticationManagerFactory;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;
import org.springframework.security.ldap.userdetails.LdapAuthoritiesPopulator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private final LdapProperties ldapProps;
    private final JwtProperties jwtProps;
    private final CorsProperties corsProps;

    public SecurityConfig(LdapProperties ldapProps, JwtProperties jwtProps, CorsProperties corsProps) {
        this.ldapProps = ldapProps;
        this.jwtProps = jwtProps;
        this.corsProps = corsProps;
    }

    @Bean
    BaseLdapPathContextSource contextSource() {
        DefaultSpringSecurityContextSource context = new DefaultSpringSecurityContextSource(ldapProps.getUrl());

        if (StringUtils.hasLength(ldapProps.getAdminDn()) && StringUtils.hasLength(ldapProps.getAdminPassword())) {
            context.setUserDn(ldapProps.getAdminDn());
            context.setPassword(ldapProps.getAdminPassword());
        }

        return context;
    }

    @Bean
    LdapAuthoritiesPopulator authoritiesPopulator(
            BaseLdapPathContextSource contextSource) {
        DefaultLdapAuthoritiesPopulator populator =
                new DefaultLdapAuthoritiesPopulator(contextSource, ldapProps.getGroupSearchBase());
        populator.setGroupSearchFilter(ldapProps.getGroupSearchFilter());
        populator.setIgnorePartialResultException(true);
        populator.setSearchSubtree(true);

        return populator;
    }

    @Bean
    AuthenticationManager ldapAuthenticationManager(
            BaseLdapPathContextSource contextSource, LdapAuthoritiesPopulator authoritiesPopulator) {
        LdapBindAuthenticationManagerFactory factory = new LdapBindAuthenticationManagerFactory(contextSource);
        factory.setUserDnPatterns(ldapProps.getUserDnPattern());

        if (StringUtils.hasLength(ldapProps.getAdminDn()) && StringUtils.hasLength(ldapProps.getAdminPassword())) {
            factory.setLdapAuthoritiesPopulator(authoritiesPopulator);
        }

        return factory.createAuthenticationManager();
    }

    @Bean
    SecretKey jwtSecretKey() {
        return new SecretKeySpec(jwtProps.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        decoder.setJwtValidator(JwtValidators
                .createDefaultWithIssuer(jwtProps.getIssuer()));

        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("authorities");
        authorities.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);

        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(corsProps.getAllowOrigins());

        configuration.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()
        ));
        configuration.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity httpSecurity, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        httpSecurity
                .csrf(CsrfConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                    .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/auth/signIn",
                        "/auth/refresh",
                        "/auth/signOut"
                    ).permitAll()
                    .requestMatchers(HttpMethod.GET, "/auth/anon").permitAll()
                    .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return httpSecurity.build();
    }
}
