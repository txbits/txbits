$(function(){

    var templates = {};
    Handlebars.registerPartial("crypto-withdraw-form", $("#crypto-withdraw-form-template").html());
    Handlebars.registerPartial("pending-w", $("#pending-w-template").html());
    Handlebars.registerPartial("pending-d", $("#pending-d-template").html());
    Handlebars.registerPartial("pending-w-crypto", $("#pending-w-crypto-template").html());
    Handlebars.registerPartial("pending-d-crypto", $("#pending-d-crypto-template").html());
    Handlebars.registerPartial("pending-w-fiat", $("#pending-w-fiat-template").html());
    Handlebars.registerPartial("crypto-deposit", $("#crypto-deposit-template").html());

    API.user().success(function(user){
        if (user.TFAEnabled) {
            $('#withdraw-confirm-tfa-form').show();
        } else {
            $('#withdraw-confirm-tfa-form').hide();
        }
    });

    API.currencies().success(function(data){
        var currencies = data;

        // compile all templates we can find
        for(var i = 0; i < currencies.length; i++){
            var currency = currencies[i];
            try{
                templates[currency] = Handlebars.compile($("#dw-"+currency.toLowerCase()+"-template").html());
            } catch(e) {}
        }

        API.dw_limits().success(function(dw_limits){
            API.required_confirms().success(function(required_confirms){
                API.dw_fees().success(function(dw_fees){
                    //TODO: this needs work to handle multiple methods
                    var fee = {};
                    for (var i = 0; i < dw_fees.length; i++) {
                        fee[dw_fees[i].currency] = dw_fees[i];
                    }
                    API.pending_deposits().success(function(data){
                        var pending_d = data;
                        for (var currency in pending_d) {
                            for (var i in pending_d[currency]) {
                                pending_d[currency][i].created = moment(Number(pending_d[currency][i].created)).format("YYYY-MM-DD HH:mm:ss");
                                pending_d[currency][i].amount = zerosToSpaces(pending_d[currency][i].amount);
                                pending_d[currency][i].fee = zerosToSpaces(pending_d[currency][i].fee);
                            }
                        }
                        API.pending_withdrawals().success(function(data){
                            var pending_w = data;
                            for (var currency in pending_w) {
                                for (var i in pending_w[currency]) {
                                    pending_w[currency][i].created = moment(Number(pending_w[currency][i].created)).format("YYYY-MM-DD HH:mm:ss");
                                    pending_w[currency][i].amount = zerosToSpaces(pending_w[currency][i].amount);
                                    pending_w[currency][i].fee = zerosToSpaces(pending_w[currency][i].fee);
                                }
                            }
                            API.deposit_crypto_all().success(function(addresses) {
                                API.balance().success(function(balances){
                                    var all = "";
                                    var i, currency;
                                    var balance_map = {};
                                    for (i = 0; i < balances.length; i++) {
                                        balances[i].available = zerosTrim(Number(balances[i].amount) - Number(balances[i].hold));
                                        balances[i].amount = zerosTrim(balances[i].amount);
                                        balances[i].hold = zerosTrim(balances[i].hold);
                                        balance_map[balances[i].currency] = balances[i];
                                    }
                                    var main_addresses = {};
                                    for(i = 0; i < currencies.length; i++) {
                                        currency = currencies[i];
                                        var main_address = addresses[currency] ? addresses[currency][0] : "Not generated yet...";
                                        main_addresses[currency] = main_address;
                                        var other_addresses = addresses[currency] ? addresses[currency].splice(1) : [];
                                        try{
                                            all += templates[currency]({
                                                currency: currency,
                                                balance: balance_map[currency],
                                                main_address: main_address,
                                                addresses: other_addresses,
                                                pending_w: pending_w[currency],
                                                pending_d: pending_d[currency],
                                                deposit_linear: zerosTrim(fee[currency].depositLinear),
                                                deposit_constant: zerosTrim(fee[currency].depositConstant),
                                                withdraw_linear: zerosTrim(fee[currency].withdrawLinear),
                                                withdraw_constant: zerosTrim(fee[currency].withdrawConstant),
                                                limit_min: zerosTrim(dw_limits[currency].limit_min),
                                                limit_max: zerosTrim(dw_limits[currency].limit_max),
                                                required_confirms: required_confirms[currency]
                                            })
                                        } catch(e) {}
                                    }
                                    $('#depositwithdraw').html(all).find("form");
                                    for(i = 0; i < currencies.length; i++) {
                                        currency = currencies[i];
                                        if (main_addresses[currency] != "Not generated yet...") {
                                            $("#deposit-"+currency+"-qr").qrcode({render: "div", size: 200, text: main_addresses[currency]});
                                        }
                                        var $form = $('#withdraw-'+currency);
                                        // we need this to keep a reference to the form and currency
                                        (function($form, currency){
                                            function update_withdrawal($form, currency){
                                                var withdraw_amount = Number($form.find(".amount").val());
                                                withdraw_amount = withdraw_amount > 0 ? withdraw_amount : 0;
                                                var withdraw_fee = withdraw_amount * Number(fee[currency].withdrawLinear) + Number(fee[currency].withdrawConstant);
                                                $form.find(".fee").val(zerosTrim(withdraw_fee > 0 ? withdraw_fee : 0));
                                                var withdraw_value = withdraw_amount - withdraw_fee;
                                                $form.find(".value").val(zerosTrim(withdraw_value > 0 ? withdraw_value : 0));
                                            }
                                            update_withdrawal($form, currency);
                                            function update_fun() {
                                                update_withdrawal($form, currency)
                                            }
                                            $form.find('.amount').keyup(update_fun).change(update_fun);

                                            $form.submit(function(e){
                                                e.preventDefault();
                                                var amount = $form.find(".amount").val();
                                                var address = $form.find(".address").val();
                                                if (address == '') {
                                                    //TODO: translate!
                                                    $.pnotify({
                                                        title: 'Address required.',
                                                        text: "Address required.",
                                                        styling: 'bootstrap',
                                                        type: 'error',
                                                        text_escape: true
                                                    });
                                                    return;
                                                }

                                                var withdraw_amount = Number($form.find(".amount").val());
                                                withdraw_amount = withdraw_amount > 0 ? withdraw_amount : 0;
                                                var withdraw_fee = withdraw_amount * Number(fee[currency].withdrawLinear) + Number(fee[currency].withdrawConstant);
                                                var withdraw_value = withdraw_amount - withdraw_fee;

                                                $('#withdraw-confirm-amount').text(amount);
                                                $('#withdraw-confirm-address').text(address);
                                                $('#withdraw-confirm-currency1').text(currency);
                                                $('#withdraw-confirm-currency2').text(currency);
                                                $('#withdraw-confirm-currency3').text(currency);
                                                $('#withdraw-confirm-fee').text((withdraw_fee > 0 ? withdraw_fee : 0).toFixed(8));
                                                $('#withdraw-confirm-value').text((withdraw_value > 0 ? withdraw_value : 0).toFixed(8));
                                                $('#withdraw-confirm-cancel').unbind('click').click(function(e){
                                                    e.preventDefault();
                                                    $('#withdraw-confirm-modal').modal('hide');
                                                });
                                                $('#withdraw-confirm-submit').unbind('click').click(do_withdraw);
                                                $('#withdraw-confirm-tfa-form').unbind('submit').submit(do_withdraw);
                                                function do_withdraw(e) {
                                                    e.preventDefault();
                                                    API.withdraw(currency, amount, address, $('#withdraw-confirm-tfa').val()).success(function(){
                                                        //TODO: translate!
                                                        $.pnotify({
                                                            title: 'Withdrawal requested.',
                                                            text: "Withdrawal queued. Wait a few minutes.",
                                                            styling: 'bootstrap',
                                                            type: 'success',
                                                            text_escape: true
                                                        });
                                                        $('#withdraw-confirm-modal').modal('hide');
                                                        $('#withdraw-confirm-submit').removeClass('disabled');
                                                        $('#withdraw-confirm-tfa-form').unbind('submit').submit(do_withdraw);
                                                    }).error(function(){
                                                        $('#withdraw-confirm-submit').removeClass('disabled');
                                                        $('#withdraw-confirm-tfa-form').unbind('submit').submit(do_withdraw);
                                                    });
                                                    $('#withdraw-confirm-submit').addClass('disabled');
                                                    $('#withdraw-confirm-tfa-form').unbind('submit').submit(function(e){e.preventDefault();});
                                                }
                                                $('#withdraw-confirm-modal').modal('show');
                                            });
                                        })($form, currency);
                                    }
                                });
                            });
                        });
                    });
                });
            });
        });
    });
});