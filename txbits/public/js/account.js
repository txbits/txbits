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
                        title: 'Mailing list subscription',
                        text: 'Mailing list subscription turned off.',
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
                        title: 'Mailing list subscription',
                        text: 'Mailing list subscription turned on.',
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
                    //TODO: internationalize javascript messages
                    API.add_pgp(password, tfa_code, pgp).success(function(){
                        $.pnotify({
                            title: 'PGP key added',
                            text: 'PGP key added. Emails will now be encrypted.',
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
                            title: 'PGP key removed',
                            text: 'PGP key removed. Emails will no longer be encrypted.',
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
                            //TODO: internationalize javascript messages
                            API.turnon_tfa(code, password).success(function(){
                                $.pnotify({
                                    title: 'Two factor authentication',
                                    text: 'Two factor authentication turned on.',
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

            //TODO: internationalize javascript messages
            $('#turnoff-tfa').click(function(e) {
                function disable(e) {
                    var code = $("#tfa-disable-modal").find('.code').val();
                    var password = $("#tfa-disable-modal").find('.password').val();
                    API.turnoff_tfa(code, password).success(function(){
                        $.pnotify({
                            title: 'Two factor authentication',
                            text: 'Two factor authentication turned off.',
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
        });
    }
    reload();
});
