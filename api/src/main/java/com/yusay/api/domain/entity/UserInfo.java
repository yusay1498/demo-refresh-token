package com.yusay.api.domain.entity;

import java.util.List;

public record UserInfo(String username, List<String> authorities) {}
