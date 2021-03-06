define(function (require, exports, module) {
    const Publisher = require('../common/basic/publisher');
    const Dispatcher = require('../component/dispatcher');
    const Messages = require('../component/instance/messages');

    const delay = 1000;

    function showMessage(publishMessage, message) {
        publishMessage(message);
        setTimeout(function () {
            publishMessage(null);
        }, delay);
    }

    module.exports = {
        displayMessage(message) {
            Dispatcher.messageStream.publish(Messages.ServerMessage, new Messages.ServerMessage(message));
        },
        messageToShowState: new Publisher.StatePublisher(null, push => {
            Dispatcher.messageStream.subscribe(Messages.ServerMessage, function (event) {
                showMessage(push, event.message);
            });
        }),
        errorToShowState: new Publisher.StatePublisher(null, push => {
            Dispatcher.messageStream.subscribe(Messages.ServerError, function (event) {
                push(event.message);
            });
        })
    };
});