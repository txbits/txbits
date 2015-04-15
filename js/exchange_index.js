$(function(){
    var template = Handlebars.compile($("#balance-template").html());

    function show_balance(){
        API.balance().success(function(data){
            var balances = data.result;
            for (var i = 0; i < balances.length; i++) {
                balances[i].available = zerosToSpaces(Number(balances[i].amount) - Number(balances[i].hold));
                balances[i].amount = zerosToSpaces(balances[i].amount);
                balances[i].hold = zerosToSpaces(balances[i].hold);
            }
            $('#balance').html(template(balances));
        });
    }
    show_balance();

    var tickers_template = Handlebars.compile($("#big-tickers-template").html());

    API.ticker().success(function(data){
        var tickers = data.result;
        for (var i = 0; i < tickers.length; i++) {
            if (Number(tickers[i].last) > Number(tickers[i].first)) {
                tickers[i].color = "green";
                tickers[i].icon = "chevron-up";
            } else if (Number(tickers[i].last) < Number(tickers[i].first)) {
                tickers[i].color = "red";
                tickers[i].icon = "chevron-down";
            } else {
                tickers[i].color = "gray";
                tickers[i].icon = "minus";
            }
            tickers[i].last = zerosToSpaces(tickers[i].last);
            tickers[i].low = zerosToSpaces(tickers[i].low);
            tickers[i].high = zerosToSpaces(tickers[i].high);
            tickers[i].volume = zerosToSpaces(tickers[i].volume);
        }

        $('#big-tickers').html(tickers_template(tickers));
    });

    /*
    var fakemoney_template = Handlebars.compile($("#fake-money-form-template").html());
    API.currencies().success(function(data){
        $('#fake-money-form').html(fakemoney_template(data.result));

        //TODO: make this work with the keyboard
        $('#fake-money-form').find('.add').click(function(e){
            var $form = $('#fake-money-form');
            var currency = $form.find("#currency").val();
            var amount = Number($form.find("#amount").val());
            API.add_fake_money(currency, amount).success(function(){
                $.pnotify({
                    title: 'Fake money added.',
                    text: 'currency:' + currency + ', amount:' + amount,
                    styling: 'bootstrap',
                    type: 'success',
                    text_escape: true
                });
                show_balance();
            });
            e.preventDefault();
        });

        //TODO: make this work with the keyboard
        $('#fake-money-form').find('.subtract').click(function(e){
            var $form = $('#fake-money-form');
            var currency = $form.find("#currency").val();
            var amount = Number($form.find("#amount").val());
            API.subtract_fake_money(currency, amount).success(function(){
                $.pnotify({
                    title: 'Fake money subtracted.',
                    text: 'currency:' + currency + ', amount:' + amount,
                    styling: 'bootstrap',
                    type: 'success',
                    text_escape: true
                });
            });
            e.preventDefault();
            show_balance();
        });
    });*/
});