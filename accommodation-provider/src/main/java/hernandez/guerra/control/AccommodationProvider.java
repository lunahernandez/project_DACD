package hernandez.guerra.control;

import hernandez.guerra.model.Accommodation;
import hernandez.guerra.model.LocationArea;

import java.util.List;

public interface AccommodationProvider {
    List<Accommodation> get(LocationArea locationArea);

}
