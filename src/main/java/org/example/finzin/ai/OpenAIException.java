package org.example.finzin.ai;

/**
 * Unified error type for anything that can go wrong talking to the OpenAI Responses API or
 * processing its output. Carries a short machine-readable tag (for logging), a user-safe
 * message (for the chat UI), and whether retrying is worth suggesting to the user.
 */
public class OpenAIException extends RuntimeException {
    private final String errorTag;
    private final String userMessage;
    private final boolean retryable;

    private OpenAIException(String errorTag, String userMessage, boolean retryable) {
        super(errorTag);
        this.errorTag = errorTag;
        this.userMessage = userMessage;
        this.retryable = retryable;
    }

    public String getErrorTag() { return errorTag; }
    public String getUserMessage() { return userMessage; }
    public boolean isRetryable() { return retryable; }

    public static OpenAIException rateLimit() {
        return new OpenAIException("RATE_LIMIT", "Our AI assistant is a bit busy right now — please try again in a moment.", true);
    }

    public static OpenAIException timeout() {
        return new OpenAIException("TIMEOUT", "The AI assistant is taking longer than expected. Please try again.", true);
    }

    public static OpenAIException authError() {
        return new OpenAIException("AUTH_ERROR", "AI assistant is temporarily unavailable. Please contact support.", false);
    }

    public static OpenAIException upstreamError() {
        return new OpenAIException("UPSTREAM_ERROR", "The AI service is having issues. Please try again shortly.", true);
    }

    public static OpenAIException emptyResponse() {
        return new OpenAIException("EMPTY_RESPONSE", "I couldn't generate a response — please rephrase your question.", true);
    }

    public static OpenAIException malformedResponse() {
        return new OpenAIException("PARSE_ERROR", "Something went wrong processing that response. Please try again.", true);
    }

    public static OpenAIException toolLoopLimit() {
        return new OpenAIException("TOOL_LOOP_LIMIT", "I'm having trouble answering that — try rephrasing your question.", true);
    }

    public static OpenAIException notConfigured() {
        return new OpenAIException("NOT_CONFIGURED", "AI Assistant is not configured on this server.", false);
    }

    public static OpenAIException disabled() {
        return new OpenAIException("DISABLED", "AI Assistant is currently disabled. Enable it in Settings to start chatting.", false);
    }

    public static OpenAIException tooManyRequestsFromUser() {
        return new OpenAIException("USER_RATE_LIMIT", "You're sending messages a bit too quickly — please wait a moment and try again.", true);
    }
}
