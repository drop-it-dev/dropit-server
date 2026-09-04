package com.dropit.user.entity;

public enum UserRole {
    SELLER,
    USER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
