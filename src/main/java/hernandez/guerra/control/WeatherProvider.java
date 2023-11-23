package hernandez.guerra.control;

import hernandez.guerra.model.Location;
import hernandez.guerra.model.Weather;

import java.util.List;

public interface WeatherProvider {
    List<Weather> get(Location location);
}
