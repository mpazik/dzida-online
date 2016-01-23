package dzida.server.app.map.descriptor;

import dzida.server.core.basic.entity.Key;
import dzida.server.core.basic.unit.Point;
import dzida.server.core.world.model.WorldMap;
import lombok.Value;

import java.util.List;

public class Survival implements Scenario {
    private final String type;
    private final Key<WorldMap> mapName;
    private final List<Spawn> spawns;
    private final int difficultyLevel;

    public Survival(Key<WorldMap> mapName, int difficultyLevel, List<Spawn> spawns) {
        this.mapName = mapName;
        this.spawns = spawns;
        this.difficultyLevel = difficultyLevel;
        this.type = "survival";
    }

    public List<Spawn> getSpawns() {
        return spawns;
    }

    public Key<WorldMap> getWorldMapKey() {
        return mapName;
    }

    public int getDifficultyLevel() {
        return difficultyLevel;
    }

    @Override
    public String getType() {
        return type;
    }

    @Value
    public static class Spawn {
        private final Point position;
    }
}
