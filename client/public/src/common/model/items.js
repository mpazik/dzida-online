if (typeof exports === 'object' && typeof exports.nodeName !== 'string' && typeof define !== 'function') {
    var define = function (factory) {
        factory(require, exports, module);
    };
}
define(function (require, exports, module) {

    const Ids = {
        ARROW: 0,
        STICK: 1
    };

    module.exports = {
        Ids
    };
});