package dzida.server.app;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dzida.server.app.command.InstanceCommand;
import dzida.server.core.basic.entity.Id;
import dzida.server.core.character.event.CharacterDied;
import dzida.server.core.character.model.Character;
import dzida.server.core.event.GameEvent;

import java.util.Collections;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class BackdoorCommandResolver {
    public static BackdoorCommandResolver NoOpResolver = new BackdoorCommandResolver() {
        @Override
        public List<GameEvent> resolveCommand(Id<Character> characterId, BackdoorCommand command) {
            return Collections.emptyList();
        }
    };

    private interface Commands {
        int KillCharacter = 0;
    }

    public List<GameEvent> resolveCommand(Id<Character> characterId, BackdoorCommand command) {
        return dispatchMessage(characterId, command.type, command.data);
    }

    private List<GameEvent> dispatchMessage(Id<Character> characterId, int type, JsonElement data) {
        switch (type) {
            case Commands.KillCharacter:
                return singletonList(new CharacterDied(characterId));
        }
        return emptyList();
    }

    public static final class BackdoorCommand implements InstanceCommand {
        int type;
        JsonObject data;
        public Id<Character> characterId;

        public BackdoorCommand(int type, JsonObject data) {
            this.type = type;
            this.data = data;
        }
    }
}
