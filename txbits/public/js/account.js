$(function(){

    var info_template = Handlebars.compile($("#acct-info-template").html());
    var totp_secret_template = Handlebars.compile($("#totp-secret-template").html());
    var totp_otps_template = Handlebars.compile($("#totp-otps-template").html());
    var turnofftfa_template = Handlebars.compile($("#turn-off-tfa-template").html());

    function reload(){
        API.user().success(function(user){
            var verificationText = "";
            $('#acct-info').html(info_template(user));

            //TODO: internationalize javascript messages
            $('#turnoff-emails').click(function(e){
                API.turnoff_emails().success(function(){
                    $.pnotify({
                        title: Messages("java.api.messages.account.mailinglistsubscription"),
                        text: Messages("java.api.messages.account.mailinglistsubscriptionturnedoff"),
                        styling: 'bootstrap',
                        type: 'success',
                        text_escape: true
                    });
                    reload();
                });
            });

            //TODO: internationalize javascript messages
            $('#turnon-emails').click(function(e){
                API.turnon_emails().success(function(){
                    $.pnotify({
                        title: Messages("java.api.messages.account.mailinglistsubscription"),
                        text: Messages("java.api.messages.account.mailinglistsubscriptionturnedon"),
                        styling: 'bootstrap',
                        type: 'success',
                        text_escape: true
                    });
                    reload();
                });
            });

            $('#turnon-pgp').click(function(e){
                function add(e) {
                    var password = $("#pgp-add-modal").find('.password').val();
                    var tfa_code = $("#pgp-add-modal").find('.code').val();
                    var pgp = $("#pgp-add-modal").find('.pgpkey').val();
                    API.add_pgp(password, tfa_code, pgp).success(function(){
                        $.pnotify({
                            title: Messages("java.api.messages.account.pgpkeyadded"),
                            text: Messages("java.api.messages.account.pgpkeyaddedemailswillnowbeencrypted"),
                            styling: 'bootstrap',
                            type: 'success',
                            text_escape: true
                        });
                        reload();
                        $("#pgp-add-modal").modal('hide');
                    });
                    e.preventDefault();
                }
                if (user.TFAEnabled) {
                    $("#pgp-add-modal .code-group").show();
                } else {
                    $("#pgp-add-modal .code-group").hide();
                }
                $("#pgp-add-modal .modal-content").find('form').off('submit').submit(add);
                $("#pgp-add-modal").modal().find('.btn-primary').off('click').click(add);
            });

            $('#turnoff-pgp').click(function(e){
                function remove(e) {
                    var password = $("#pgp-remove-modal").find('.password').val();
                    var tfa_code = $("#pgp-remove-modal").find('.code').val();
                    //TODO: internationalize javascript messages
                    API.remove_pgp(password, tfa_code).success(function(){
                        $.pnotify({
                            title: Messages("java.api.messages.account.pgpkeyremoved"),
                            text: Messages("java.api.messages.account.pgpkeyremovedemailswillnolongerbeencrypted"),
                            styling: 'bootstrap',
                            type: 'success',
                            text_escape: true
                        });
                        reload();
                        $("#pgp-remove-modal").modal('hide');
                    });
                    e.preventDefault();
                }
                if (user.TFAEnabled) {
                    $("#pgp-remove-modal .code-group").show();
                } else {
                    $("#pgp-remove-modal .code-group").hide();
                }
                $("#pgp-remove-modal .modal-content").find('form').off('submit').submit(remove);
                $("#pgp-remove-modal").modal().find('.btn-primary').off('click').click(remove);
            });

            $('#turnon-tfa').click(function(e){
                API.gen_totp_secret().success(function(data){
                    function show_otps() {
                        $("#tfa-enable-modal .modal-content").html(totp_otps_template(data));
                        $('#tfa-printing-complete').click(show_totp);
                        $("#tfa-enable-modal").modal().find('.btn-primary');
                    }

                    function show_totp() {
                        function enable(e) {
                            var code = $("#tfa-enable-modal").find('.code').val();
                            var password = $("#tfa-enable-modal").find('.password').val();
                            API.turnon_tfa(code, password).success(function(){
                                $.pnotify({
                                    title: Messages("java.api.messages.account.twofactorauthentication"),
                                    text: Messages("java.api.messages.account.twofactorauthenticationturnedon"),
                                    styling: 'bootstrap',
                                    type: 'success',
                                    text_escape: true
                                });
                                reload();
                                $("#tfa-enable-modal").modal('hide');
                            });
                            e.preventDefault();
                        }
                        $("#tfa-enable-modal .modal-content").html(totp_secret_template(data)).find('form').submit(enable);
                        $("#tfa-enable-qr").qrcode({render: "div", size: 200, text: data.otpauth});
                        $('#tfa-printing-incomplete').click(show_otps);
                        $("#tfa-enable-modal").modal().find('.btn-primary').off("click").click(enable);
                    }
                    show_otps();
                });
                e.preventDefault();
            });

            $('#turnoff-tfa').click(function(e) {
                function disable(e) {
                    var code = $("#tfa-disable-modal").find('.code').val();
                    var password = $("#tfa-disable-modal").find('.password').val();
                    API.turnoff_tfa(code, password).success(function(){
                        $.pnotify({
                            title: Messages("java.api.messages.account.twofactorauthentication"),
                            text: Messages("java.api.messages.account.twofactorauthenticationturnedoff"),
                            styling: 'bootstrap',
                            type: 'success',
                            text_escape: true
                        });
                        reload();
                        $("#tfa-disable-modal").modal('hide');
                    });
                    e.preventDefault();
                }
                $("#tfa-disable-modal .modal-body").html(turnofftfa_template()).find('form').submit(disable);
                $("#tfa-disable-modal").modal().find('.btn-primary').off("click").click(disable);
                e.preventDefault();
            });

            $('#add-api-key').off('click').click(function(e){
                API.add_api_key().success(function(){
                    $.pnotify({
                        title: Messages("java.api.messages.account.apikeyadded"),
                        text: Messages("java.api.messages.account.apikeyaddedsuccessfully"),
                        styling: 'bootstrap',
                        type: 'success',
                        text_escape: true
                    });
                    reload();
                });
            });

            var api_keys_template = Handlebars.compile($("#api-keys-template").html());
            API.get_api_keys().success(function(api_keys){
                var i;
                for (i = 0; i < api_keys.length; i++){
                    api_keys[i].api_key_id = i;
                    api_keys[i].created = moment(Number(api_keys[i].created)).format("YYYY-MM-DD HH:mm:ss");
                    api_keys[i].trading = api_keys[i].trading ? "checked" : "";
                    api_keys[i].trade_history = api_keys[i].trade_history ? "checked" : "";
                    api_keys[i].list_balance = api_keys[i].list_balance ? "checked" : "";
                }

                $('#api-keys').html(api_keys_template(api_keys));

                function mk_del(tfa_code_ele, alt_this){
                    function del(e){
                        e.preventDefault();
                        var $this = $(this);
                        var id = $this.attr('api-key-id');
                        var key = api_keys[id].api_key;
                        var tfa_code = tfa_code_ele ? tfa_code_ele.val() : '';
                        API.disable_api_key(tfa_code, key).success(function(){
                            $.pnotify({
                                title: Messages("java.api.messages.account.apikeydisabled"),
                                text: Messages("java.api.messages.account.apikeydisabledsuccessfully"),
                                styling: 'bootstrap',
                                type: 'success',
                                text_escape: true
                            });
                            reload();
                            $('#tfa-api-keys-delete-modal').modal('hide');
                        });
                    }
                    if (alt_this) {
                        return del.bind(alt_this);
                    } else {
                        return del;
                    }
                }

                function mk_update(tfa_code_ele, alt_this){
                    function update(e){
                        e.preventDefault();
                        var $this = $(this);
                        var id = $this.attr('api-key-id');
                        var key = api_keys[id].api_key;
                        var $row = $(this).parent().parent();
                        var comment = $row.find('.api-key-comment').val();
                        var trading = $row.find('.api-key-trading').is(':checked');
                        var trade_history = $row.find('.api-key-trade-history').is(':checked');
                        var list_balance = $row.find('.api-key-list-balance').is(':checked');
                        var tfa_code = tfa_code_ele ? tfa_code_ele.val() : '';
                        API.update_api_key(tfa_code, key, comment, trading, trade_history, list_balance).success(function(){
                            $.pnotify({
                                title: Messages("java.api.messages.account.apikeyupdated"),
                                text: Messages("java.api.messages.account.apikeyupdatedsuccessfully"),
                                styling: 'bootstrap',
                                type: 'success',
                                text_escape: true
                            });
                            reload();
                            $('#tfa-api-keys-save-modal').modal('hide');
                        });
                    }
                    if (alt_this) {
                        return update.bind(alt_this);
                    } else {
                        return update;
                    }
                }

                if (user.TFAEnabled) {
                    $('#api-keys').find('.api-key-delete').click(function(){
                        $('#tfa-api-keys-delete-modal').find('.code').val('');
                        $('#tfa-api-keys-delete-modal').modal();
                        $('#tfa-api-keys-delete-modal').find('.btn-primary').off('click').click(mk_del($('#tfa-api-keys-delete-modal').find('.code'), this));
                        $('#tfa-api-keys-delete-modal').find('form').off('submit').submit(mk_del($('#tfa-api-keys-delete-modal').find('.code'), this));
                    });
                    $('#api-keys').find('.api-key-save').click(function(){
                        $('#tfa-api-keys-save-modal').find('.code').val('');
                        $('#tfa-api-keys-save-modal').modal();
                        $('#tfa-api-keys-save-modal').find('.btn-primary').off('click').click(mk_update($('#tfa-api-keys-save-modal').find('.code'), this));
                        $('#tfa-api-keys-save-modal').find('form').off('submit').submit(mk_update($('#tfa-api-keys-save-modal').find('.code'), this));
                    });
                } else {
                    $('#api-keys').find('.api-key-delete').click(mk_del());
                    $('#api-keys').find('.api-key-save').click(mk_update());
                }

            });
        });
    }
    reload();
});
