define(function (require, exports, module) {
    const Publisher = require('../../common/basic/publisher');
    const Dispatcher = require('./../dispatcher');
    const NetworkDispatcher = require('./../network-dispatcher');
    const Commands = require('./commands');
    const Messages = require('./messages');
    const JsonProtocol = require('../../common/basic/json-protocol');

    const instanceProtocol = JsonProtocol.Builder()
        .registerParsingMessageType(5, Messages.CharacterSpawned)
        .registerParsingMessageType(6, Messages.CharacterDied)
        .registerParsingMessageType(7, Messages.CharacterMoved)
        .registerParsingMessageType(8, Messages.SkillUsedOnCharacter)
        .registerParsingMessageType(9, Messages.CharacterGotDamage)
        .registerParsingMessageType(11, Messages.InitialData)
        .registerParsingMessageType(12, Messages.ServerMessage)
        .registerParsingMessageType(19, Messages.ScenarioEnd)
        .registerParsingMessageType(21, Messages.SkillUsedOnWorldMap)
        .registerParsingMessageType(22, Messages.WorldObjectCreated)
        .registerParsingMessageType(23, Messages.SkillUsedOnWorldObject)
        .registerParsingMessageType(24, Messages.WorldObjectRemoved)
        .registerSerializationMessageType(2, Commands.Move)
        .registerSerializationMessageType(3, Commands.UseSkillOnCharacter)
        .registerSerializationMessageType(4, Commands.UseSkillOnWorldMap)
        .registerSerializationMessageType(8, Commands.Backdoor)
        .registerSerializationMessageType(11, Commands.UseSkillOnWorldObject)
        .build();

    const State = {
        CREATED: 0,
        CONNECTING: 1,
        CONNECTED: 2,
        ERROR: 3,
        DISCONNECTING: 4,
        DISCONNECTED: 5
    };
    const stateToStr = ['CREATED', 'CONNECTING', 'CONNECTED', 'ERROR', 'DISCONNECTING', 'DISCONNECTED'];

    function Network() {
        var _this = this;
        this.publishState = null;
        this.state = new Publisher.StatePublisher(State.CREATED, function (f) {
            return _this.publishState = f;
        });
    }

    Network.State = State;
    Network.prototype.connect = function (instanceKey, userToken) {
        var _this = this;
        var socket = NetworkDispatcher.newSocket(instanceKey, userToken);
        this.updateState(State.CONNECTING);
        socket.onMessage = function (data) {
            const message = instanceProtocol.parse(data);
            Dispatcher.messageStream.publish(message.constructor, message);
        };
        socket.onClose = function () {
            _this.updateState(State.DISCONNECTED);
        };
        socket.onOpen = function () {
            _this.updateState(State.CONNECTED);
        };
        socket.onError = function (error) {
            if (_this.state.value === State.CONNECTING) {
                console.error(error);
            }
        };
        this.socket = socket;
    };
    Network.prototype.sendCommand = function (command) {
        this.socket.send(instanceProtocol.serialize(command));
    };
    Network.prototype.disconnect = function () {
        this.updateState(State.DISCONNECTING);
        this.socket.close();
    };

    Network.prototype.updateState = function (state) {
        console.log("Network: " + stateToStr[state]);
        this.publishState(state);
    };
    module.exports = Network;
});