package dzida.server.app.chat;

import com.google.common.base.Strings;
import dzida.server.core.basic.Result;
import dzida.server.core.basic.connection.Connector;
import dzida.server.core.basic.connection.ServerConnection;
import dzida.server.core.basic.connection.VerifyingConnectionServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class Chat implements VerifyingConnectionServer<String, String> {
    private final Map<String, List<String>> channelConnections;
    private final Map<String, Consumer<String>> messageTargets;

    public Chat() {
        channelConnections = new HashMap<>();
        messageTargets = new HashMap<>();
    }

    private static boolean isChannelName(String name) {
        return !Strings.isNullOrEmpty(name) && name.charAt(0) == '#';
    }

    @Override
    public Result verifyConnection(String nick) {
        if (Strings.isNullOrEmpty(nick)) {
            return Result.error("Nick has to be defined");
        }
        if (nick.length() < 3) {
            return Result.error("Length of the userNick should be longer than 3 characters");
        }
        if (isChannelName(nick)) {
            return Result.error("Nick can not starts with '#'");
        }
        return Result.ok();
    }

    @Override
    public void onConnection(Connector<String> connector, String userNick) {
        messageTargets.put(userNick, connector::onMessage);
        ChatConnection chatConnection = new ChatConnection(userNick);
        connector.onOpen(chatConnection);
    }

    private void createChannel(String channelName) {
        channelConnections.put(channelName, new ArrayList<>());
        messageTargets.put(channelName, message -> channelConnections.get(channelName)
                .forEach(nick -> messageTargets.get(nick).accept("CHANNEL " + channelName + " " + message))
        );
    }

    private void closeChannel(String channelName) {
        channelConnections.remove(channelName);
        messageTargets.remove(channelName);
    }

    private final class ChatConnection implements ServerConnection<String> {
        private final String userNick;

        private ChatConnection(String userNick) {
            this.userNick = userNick;
        }

        @Override
        public void send(String data) {
            String[] dataSplit = data.split(" ", 2);
            String command = dataSplit[0];
            String args = dataSplit[1];
            switch (command) {
                case "JOIN": {
                    joinToChannel(args);
                    break;
                }
                case "MSG": {
                    String[] split = args.split(" ", 2);
                    String target = split[0];
                    String message = split[1];
                    sendMessage(target, message);
                    break;
                }
                case "QUIT": {
                    quiteFromChannel(args);
                    break;
                }
                case "LIST": {
                    listChannelPlayers(args);
                    break;
                }
            }
        }

        private void listChannelPlayers(String channelName) {
            if (!channelNameValidation(channelName)) {
                return;
            }
            List<String> channelUsers = channelConnections.get(channelName);
            messageTargets.get(userNick).accept("CHANNEL " + channelName + " LIST " + channelUsers.size() + " " + String.join(",", channelUsers));
        }

        private void quiteFromChannel(String channelName) {
            if (!channelNameValidation(channelName)) {
                return;
            }
            channelConnections.get(channelName).remove(userNick);
            messageTargets.get(channelName).accept("QUITED " + userNick);
            if (channelConnections.get(channelName).size() == 0) {
                closeChannel(channelName);
            }
        }

        private boolean channelNameValidation(String channelName) {
            if (!isChannelName(channelName)) {
                response("ERROR channel name: " + channelName + ", is incorrect");
                return false;
            }
            if (!channelConnections.containsKey(channelName)) {
                response("ERROR channel: " + channelName + ", does not exist.");
                return false;
            }
            return true;
        }

        private void response(String message) {
            messageTargets.get(userNick).accept(message);
        }

        private void sendMessage(String target, String message) {
            if (Strings.isNullOrEmpty(target)) {
                response("ERROR message target has to be defined");
                return;
            }
            if (Strings.isNullOrEmpty(message)) {
                response("ERROR can not send empty message");
                return;
            }
            if (!messageTargets.containsKey(target)) {
                response("ERROR target:" + target + " is not defined");
                return;
            }
            messageTargets.get(target).accept("MSG " + userNick + " " + message);
        }

        public void joinToChannel(String channelName) {
            if (!isChannelName(channelName)) {
                response("ERROR channel name: " + channelName + ", is in correct");
                return;
            }
            if (!channelConnections.containsKey(channelName)) {
                createChannel(channelName);
            }
            channelConnections.get(channelName).add(userNick);
            messageTargets.get(channelName).accept("JOINED " + userNick);
        }

        @Override
        public void close() {
            messageTargets.remove(userNick);
            channelConnections.forEach((channelName, users) -> {
                if (!users.contains(userNick)) {
                    return;
                }
                users.remove(userNick);
                quiteFromChannel(channelName);
            });
        }
    }

}
