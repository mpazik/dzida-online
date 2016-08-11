package dzida.server.app;

import com.google.common.collect.ImmutableMap;
import dzida.server.app.map.descriptor.Scenario;
import dzida.server.core.basic.Publisher;
import dzida.server.core.basic.entity.Id;
import dzida.server.core.basic.entity.Id;
import dzida.server.core.character.CharacterService;
import dzida.server.core.character.model.Character;
import dzida.server.core.event.GameEvent;
import dzida.server.core.player.Player;
import dzida.server.core.position.PositionService;
import dzida.server.core.skill.SkillService;
import dzida.server.core.time.TimeService;
import dzida.server.core.world.map.WorldMapService;
import dzida.server.core.world.object.WorldObjectService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class GameEventDispatcher {
    private final PositionService positionService;
    private final Publisher<GameEvent> eventPublisher = new Publisher<>();
    private final Publisher<GameEvent> eventPublisherBeforeChanges = new Publisher<>();
    private final Map<Id<Character>, Consumer<GameEvent>> listeners = new HashMap<>();
    private final CharacterService characterService;
    private final WorldMapService worldMapService;
    private final SkillService skillService;
    private final WorldObjectService worldObjectService;
    private final Scenario scenario;
    private final TimeService timeService;

    public GameEventDispatcher(
            PositionService positionService,
            CharacterService characterService,
            WorldMapService worldMapService,
            SkillService skillService,
            WorldObjectService worldObjectService,
            Scenario scenario,
            TimeService timeService) {
        this.positionService = positionService;
        this.characterService = characterService;
        this.worldMapService = worldMapService;
        this.skillService = skillService;
        this.worldObjectService = worldObjectService;
        this.scenario = scenario;
        this.timeService = timeService;
    }

    public void registerCharacter(Character character, Consumer<GameEvent> send) {
        eventPublisher.subscribe(send);
        listeners.put(character.getId(), send);
    }

    // I do not think that this should be here.
    public void sendInitialPacket(Id<Character> characterId, Id<Player> playerId, Player playerEntity) {
        listeners.get(characterId).accept(new InitialMessage(characterId, playerId, getState(), scenario, playerEntity.getData(), timeService.getCurrentMillis()));
    }

    private Map<String, Object> getState() {
        return ImmutableMap.of(
                positionService.getKey(), positionService.getState(),
                characterService.getKey(), characterService.getState(),
                worldMapService.getKey(), worldMapService.getState(),
                skillService.getKey(), skillService.getState(),
                worldObjectService.getKey(), worldObjectService.getState()
        );
    }

    public void unregisterCharacter(Id<Character> characterId) {
        eventPublisher.unsubscribe(listeners.get(characterId));
        listeners.remove(characterId);
    }

    public void dispatchEvent(GameEvent gameEvent) {
        eventPublisherBeforeChanges.notify(gameEvent);
        characterService.processEvent(gameEvent);
        positionService.processEvent(gameEvent);
        skillService.processEvent(gameEvent);
        worldObjectService.processEvent(gameEvent);
        eventPublisher.notify(gameEvent);
    }

    public void dispatchEvents(List<GameEvent> gameEvents) {
        gameEvents.forEach(this::dispatchEvent);
    }

    public void sendEvent(Id<Character> characterId, GameEvent gameEvent) {
        listeners.get(characterId).accept(gameEvent);
    }

    public Publisher<GameEvent> getEventPublisherBeforeChanges() {
        return eventPublisherBeforeChanges;
    }

    public Publisher<GameEvent> getEventPublisher() {
        return eventPublisher;
    }

    public static final class InitialMessage implements GameEvent {
        Id<Character> characterId;
        Id<Player> playerId;
        Map<String, Object> state;
        Scenario scenario;
        Player.Data playerData;
        long serverTime;

        public InitialMessage(Id<Character> characterId, Id<Player> playerId, Map<String, Object> state, Scenario scenario, dzida.server.core.player.Player.Data playerData, long serverTime) {
            this.characterId = characterId;
            this.playerId = playerId;
            this.state = state;
            this.scenario = scenario;
            this.playerData = playerData;
            this.serverTime = serverTime;
        }

        @Override
        public int getId() {
            return GameEvent.InitialData;
        }
    }
}
