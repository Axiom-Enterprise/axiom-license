package com.github.axiomc.license.web.auth;

public enum Role {
    ADMIN,
    MOD;

    public boolean covers(Role required) {
        return switch (this) {
            case ADMIN -> true;
            case MOD -> required == MOD;
        };
    }

    public String label() {
        return switch (this) {
            case ADMIN -> "Admin";
            case MOD -> "Moderator";
        };
    }
}
