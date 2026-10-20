package com.yusay.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
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

	@Test
	void contextLoads() {
	}

}
