package org.example.finzin.family;

/** Mirrors org.example.finzin.purchaseplanner.PurchaseItemException (errorTag + user-safe message); HTTP status mapping stays in the controller. */
public class FamilyException extends RuntimeException {
    private final String errorTag;
    private final String userMessage;

    private FamilyException(String errorTag, String userMessage) {
        super(errorTag);
        this.errorTag = errorTag;
        this.userMessage = userMessage;
    }

    public String getErrorTag() { return errorTag; }
    public String getUserMessage() { return userMessage; }

    public static FamilyException notMember() {
        return new FamilyException("NOT_MEMBER", "You are not a member of this household.");
    }

    public static FamilyException notAdmin() {
        return new FamilyException("NOT_ADMIN", "Only the household administrator can do this.");
    }

    public static FamilyException notFound(String what) {
        return new FamilyException("NOT_FOUND", what + " not found.");
    }

    public static FamilyException badRequest(String message) {
        return new FamilyException("BAD_REQUEST", message);
    }

    public static FamilyException conflict(String message) {
        return new FamilyException("CONFLICT", message);
    }
}
