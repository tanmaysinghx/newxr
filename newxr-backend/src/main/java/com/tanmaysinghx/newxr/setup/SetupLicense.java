package com.tanmaysinghx.newxr.setup;

import com.tanmaysinghx.newxr.common.ApiException;

/** The license an operator must accept before Newxr creates its first administrator. */
public final class SetupLicense {

    public static final String ID = "Apache-2.0";
    public static final String NAME = "Apache License, Version 2.0";

    private SetupLicense() {}

    public static void requireAcceptance(Boolean accepted, String licenseId) {
        if (!Boolean.TRUE.equals(accepted) || !ID.equals(licenseId)) {
            throw ApiException.badRequest("Accept the Apache License 2.0 before continuing setup.");
        }
    }
}
