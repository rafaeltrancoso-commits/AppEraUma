package com.rrsistemas.erauma.story;

public class AiImageGenerationException extends AiGenerationException {
    private final AiImageFailureReason reason;
    private final String providerErrorCode;
    private final String providerRequestId;

    public AiImageGenerationException(AiImageFailureReason reason, String message, String providerErrorCode, String providerRequestId) {
        super(message);
        this.reason = reason;
        this.providerErrorCode = providerErrorCode;
        this.providerRequestId = providerRequestId;
    }

    public AiImageGenerationException(AiImageFailureReason reason, String message, Throwable cause, String providerErrorCode, String providerRequestId) {
        super(message, cause);
        this.reason = reason;
        this.providerErrorCode = providerErrorCode;
        this.providerRequestId = providerRequestId;
    }

    public AiImageFailureReason reason() {
        return reason;
    }

    public String providerErrorCode() {
        return providerErrorCode;
    }

    public String providerRequestId() {
        return providerRequestId;
    }
}
