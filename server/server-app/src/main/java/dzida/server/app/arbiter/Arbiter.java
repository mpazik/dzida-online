package dzida.server.app.arbiter;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import dzida.server.app.Configuration;
import dzida.server.app.Scheduler;
import dzida.server.app.basic.Publisher;
import dzida.server.app.basic.Result;
import dzida.server.app.basic.connection.Connector;
import dzida.server.app.basic.connection.ServerConnection;
import dzida.server.app.basic.connection.VerifyingConnectionServer;
import dzida.server.app.basic.entity.Id;
import dzida.server.app.basic.entity.Key;
import dzida.server.app.dispatcher.ServerDispatcher;
import dzida.server.app.instance.GameDefinitions;
import dzida.server.app.instance.Instance;
import dzida.server.app.instance.InstanceServer;
import dzida.server.app.instance.InstanceStore;
import dzida.server.app.instance.skill.Skill;
import dzida.server.app.instance.world.object.WorldObjectKind;
import dzida.server.app.map.descriptor.Scenario;
import dzida.server.app.protocol.json.JsonProtocol;
import dzida.server.app.store.http.loader.SkillLoader;
import dzida.server.app.store.http.loader.StaticDataLoader;
import dzida.server.app.store.http.loader.WorldObjectKindLoader;
import dzida.server.app.user.EncryptedLoginToken;
import dzida.server.app.user.LoginToken;
import dzida.server.app.user.User;
import dzida.server.app.user.UserTokenVerifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.nurkiewicz.typeof.TypeOf.whenTypeOf;

public class Arbiter implements VerifyingConnectionServer<String, String> {
    public final Publisher<InstanceServer> instanceStartedPublisher;
    public final Publisher<InstanceServer> instanceClosedPublisher;
    private final ServerDispatcher serverDispatcher;
    private final Scheduler scheduler;
    private final JsonProtocol arbiterProtocol;
    private final UserTokenVerifier userTokenVerifier;
    private final ArbiterStore arbiterStore;
    private final InstanceStore instanceStore;
    private final GameDefinitions gameDefinitions;

    private final Map<Id<User>, Key<Instance>> usersInstances;
    private final Map<Key<Instance>, InstanceServer> instances;
    private final Set<Key<Instance>> initialInstances;
    private final Key<Instance> defaultInstance;
    private final Set<Key<Instance>> instancesToShutdown;
    private final Set<Id<User>> connectedUsers;

    public Arbiter(ServerDispatcher serverDispatcher, Scheduler scheduler, ArbiterStore arbiterStore, InstanceStore instanceStore) {
        this.serverDispatcher = serverDispatcher;
        this.scheduler = scheduler;
        this.arbiterStore = arbiterStore;
        this.instanceStore = instanceStore;
        arbiterProtocol = JsonProtocol.create(ArbiterCommand.clientCommandClasses, ArbiterCommand.serverCommandClasses);
        userTokenVerifier = new UserTokenVerifier();

        usersInstances = new HashMap<>();
        instances = new HashMap<>();
        initialInstances = ImmutableList.copyOf(Configuration.getInitialInstances())
                .stream()
                .map((Function<String, Key<Instance>>) Key::new)
                .collect(Collectors.toSet());
        instancesToShutdown = new HashSet<>();
        defaultInstance = new Key<>(Configuration.getInitialInstances()[0]);
        connectedUsers = new HashSet<>();
        instanceStartedPublisher = new Publisher<>();
        instanceClosedPublisher = new Publisher<>();
        gameDefinitions = createGameDefinitions();
    }

    private GameDefinitions createGameDefinitions() {
        StaticDataLoader staticDataLoader = new StaticDataLoader();
        Map<Id<Skill>, Skill> skills = new SkillLoader(staticDataLoader).loadSkills();
        WorldObjectKindLoader worldObjectKindLoader = new WorldObjectKindLoader(staticDataLoader);
        Map<Id<WorldObjectKind>, WorldObjectKind> worldObjectKinds = worldObjectKindLoader.loadWorldObjectKinds()
                .stream()
                .collect(Collectors.toMap(WorldObjectKind::getId, Function.identity()));
        return new GameDefinitions(skills, worldObjectKinds);
    }

    public void start() {
        arbiterStore.systemStarted();
        initialInstances.forEach(instanceKey -> {
            startInstance(instanceKey, new Scenario(new Key<>(instanceKey.getValue())));
        });
    }

    public void stop() {
        List<Key<Instance>> instanceKeysCopy = instances.keySet().stream().collect(Collectors.toList());
        instanceKeysCopy.forEach(this::stopInstance);
        arbiterStore.systemStopped();
    }

    public void startInstance(Key<Instance> instanceKey, Scenario scenario) {
        arbiterStore.instanceStarted(instanceKey);
        String instanceKeyValue = instanceKey.getValue();
        InstanceServer instanceServer = new InstanceServer(scheduler, instanceStore, this, instanceKey, scenario, gameDefinitions);
        instanceServer.start();

        serverDispatcher.addServer(instanceKeyValue, instanceServer);
        instances.put(instanceKey, instanceServer);
        instanceStartedPublisher.notify(instanceServer);
        cleanOldInstances();
    }

    public void stopInstance(Key<Instance> instanceKey) {
//        serverDispatcher.removeServer(instanceKey.getValue());
        instanceClosedPublisher.notify(instances.get(instanceKey));
        instances.get(instanceKey).closeInstance();
        instances.remove(instanceKey);
        arbiterStore.instanceStopped(instanceKey);
    }

    @Override
    public Result onConnection(Connector<String> connector, String userToken) {
        Optional<LoginToken> loginToken = userTokenVerifier.verifyToken(new EncryptedLoginToken(userToken));
        if (!loginToken.isPresent()) {
            return Result.error("Login to is invalid");
        }

        Id<User> userId = loginToken.get().userId;
        if (connectedUsers.contains(userId)) {
            return Result.error("User is already logged in.");
        }
        connectedUsers.add(userId);

        ArbiterConnection arbiterConnection = new ArbiterConnection(userId, connector);
        connector.onOpen(arbiterConnection);
        arbiterConnection.movePlayerToInstance(defaultInstance);
        return Result.ok();
    }

    public boolean isUserOnInstance(Key<Instance> instanceKey, Id<User> userId) {
        return usersInstances.get(userId).equals(instanceKey);
    }

    private void cleanOldInstances() {
        instancesToShutdown.forEach(this::tryToStopInstance);
        Iterables.removeIf(instancesToShutdown, instanceKey -> !instances.containsKey(instanceKey));
    }

    private void tryToStopInstance(Key<Instance> instanceKey) {
        if (instancesToShutdown.contains(instanceKey) && instances.get(instanceKey).isEmpty()) {
            stopInstance(instanceKey);
        }
    }

    private final class ArbiterConnection implements ServerConnection<String> {
        private final Id<User> userId;
        private final Connector<String> connector;

        ArbiterConnection(Id<User> userId, Connector<String> connector) {
            this.userId = userId;
            this.connector = connector;
        }

        @Override
        public void send(String data) {
            Object message = arbiterProtocol.parseMessage(data);
            whenTypeOf(message)
                    .is(ArbiterCommand.Travel.class)
                    .then(command -> {
                        Key<Instance> instanceKey = new Key<>(command.location);
                        if (instances.containsKey(instanceKey)) {
                            movePlayerToInstance(instanceKey);
                        }
                    });
        }

        public void movePlayerToInstance(Key<Instance> newInstanceKey) {
            usersInstances.put(userId, newInstanceKey);
            connector.onMessage(arbiterProtocol.serializeMessage(new ArbiterCommand.JoinToInstance(newInstanceKey)));
            arbiterStore.userJoinedInstance(userId, newInstanceKey);
        }

        @Override
        public void close() {
            Key<Instance> lastInstanceKey = usersInstances.get(userId);
            arbiterStore.playerLeftInstance(userId, lastInstanceKey);

            connectedUsers.remove(userId);
            usersInstances.remove(userId);
        }
    }
}
