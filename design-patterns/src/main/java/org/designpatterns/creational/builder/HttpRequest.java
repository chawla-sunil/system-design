package org.designpatterns.creational.builder;

/**
 * Builder Pattern - HTTP Request Builder Example
 *
 * Separates construction of a complex object from its representation.
 * Useful when an object has many optional parameters.
 */
public class HttpRequest {

    private final String url;
    private final String method;
    private final String body;
    private final String contentType;
    private final String authorization;
    private final int timeout;
    private final boolean followRedirects;

    // Private constructor - only accessible via Builder
    private HttpRequest(Builder builder) {
        this.url = builder.url;
        this.method = builder.method;
        this.body = builder.body;
        this.contentType = builder.contentType;
        this.authorization = builder.authorization;
        this.timeout = builder.timeout;
        this.followRedirects = builder.followRedirects;
    }

    public String getUrl() { return url; }
    public String getMethod() { return method; }
    public String getBody() { return body; }
    public String getContentType() { return contentType; }
    public String getAuthorization() { return authorization; }
    public int getTimeout() { return timeout; }
    public boolean isFollowRedirects() { return followRedirects; }

    @Override
    public String toString() {
        return "HttpRequest{" +
                "method='" + method + '\'' +
                ", url='" + url + '\'' +
                ", contentType='" + contentType + '\'' +
                ", authorization='" + (authorization != null ? "***" : "none") + '\'' +
                ", timeout=" + timeout +
                ", followRedirects=" + followRedirects +
                ", body='" + (body != null ? body.substring(0, Math.min(body.length(), 30)) + "..." : "null") + '\'' +
                '}';
    }

    /** Static inner Builder class */
    public static class Builder {
        // Required parameters
        private final String url;

        // Optional parameters with defaults
        private String method = "GET";
        private String body = null;
        private String contentType = "application/json";
        private String authorization = null;
        private int timeout = 30000;
        private boolean followRedirects = true;

        public Builder(String url) {
            this.url = url;
        }

        public Builder method(String method) {
            this.method = method;
            return this; // enables method chaining
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder authorization(String authorization) {
            this.authorization = authorization;
            return this;
        }

        public Builder timeout(int timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder followRedirects(boolean followRedirects) {
            this.followRedirects = followRedirects;
            return this;
        }

        public HttpRequest build() {
            // Validation logic
            if (url == null || url.isBlank()) {
                throw new IllegalStateException("URL is required");
            }
            if (("POST".equals(method) || "PUT".equals(method)) && body == null) {
                throw new IllegalStateException(method + " request requires a body");
            }
            return new HttpRequest(this);
        }
    }
}
