package dzida.server.app.instance;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dzida.server.app.instance.command.InstanceCommand;
import dzida.server.core.basic.Outcome;
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
        public Outcome<List<GameEvent>> resolveCommand(Id<Character> characterId, BackdoorCommand command) {
            return Outcome.ok(Collections.emptyList());
        }
    };

    public Outcome<List<GameEvent>> resolveCommand(Id<Character> characterId, BackdoorCommand command) {
        return dispatchMessage(characterId, command.type, command.data);
    }

    private Outcome<List<GameEvent>> dispatchMessage(Id<Character> characterId, int type, JsonElement data) {
        switch (type) {
            case Commands.KillCharacter:
                return Outcome.ok(singletonList(new CharacterDied(characterId)));
        }
        return Outcome.ok(emptyList());
    }

    private interface Commands {
        int KillCharacter = 0;
    }

    public static final class BackdoorCommand implements InstanceCommand {
        public Id<Character> characterId;
        int type;
        JsonObject data;

        public BackdoorCommand(int type, JsonObject data) {
            this.type = type;
            this.data = data;
        }
    }
}
