package io.github.prasantmohanty.jmeter.backendlistener.junit.transform;

public class JtlRecord {
    private final String label;
    private final boolean success;
    private final String responseMessage;
    private final String failureMessage;
    private final String requestHeaders;
    private final String requestBody;
    private final String responseHeaders;
    private final String responseBody;
    private final String responseCode;

    public JtlRecord(String label, boolean success, String responseMessage, String failureMessage) {
        this(label, success, responseMessage, failureMessage, null, null, null, null, null);
    }

    public JtlRecord(String label,
                     boolean success,
                     String responseMessage,
                     String failureMessage,
                     String requestHeaders,
                     String requestBody,
                     String responseHeaders,
                     String responseBody,
                     String responseCode) {
        this.label = label;
        this.success = success;
        this.responseMessage = responseMessage;
        this.failureMessage = failureMessage;
        this.requestHeaders = requestHeaders;
        this.requestBody = requestBody;
        this.responseHeaders = responseHeaders;
        this.responseBody = responseBody;
        this.responseCode = responseCode;
    }

    public String getLabel() {
        return label;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public String getRequestHeaders() { return requestHeaders; }

    public String getRequestBody() { return requestBody; }

    public String getResponseHeaders() { return responseHeaders; }

    public String getResponseBody() { return responseBody; }

    public String getResponseCode() { return responseCode; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JtlRecord)) return false;

        JtlRecord jtlRecord = (JtlRecord) o;

        if (success != jtlRecord.success) return false;
        if (label != null ? !label.equals(jtlRecord.label) : jtlRecord.label != null) return false;
        if (responseMessage != null ? !responseMessage.equals(jtlRecord.responseMessage) : jtlRecord.responseMessage != null)
            return false;
        if (failureMessage != null ? !failureMessage.equals(jtlRecord.failureMessage) : jtlRecord.failureMessage != null)
            return false;
        if (requestHeaders != null ? !requestHeaders.equals(jtlRecord.requestHeaders) : jtlRecord.requestHeaders != null)
            return false;
        if (requestBody != null ? !requestBody.equals(jtlRecord.requestBody) : jtlRecord.requestBody != null)
            return false;
        if (responseHeaders != null ? !responseHeaders.equals(jtlRecord.responseHeaders) : jtlRecord.responseHeaders != null)
            return false;
        if (responseBody != null ? !responseBody.equals(jtlRecord.responseBody) : jtlRecord.responseBody != null)
            return false;
        return !(responseCode != null ? !responseCode.equals(jtlRecord.responseCode) : jtlRecord.responseCode != null);

    }

    @Override
    public int hashCode() {
    int result = label != null ? label.hashCode() : 0;
    result = 31 * result + (success ? 1 : 0);
    result = 31 * result + (responseMessage != null ? responseMessage.hashCode() : 0);
    result = 31 * result + (failureMessage != null ? failureMessage.hashCode() : 0);
    result = 31 * result + (requestHeaders != null ? requestHeaders.hashCode() : 0);
    result = 31 * result + (requestBody != null ? requestBody.hashCode() : 0);
    result = 31 * result + (responseHeaders != null ? responseHeaders.hashCode() : 0);
    result = 31 * result + (responseBody != null ? responseBody.hashCode() : 0);
    result = 31 * result + (responseCode != null ? responseCode.hashCode() : 0);
    return result;
    }

    @Override
    public String toString() {
    return "JtlRecord{" +
        "label='" + label + '\'' +
        ", success=" + success +
        ", responseMessage='" + responseMessage + '\'' +
        ", failureMessage='" + failureMessage + '\'' +
        ", requestHeaders='" + requestHeaders + '\'' +
        ", requestBody='" + requestBody + '\'' +
        ", responseHeaders='" + responseHeaders + '\'' +
        ", responseBody='" + responseBody + '\'' +
        ", responseCode='" + responseCode + '\'' +
        '}';
    }
}
