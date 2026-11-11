package com.tanmaysinghx.newxr.code;

/** Exposes CiService's package-private status mapping to tests in other packages. */
public final class CiServiceStatusAccess {
    private CiServiceStatusAccess() {}

    public static String status(String s) {
        return CiService.status(s, null);
    }
}
