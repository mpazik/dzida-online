package dzida.server.core.world.event;

import dzida.server.core.basic.entity.GeneralEntity;
import dzida.server.core.event.GameEvent;
import dzida.server.core.world.object.WorldObject;

public class WorldObjectCreated implements GameEvent {
    public final GeneralEntity<WorldObject> worldObject;

    public WorldObjectCreated(GeneralEntity<WorldObject> worldObject) {
        this.worldObject = worldObject;
    }

    @Override
    public int getId() {
        return GameEvent.WorldObjectCreated;
    }
}
