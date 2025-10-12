package in.co.kitree.services;

/**
 * Configuration class for AstrologyService
 * Contains constants for API provider selection and other configuration options
 */
public class AstrologyServiceConfig {
    
    // API Provider options
    public static final String PYTHON_SERVER_PROVIDER = "PYTHON_SERVER";
    public static final String FREE_ASTROLOGY_API_PROVIDER = "FREE_ASTROLOGY_API";
    
    // Current API provider - change this to switch between providers
    public static final String ASTROLOGY_API_PROVIDER = FREE_ASTROLOGY_API_PROVIDER;
    
    // API URLs
    public static final String PYTHON_SERVER_BASE_URL = "https://kitree-python-server.salmonmoss-7e006d81.centralindia.azurecontainerapps.io";
    
    // Free Astrology API URLs
    public static final String FREE_ASTROLOGY_API_BASE_URL = "https://json.freeastrologyapi.com";
    public static final String FREE_ASTROLOGY_API_PLANETS_URL = FREE_ASTROLOGY_API_BASE_URL + "/planets";
    public static final String FREE_ASTROLOGY_API_MAHA_DASAS_URL = FREE_ASTROLOGY_API_BASE_URL + "/vimsottari/maha-dasas";
    public static final String FREE_ASTROLOGY_API_MAHA_ANTAR_DASAS_URL = FREE_ASTROLOGY_API_BASE_URL + "/vimsottari/maha-dasas-and-antar-dasas";
    
    // Divisional Charts URLs
    public static final String FREE_ASTROLOGY_API_D2_CHART_URL = FREE_ASTROLOGY_API_BASE_URL + "/d2-chart-info";
    public static final String FREE_ASTROLOGY_API_D3_CHART_URL = FREE_ASTROLOGY_API_BASE_URL + "/d3-chart-info";
    public static final String FREE_ASTROLOGY_API_D4_CHART_URL = FREE_ASTROLOGY_API_BASE_URL + "/d4-chart-info";
    public static final String FREE_ASTROLOGY_API_D5_CHART_URL = FREE_ASTROLOGY_API_BASE_URL + "/d5-chart-info";
    public static final String FREE_ASTROLOGY_API_D6_CHART_URL = FREE_ASTROLOGY_API_BASE_URL + "/d6-chart-info";
    public static final String FREE_ASTROLOGY_API_D7_CHART_URL = FREE_ASTROLOGY_API_BASE_URL + "/d7-chart-info";
    public static final String FREE_ASTROLOGY_API_D8_CHART_URL = FREE_ASTROLOGY_API_BASE_URL + "/d8-chart-info";
    public static final String FREE_ASTROLOGY_API_D9_CHART_URL = FREE_ASTROLOGY_API_BASE_URL + "/navamsa-chart-info";
    public static final String FREE_ASTROLOGY_API_D10_CHART_URL = FREE_ASTROLOGY_API_BASE_URL + "/d10-chart-info";
    public static final String FREE_ASTROLOGY_API_D11_CHART_URL = FREE_ASTROLOGY_API_BASE_URL + "/d11-chart-info";
    public static final String FREE_ASTROLOGY_API_D12_CHART_URL = FREE_ASTROLOGY_API_BASE_URL + "/d12-chart-info";
    public static final String FREE_ASTROLOGY_API_D16_CHART_URL = FREE_ASTROLOGY_API_BASE_URL + "/d16-chart-info";
    public static final String FREE_ASTROLOGY_API_D20_CHART_URL = FREE_ASTROLOGY_API_BASE_URL + "/d20-chart-info";
    public static final String FREE_ASTROLOGY_API_D24_CHART_URL = FREE_ASTROLOGY_API_BASE_URL + "/d24-chart-info";
    public static final String FREE_ASTROLOGY_API_D27_CHART_URL = FREE_ASTROLOGY_API_BASE_URL + "/d27-chart-info";
    public static final String FREE_ASTROLOGY_API_D30_CHART_URL = FREE_ASTROLOGY_API_BASE_URL + "/d30-chart-info";
    public static final String FREE_ASTROLOGY_API_D40_CHART_URL = FREE_ASTROLOGY_API_BASE_URL + "/d40-chart-info";
    public static final String FREE_ASTROLOGY_API_D45_CHART_URL = FREE_ASTROLOGY_API_BASE_URL + "/d45-chart-info";
    public static final String FREE_ASTROLOGY_API_D60_CHART_URL = FREE_ASTROLOGY_API_BASE_URL + "/d60-chart-info";
    
    // API Keys
    public static final String PYTHON_SERVER_API_TOKEN = "D80FE645F582F9E0";
    
    // Free Astrology API settings
    public static final String OBSERVATION_POINT = "topocentric";
    public static final String AYANAMSHA = "lahiri";
    
    // Timeout settings
    public static final int HTTP_TIMEOUT_SECONDS = 10;
    
    /**
     * Check if Free Astrology API provider is currently selected
     */
    public static boolean isFreeAstrologyApiProviderSelected() {
        return FREE_ASTROLOGY_API_PROVIDER.equals(ASTROLOGY_API_PROVIDER);
    }
    
    /**
     * Check if Python server provider is currently selected
     */
    public static boolean isPythonServerProviderSelected() {
        return PYTHON_SERVER_PROVIDER.equals(ASTROLOGY_API_PROVIDER);
    }
    
    /**
     * Get the Free Astrology API URL for a specific divisional chart
     */
    public static String getDivisionalChartUrl(int chartNumber) {
        switch (chartNumber) {
            case 2: return FREE_ASTROLOGY_API_D2_CHART_URL;
            case 3: return FREE_ASTROLOGY_API_D3_CHART_URL;
            case 4: return FREE_ASTROLOGY_API_D4_CHART_URL;
            case 5: return FREE_ASTROLOGY_API_D5_CHART_URL;
            case 6: return FREE_ASTROLOGY_API_D6_CHART_URL;
            case 7: return FREE_ASTROLOGY_API_D7_CHART_URL;
            case 8: return FREE_ASTROLOGY_API_D8_CHART_URL;
            case 9: return FREE_ASTROLOGY_API_D9_CHART_URL;
            case 10: return FREE_ASTROLOGY_API_D10_CHART_URL;
            case 11: return FREE_ASTROLOGY_API_D11_CHART_URL;
            case 12: return FREE_ASTROLOGY_API_D12_CHART_URL;
            case 16: return FREE_ASTROLOGY_API_D16_CHART_URL;
            case 20: return FREE_ASTROLOGY_API_D20_CHART_URL;
            case 24: return FREE_ASTROLOGY_API_D24_CHART_URL;
            case 27: return FREE_ASTROLOGY_API_D27_CHART_URL;
            case 30: return FREE_ASTROLOGY_API_D30_CHART_URL;
            case 40: return FREE_ASTROLOGY_API_D40_CHART_URL;
            case 45: return FREE_ASTROLOGY_API_D45_CHART_URL;
            case 60: return FREE_ASTROLOGY_API_D60_CHART_URL;
            default: throw new IllegalArgumentException("Unsupported divisional chart number: " + chartNumber);
        }
    }
}
