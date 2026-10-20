package com.yusay.api.domain;

import java.util.List;

public record UserInfo(String username, List<String> authorities) {}
