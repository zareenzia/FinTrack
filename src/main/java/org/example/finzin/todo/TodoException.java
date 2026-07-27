package org.example.finzin.todo;

/** Mirrors org.example.finzin.purchaseplanner.PurchaseItemException (errorTag + user-safe message); HTTP status mapping stays in the controller. */
public class TodoException extends RuntimeException {
    private final String errorTag;
    private final String userMessage;

    private TodoException(String errorTag, String userMessage) {
        super(errorTag);
        this.errorTag = errorTag;
        this.userMessage = userMessage;
    }

    public String getErrorTag() { return errorTag; }
    public String getUserMessage() { return userMessage; }

    /** Any invalid input: blank title, unowned parent, malformed date, etc. */
    public static TodoException badRequest(String message) {
        return new TodoException("BAD_REQUEST", message);
    }

    /** Attempted to nest a sub-item under an item that is itself already a sub-item. */
    public static TodoException nestingTooDeep() {
        return new TodoException("NESTING_TOO_DEEP", "A step can't have its own steps — only one level of sub-items is supported.");
    }
}
