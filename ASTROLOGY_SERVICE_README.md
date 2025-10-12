# AstrologyService Documentation

## Overview

The `AstrologyService` is a configurable service that provides astrological calculations by integrating with multiple API providers. It currently supports:

1. **Python Server API** (existing implementation)
2. **Free Astrology API** (new implementation)

## Configuration

The service uses `AstrologyServiceConfig` to manage configuration. To switch between API providers, modify the `ASTROLOGY_API_PROVIDER` constant in `AstrologyServiceConfig.java`:

```java
// Use Python server (default)
public static final String ASTROLOGY_API_PROVIDER = PYTHON_SERVER_PROVIDER;

// Use Free Astrology API
public static final String ASTROLOGY_API_PROVIDER = FREE_ASTROLOGY_API_PROVIDER;
```

## API Methods

### 1. getAstrologicalDetails(RequestBody requestBody)

Returns horoscope/planetary positions based on birth data.

**Required fields:**
- `horoscopeDate` (Integer): Day of birth
- `horoscopeMonth` (Integer): Month of birth  
- `horoscopeYear` (Integer): Year of birth
- `horoscopeHour` (Integer): Hour of birth (0-23)
- `horoscopeMinute` (Integer): Minute of birth (0-59)
- `horoscopeLatitude` (Double): Birth latitude
- `horoscopeLongitude` (Double): Birth longitude

**Response format:**
```json
{
  "ascendant": {
    "sign": 9,
    "is_retrograde": false,
    "longitude": 20.15
  },
  "sun": {
    "sign": 4,
    "is_retrograde": false,
    "longitude": 24.61
  },
  // ... other planets
}
```

### 2. getDashaDetails(RequestBody requestBody)

Returns dasha (planetary periods) information using Vimsottari Dasa system.

**Required fields:**
- `dashaDate`, `dashaMonth`, `dashaYear`, `dashaHour`, `dashaMinute`
- `dashaLatitude`, `dashaLongitude`
- `dashaPrefix` (List<String>): Planet hierarchy for dasha calculation

**API Endpoints Used:**
- **Maha Dasas Only**: `/vimsottari/maha-dasas` (when no prefix provided)
- **Maha Dasas + Antar Dasas**: `/vimsottari/maha-dasas-and-antar-dasas` (when prefix provided)

**Response format:**
```json
{
  "1": {
    "planet_name": "Venus",
    "date_str": "1988-08-30 11:18:53.715911"
  },
  "2": {
    "planet_name": "Sun", 
    "date_str": "2008-08-30 14:22:10.707911"
  },
  "current_level_calculated": 1,
  "prefix_used": ["Sun", "Moon"]
}
```

### 3. getDivisionalCharts(RequestBody requestBody)

Returns divisional chart calculations.

**Required fields:**
- `horoscopeDate`, `horoscopeMonth`, `horoscopeYear`, `horoscopeHour`, `horoscopeMinute`
- `horoscopeLatitude`, `horoscopeLongitude`
- `divisionalChartNumbers` (List<Integer>): Chart numbers to calculate (e.g., [2, 9] for D2 and D9)

**Note:** Free Astrology API implementation is now complete for all three methods.

## Free Astrology API Integration

### Setup

1. **API Key**: Set the `ASTROLOGY_API_KEY` environment variable with your API key
2. **Configuration**: Change `ASTROLOGY_API_PROVIDER` to `FREE_ASTROLOGY_API_PROVIDER` in `AstrologyServiceConfig.java`

### API Details

- **Base URL**: `https://json.freeastrologyapi.com`
- **Planets Endpoint**: `/planets` - For horoscope/planetary positions
- **Maha Dasas Endpoint**: `/vimsottari/maha-dasas` - For Vimsottari Maha Dasas only
- **Maha + Antar Dasas Endpoint**: `/vimsottari/maha-dasas-and-antar-dasas` - For both Maha and Antar Dasas
- **Divisional Chart Endpoints**:
  - `/d2-chart-info` - D2 (Hora) Chart
  - `/d3-chart-info` - D3 (Drekkana) Chart  
  - `/d4-chart-info` - D4 (Chaturamsa) Chart
  - `/d5-chart-info` - D5 (Panchamsha) Chart
  - `/d6-chart-info` - D6 (Shasthamsa) Chart
  - `/d7-chart-info` - D7 (Saptamsa) Chart
  - `/d8-chart-info` - D8 (Ashtamsa) Chart
  - `/navamsa-chart-info` - D9 (Navamsa) Chart
  - `/d10-chart-info` - D10 (Dasamsa) Chart
  - `/d11-chart-info` - D11 (Rudramsa) Chart
  - `/d12-chart-info` - D12 (Dwadasamsa) Chart
  - `/d16-chart-info` - D16 (Shodashamsa) Chart
  - `/d20-chart-info` - D20 (Vimshamsha) Chart
  - `/d24-chart-info` - D24 (Chaturvimsamsa) Chart
  - `/d27-chart-info` - D27 (Saptavimsamsa) Chart
  - `/d30-chart-info` - D30 (Trimshamsha) Chart
  - `/d40-chart-info` - D40 (Khavedamsa) Chart
  - `/d45-chart-info` - D45 (Akshavedamsa) Chart
  - `/d60-chart-info` - D60 (Shashtiamsa) Chart
- **Method**: POST
- **Headers**: 
  - `Content-Type: application/json`
  - `x-api-key: YOUR_API_KEY`

### Request Format

**For Planets/Horoscope:**
```json
{
  "year": 1990,
  "month": 8,
  "date": 15,
  "hours": 10,
  "minutes": 30,
  "seconds": 0,
  "latitude": 28.6139,
  "longitude": 77.2090,
  "timezone": 5.5,
  "settings": {
    "observation_point": "topocentric",
    "ayanamsha": "lahiri"
  }
}
```

**For Dasha Calculations:**
```json
{
  "year": 1994,
  "month": 4,
  "date": 30,
  "hours": 12,
  "minutes": 45,
  "seconds": 0,
  "latitude": 21.1904,
  "longitude": 81.28491,
  "timezone": 5.5,
  "config": {
    "observation_point": "topocentric",
    "ayanamsha": "lahiri"
  }
}
```

### Response Transformation

The service automatically transforms the Free Astrology API response to match the expected format from the Python server API, ensuring compatibility with existing mobile app code.

## Timezone Calculation

The service includes `TimezoneUtils` for accurate timezone detection from latitude/longitude coordinates using the [Timeshape library](https://github.com/RomanIakovlev/timeshape):

```java
// Get timezone offset in hours
double timezone = TimezoneUtils.getTimezoneOffset(latitude, longitude);

// Get timezone ID (e.g., "Asia/Kolkata", "America/New_York")
String timezoneId = TimezoneUtils.getTimezoneId(latitude, longitude);

// Get ZoneId object for more advanced operations
Optional<ZoneId> zoneId = TimezoneUtils.getZoneId(latitude, longitude);

// Get ZoneOffset object
ZoneOffset zoneOffset = TimezoneUtils.getZoneOffset(latitude, longitude);
```

### Timeshape Integration

The service uses [Timeshape](https://github.com/RomanIakovlev/timeshape) for accurate timezone detection based on actual timezone boundaries from OpenStreetMap data. This provides much better accuracy than simple longitude-based calculations.

**Key Features:**
- **Accurate Detection**: Uses real timezone boundaries instead of simple longitude calculations
- **DST Support**: Automatically handles Daylight Saving Time transitions
- **Performance Optimized**: Uses accelerated geometry for faster lookups
- **Fallback Support**: Falls back to longitude-based calculation if Timeshape fails
- **Thread-Safe**: Singleton pattern with proper synchronization

**Dependencies:**
```gradle
implementation 'net.iakovlev:timeshape:2025b.26'
```

## Testing

### Unit Tests

Run the AstrologyService tests:

```bash
./gradlew test --tests "in.co.kitree.services.AstrologyServiceTest"
```

### Third-Party API Tests

To test the third-party API integration:

1. Set the `ASTROLOGY_API_KEY` environment variable
2. Run the specific test:

```bash
./gradlew test --tests "in.co.kitree.services.ThirdPartyAstrologyServiceTest"
```

### E2E Tests

The service includes comprehensive E2E tests that verify:
- Input validation
- API response handling
- Error scenarios
- Timezone calculations

## Error Handling

The service provides consistent error responses:

```json
{
  "success": false,
  "errorMessage": "Missing required horoscope details"
}
```

Common error scenarios:
- Missing required fields
- Invalid API key (for third-party API)
- Network timeouts
- API service unavailability

## Migration Guide

### From Handler to AstrologyService

The astrology-related methods have been extracted from `Handler.java` to `AstrologyService.java`. The Handler now delegates to AstrologyService:

```java
// Old way (in Handler)
return getAstrologyDetails(gson.toJson(horoscopeApiRequestBody));

// New way (in Handler)
return astrologyService.getAstrologicalDetails(requestBody);
```

### Switching API Providers

1. **For Python Server** (default):
   ```java
   public static final String ASTROLOGY_API_PROVIDER = PYTHON_SERVER_PROVIDER;
   ```

2. **For Third-Party API**:
   ```java
   public static final String ASTROLOGY_API_PROVIDER = THIRD_PARTY_PROVIDER;
   ```

## Future Enhancements

- [ ] Implement third-party API for dasha calculations
- [ ] Implement third-party API for divisional charts
- [ ] Add caching for API responses
- [ ] Add retry logic for failed requests
- [ ] Add metrics and monitoring
- [ ] Support for additional third-party astrology APIs
