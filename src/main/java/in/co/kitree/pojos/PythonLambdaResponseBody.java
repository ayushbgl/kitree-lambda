package in.co.kitree.pojos;

import java.util.List;
import java.util.Map;

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

    List<Map<String, String>> courses; // For certificate courses


    public List<Map<String, String>> getCourses() {
        return courses;
    }

    public void setCourses(List<Map<String, String>> courses) {
        this.courses = courses;
    }

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
