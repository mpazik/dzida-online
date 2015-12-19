package dzida.server.core.position;

import dzida.server.core.character.CharacterId;
import dzida.server.core.position.model.Position;

public interface PositionStore {
    Position getCharacterPosition(CharacterId characterId);
}
