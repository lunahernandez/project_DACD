package hernandez.guerra.control;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import hernandez.guerra.exceptions.AccommodationProviderException;
import hernandez.guerra.model.Accommodation;
import hernandez.guerra.model.Location;
import hernandez.guerra.model.LocationArea;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public class AirbnbProvider implements AccommodationProvider {
    private final String apiKey;

    public AirbnbProvider(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public List<Accommodation> get(Location location) throws AccommodationProviderException {
        Instant now = Instant.now();
        Instant checkInDate = calculateCheckInDate(now);
        Instant checkOutDate = checkInDate.plus(5, ChronoUnit.DAYS);
        LocationArea locationArea = location.locationArea();

        JsonObject jsonObject = getJsonObjectFromAccommodationProvider(
                locationArea.neLat(), locationArea.neLng(), locationArea.swLat(), locationArea.swLng(),
                checkInDate, checkOutDate, apiKey
        );
        return convertJsonToAccommodationList(jsonObject, location);
    }

    private Instant calculateCheckInDate(Instant now) {
        LocalDate currentDate = Instant.ofEpochSecond(now.getEpochSecond()).atZone(ZoneOffset.UTC).toLocalDate();
        LocalTime currentTime = Instant.ofEpochSecond(now.getEpochSecond()).atZone(ZoneOffset.UTC).toLocalTime();

        LocalDate checkInDate = currentTime.isBefore(LocalTime.NOON) ? currentDate : currentDate.plusDays(1);

        return checkInDate.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private JsonObject getJsonObjectFromAccommodationProvider(
            String neLat, String neLng, String swLat, String swLng,
            Instant checkInDate, Instant checkOutDate, String apiKey
    ) throws AccommodationProviderException {
        try {
            HttpURLConnection connection = openAccommodationProviderConnection(neLat, neLng, swLat, swLng,
                    checkInDate, checkOutDate, apiKey);
            return getJsonObjectFromConnection(connection);
        } catch (IOException e) {
            throw new AccommodationProviderException(e.getMessage(), e);
        }
    }

    private HttpURLConnection openAccommodationProviderConnection(
            String neLat, String neLng, String swLat, String swLng,
            Instant checkInDate, Instant checkOutDate, String apiKey
    ) throws IOException {
        URL url = getUrl(neLat, neLng, swLat, swLng, checkInDate, checkOutDate);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        connection.setRequestProperty("X-Rapidapi-Key", apiKey);
        connection.setRequestProperty("X-Rapidapi-Host", "airbnb13.p.rapidapi.com");

        return connection;
    }

    private JsonObject getJsonObjectFromConnection(HttpURLConnection connection) throws AccommodationProviderException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new AccommodationProviderException(e.getMessage(), e);
        } finally {
            connection.disconnect();
        }
    }

    private URL getUrl(String neLat, String neLng, String swLat, String swLng, Instant checkInDate, Instant checkOutDate)
            throws MalformedURLException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String formattedCheckInDate = dateFormat.format(Date.from(checkInDate));
        String formattedCheckOutDate = dateFormat.format(Date.from(checkOutDate));

        String apiUrl = "https://airbnb13.p.rapidapi.com/search-geo?ne_lat=" + neLat + "&ne_lng=" + neLng +
                "&sw_lat=" + swLat + "&sw_lng=" + swLng + "&checkin=" + formattedCheckInDate + "&checkout=" +
                formattedCheckOutDate + "&adults=2";
        return new URL(apiUrl);
    }

    private List<Accommodation> convertJsonToAccommodationList(JsonObject jsonObject, Location location) {
        List<Accommodation> accommodationList = new ArrayList<>();
        jsonObject.getAsJsonArray("results").forEach(element ->
                Optional.ofNullable(createAccommodationFromJson(element.getAsJsonObject(), location))
                        .ifPresent(accommodationList::add)
        );
        return accommodationList;
    }

    private Accommodation createAccommodationFromJson(JsonObject accommodationInfo, Location location) {
        List<String> generalAttributes = obtainGeneralAccommodationInfo(accommodationInfo);

        return (generalAttributes.contains("") || isPriceInvalid(accommodationInfo))
                ? null
                : new Accommodation(Instant.now(), "AccommodationProvider", generalAttributes.get(0),
                generalAttributes.get(1), location, generalAttributes.get(2), generalAttributes.get(3),
                generalAttributes.get(4),
                accommodationInfo.has("reviewsCount") ? accommodationInfo.get("reviewsCount").getAsInt() : 0,
                accommodationInfo.has("rating") ? accommodationInfo.get("rating").getAsDouble() : 0.0,
                accommodationInfo.getAsJsonObject("price").get("total").getAsInt());
    }

    private List<String> obtainGeneralAccommodationInfo(JsonObject accommodationInfo) {
        return List.of(
                accommodationInfo.has("url") ? accommodationInfo.get("url").getAsString() : "",
                accommodationInfo.has("name") ? accommodationInfo.get("name").getAsString() : "",
                accommodationInfo.has("city") ? accommodationInfo.get("city").getAsString() : "",
                accommodationInfo.has("lat") ? accommodationInfo.get("lat").getAsString() : "",
                accommodationInfo.has("lng") ? accommodationInfo.get("lng").getAsString() : ""
        );
    }


    private boolean isPriceInvalid(JsonObject accommodationInfo) {
        JsonElement priceElement = accommodationInfo.get("price");
        return priceElement == null || priceElement.isJsonNull() || priceElement.getAsJsonObject().get("total") == null;
    }

}
