package com.tanmaysinghx.newxr.user;

import com.tanmaysinghx.newxr.common.ApiException;

/** At least 12 characters with upper case, lower case and a digit. */
public final class PasswordPolicy {

    public static final String DESCRIPTION = "At least 12 characters, with upper case, lower case and a digit.";

    private PasswordPolicy() {}

    public static void check(String password) {
        if (password == null || password.length() < 12
                || password.chars().noneMatch(Character::isUpperCase)
                || password.chars().noneMatch(Character::isLowerCase)
                || password.chars().noneMatch(Character::isDigit)) {
            throw ApiException.badRequest("Password does not meet the policy. " + DESCRIPTION);
        }
        if (password.length() > 128) {
            throw ApiException.badRequest("Password must be at most 128 characters.");
        }
    }
}
