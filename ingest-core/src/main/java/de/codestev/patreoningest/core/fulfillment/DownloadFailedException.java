package de.codestev.patreoningest.core.fulfillment;

// permanent=true means a 403/404-style failure - the source is almost
// certainly dead, not worth retrying. permanent=false means a transient
// failure (timeout, rate limit, network blip) - eligible for the
// retry/backoff schedule in ExecuteDownloadUseCase.
public class DownloadFailedException extends RuntimeException {

    private final boolean permanent;

    public DownloadFailedException(String message, boolean permanent) {
        super(message);
        this.permanent = permanent;
    }

    public DownloadFailedException(String message, boolean permanent, Throwable cause) {
        super(message, cause);
        this.permanent = permanent;
    }

    public boolean isPermanent() {
        return permanent;
    }
}
