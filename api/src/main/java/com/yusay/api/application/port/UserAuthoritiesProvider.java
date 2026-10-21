package com.yusay.api.application.port;

import java.util.List;

public interface UserAuthoritiesProvider {
    List<String> findAuthorities(String username);
}
