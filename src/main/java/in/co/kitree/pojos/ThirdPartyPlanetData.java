package in.co.kitree.pojos;

public class ThirdPartyPlanetData {
    private String name;
    private double fullDegree;
    private double normDegree;
    private String isRetro;
    private int currentSign;

    public ThirdPartyPlanetData() {
    }

    public ThirdPartyPlanetData(String name, double fullDegree, double normDegree, 
                               String isRetro, int currentSign) {
        this.name = name;
        this.fullDegree = fullDegree;
        this.normDegree = normDegree;
        this.isRetro = isRetro;
        this.currentSign = currentSign;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getFullDegree() {
        return fullDegree;
    }

    public void setFullDegree(double fullDegree) {
        this.fullDegree = fullDegree;
    }

    public double getNormDegree() {
        return normDegree;
    }

    public void setNormDegree(double normDegree) {
        this.normDegree = normDegree;
    }

    public String getIsRetro() {
        return isRetro;
    }

    public void setIsRetro(String isRetro) {
        this.isRetro = isRetro;
    }

    public int getCurrentSign() {
        return currentSign;
    }

    public void setCurrentSign(int currentSign) {
        this.currentSign = currentSign;
    }
}
