package in.co.kitree.pojos;

public class PythonLambdaResponseBody {
    String certificate;

    String streamUserToken;

    String reportLink; // For numerology

    public String getAuraReportLink() {
        return auraReportLink;
    }

    public void setAuraReportLink(String auraReportLink) {
        this.auraReportLink = auraReportLink;
    }

    String auraReportLink; // For aura report


    public String getReportLink() {
        return reportLink;
    }

    public void setReportLink(String reportLink) {
        this.reportLink = reportLink;
    }

    public String getCertificate() {
        return certificate;
    }

    public void setCertificate(String certificate) {
        this.certificate = certificate;
    }

    public String getStreamUserToken() {
        return streamUserToken;
    }

    public void setStreamUserToken(String streamUserToken) {
        this.streamUserToken = streamUserToken;
    }
}
