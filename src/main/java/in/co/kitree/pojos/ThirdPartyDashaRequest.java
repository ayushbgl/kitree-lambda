package in.co.kitree.pojos;

import java.util.Map;

public class ThirdPartyDashaRequest {
    private int year;
    private int month;
    private int date;
    private int hours;
    private int minutes;
    private int seconds;
    private double latitude;
    private double longitude;
    private double timezone;
    private Map<String, String> config;

    public ThirdPartyDashaRequest() {
    }

    public ThirdPartyDashaRequest(int year, int month, int date, int hours, int minutes, 
                                int seconds, double latitude, double longitude, 
                                double timezone, Map<String, String> config) {
        this.year = year;
        this.month = month;
        this.date = date;
        this.hours = hours;
        this.minutes = minutes;
        this.seconds = seconds;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timezone = timezone;
        this.config = config;
    }

    // Getters and Setters
    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public int getDate() {
        return date;
    }

    public void setDate(int date) {
        this.date = date;
    }

    public int getHours() {
        return hours;
    }

    public void setHours(int hours) {
        this.hours = hours;
    }

    public int getMinutes() {
        return minutes;
    }

    public void setMinutes(int minutes) {
        this.minutes = minutes;
    }

    public int getSeconds() {
        return seconds;
    }

    public void setSeconds(int seconds) {
        this.seconds = seconds;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getTimezone() {
        return timezone;
    }

    public void setTimezone(double timezone) {
        this.timezone = timezone;
    }

    public Map<String, String> getConfig() {
        return config;
    }

    public void setConfig(Map<String, String> config) {
        this.config = config;
    }
}
