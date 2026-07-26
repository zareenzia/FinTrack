package org.example.finzin.purchaseplanner;

/** Mirrors org.example.finzin.receipts.ReceiptException (errorTag + user-safe message); HTTP status mapping stays in the controller. */
public class PurchaseItemException extends RuntimeException {
    private final String errorTag;
    private final String userMessage;

    private PurchaseItemException(String errorTag, String userMessage) {
        super(errorTag);
        this.errorTag = errorTag;
        this.userMessage = userMessage;
    }

    public String getErrorTag() { return errorTag; }
    public String getUserMessage() { return userMessage; }

    /** The purchase item is already PURCHASED/CANCELLED and can't be moved through another transition. */
    public static PurchaseItemException alreadyFinalized(String status) {
        return new PurchaseItemException("ALREADY_FINALIZED",
                "This purchase is already " + status.toLowerCase() + " and can't be changed further.");
    }

    /** Any other invalid input: bad enum value, malformed date, unowned/missing transaction id, etc. */
    public static PurchaseItemException badRequest(String message) {
        return new PurchaseItemException("BAD_REQUEST", message);
    }
}
