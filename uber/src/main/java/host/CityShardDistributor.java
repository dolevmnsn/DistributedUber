package host;

import entities.City;

import java.util.Arrays;

public class CityShardDistributor {
    public static int getShardIdByCity(City city) {
        return (Arrays.asList(City.values()).indexOf(city) % ConfigurationManager.NUM_OF_SHARDS) + 1;
    }

//    private static Map<City, Integer> CITY_TO_SHARD_ID = IntStream
//            .range(0, City.values().length)
//            .mapToObj(i -> new AbstractMap.SimpleEntry<>(City.values()[i], i % Configuration.NUM_OF_SHARDS))
//            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
}
