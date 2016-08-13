define(function (require, exports, module) {
    function onCharacterDown(character, func) {
        return function (event) {
            const eventCharacter = String.fromCharCode(event.keyCode);
            if (eventCharacter == character) {
                func.call(this);
            }
        };
    }

    var actionSocketTag = Object.create(HTMLElement.prototype, {
        createdCallback: {
            value: function () {
                this.innerHTML = `
<div>
<div class="key-bind"></div>
</div>`;
                this.classList.add('icon');
            }
        },
        attachedCallback: {
            value: function () {
                const icon = this.getAttribute('icon');
                if (icon) {
                    this.classList.add(icon);
                } else {
                    this.classList.add('icon-empty');
                }

                const active = this.getAttribute('active');
                if (this.getAttribute('active')) {
                    this.classList.add('active');
                }

                const key = this.getAttribute('key');

                function action() {
                    this.dispatchEvent(new CustomEvent('action-triggered', {detail: {key: key}}))
                }

                this.addEventListener('click', action);

                const keyBind = this.getAttribute('keyBind');
                if (keyBind) {
                    this.keyListener = onCharacterDown(keyBind, action).bind(this);
                    this.getElementsByClassName('key-bind')[0].innerText = keyBind;
                    document.addEventListener('keydown', this.keyListener);
                }
            }
        },
        attributeChangedCallback: {
            value: function (attrName, oldVal, newVal) {
                if (attrName == 'active') {
                    if (newVal) {
                        this.classList.add('active');
                    } else {
                        this.classList.remove('active');
                    }
                }
            }
        },
        detachedCallback: {
            value: function () {
                if (this.keyListener) {
                    document.removeEventListener('keydown', this.keyListener);
                }
            }
        }
    });
    return document.registerElement('action-socket', {prototype: actionSocketTag});
});