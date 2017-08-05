var TrollBox = {
    interfaceURL: "/trollbox",
    pollingID: 0,
    visibilityConfig: "isTrollboxVisible",
    
    initialize: function () {
        $("#trollbox-activate").click(function (event) {
            if(TrollBox.visible()) {
                $('#trollbox-wrapper').slideUp();
                $('#trollbox-activate').removeClass('trollbox-active');
                TrollBox.stopPolling();
            } else {
                $("#trollbox-wrapper").slideDown();
                $('#trollbox-activate').addClass('trollbox-active');
                TrollBox.startPolling();
            }
            TrollBox.toggleVisibility();            
        });

        $("#trollbox-input").keydown(function (event) {
            if(event.keyCode == 13) {
                event.preventDefault();
                TrollBox.postMessage(event.currentTarget.value);
            }
        });

        if(TrollBox.visible()) {
            $("#trollbox-wrapper").slideDown();
            $('#trollbox-activate').addClass('trollbox-active');
            TrollBox.startPolling();
        }

        TrollBox.fetchMessages();
    },

    visible: function () {
        var visibility = JSON.parse(window.localStorage.getItem(TrollBox.visibilityConfig));

        if (visibility === null) {
            window.localStorage.setItem(TrollBox.visibilityConfig, false);
            visibility = false;
        }

        return visibility;
    },

    toggleVisibility: function () {
        var visible = TrollBox.visible();
        window.localStorage.setItem(TrollBox.visibilityConfig, JSON.stringify(!visible));
    },

    startPolling: function () {
        TrollBox.pollingID = window.setInterval(function () {
            TrollBox.fetchMessages();
        }, 60 * 60 * 1);
    },

    stopPolling: function () {
        window.clearInterval(TrollBox.pollingID);
    },

    clearUpvoteCache: function () {
        window.localStorage.setItem("userUpvotedItems", JSON.stringify([]));
    },
    
    fetchMessages: function () {
        $.get(TrollBox.interfaceURL, {}, function (data) {
            var messageData = "";

            if(data.length == 0) {
                TrollBox.clearUpvoteCache();
            }
            
            for(var i = 0; i < data.length; i++) {
                messageData += TrollBox.formatMessage(data[i]);
            }
            
            TrollBox.setTrollBox(messageData);

            $('.trollbox-upvote-button').click(TrollBox.upvote);
        });
    },

    getTrollbox: function() {
        return document.getElementById("trollbox-chat-window");
    },

    setTrollBox: function (content) {
        this.getTrollbox().innerHTML = content;
    },

    appendTrollbox: function(message) {
        this.getTrollbox().innerHTML += message;
    },
    
    getUserInput: function() {
        return $("#trollbox-input");
    },

    clearUserInput: function () {
        this.getUserInput().val("");
    },

    formatMessage: function (message) {
        message.upvoted = "";

        if(TrollBox.isMessageUpvoted(message.messageID)) {
            message.upvoted = "upvoted";
        }
        message.colorBin = TrollBox.getUserColorBin(message.user);
        
        var currentMessageTemplate = Handlebars.compile($('#trollbox-message-template').html());

        return currentMessageTemplate(message);
    },

    getUserColorBin: function(username) {
        var red   = Math.abs(128 + (username.length * 100));
        var green = Math.abs(256 - (username.length * 100));
        var blue  = Math.abs(512 - (username.length * 100));
        return 'rgb(' +
            (red   % 256) + ', ' +
            (green % 256) + ', ' +
            (blue  % 256) + ')';
    },
    
    postMessage: function (message) {
        $.ajax(this.interfaceURL, {
            type: 'POST',
            data: JSON.stringify({ 'body': this.getUserInput().val() }),
            dataType: 'json',
            contentType: 'application/json'
        }).success(function (data) {
            TrollBox.fetchMessages();
            TrollBox.clearUserInput();
        });
    },

    upvote: function (event) {
        var messageID = parseInt(event.currentTarget.dataset.message_id);
        $.ajax(TrollBox.interfaceURL + '_upvote', {
            type: 'POST',
            data: JSON.stringify({ 'message_id': messageID }),
            dataType: 'json',
            contentType: 'application/json'
        }).success(function (data) {
            var upvotedItems = JSON.parse(window.localStorage.getItem("userUpvotedItems"));

            if(upvotedItems == null)
                upvotedItems = [];
            
            var index = upvotedItems.indexOf(messageID);
            if(index === -1) {
                index = upvotedItems.push(messageID) - 1;
            } else {
                upvotedItems.splice(index, 1);
                index = -1;
            }

            window.localStorage.setItem("userUpvotedItems", JSON.stringify(upvotedItems));
            TrollBox.fetchMessages();
        }).error(function (data) {
            console.log(data);
        });
    },

    isMessageUpvoted: function(messageID) {
        var upvotedItems = JSON.parse(window.localStorage.getItem("userUpvotedItems"));

        if(upvotedItems === null)
            upvotedItems = [];
        
        return (upvotedItems.indexOf(messageID) !== -1);
    }
};

/* Only initialize the trollbox if it exists */
if($('#trollbox-activate')) {
    TrollBox.initialize();
}

