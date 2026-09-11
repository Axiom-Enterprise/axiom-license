package com.github.axiomc.license.web.auth;

import java.time.Instant;

public record Account(long id, String username, Role role, Instant createdAt) {
}
