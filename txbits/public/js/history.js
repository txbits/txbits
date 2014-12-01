$(function(){
    var dw_template = Handlebars.compile($("#deposit-withdraw-history-template").html());
    var trade_template = Handlebars.compile($("#trade-history-template").html());
    var login_history_template = Handlebars.compile($("#login-history-template").html());
    API.deposit_withdraw_history().success(function(data){
        var i;
        for (i = 0; i < data.length; i++){
            data[i].created = moment(Number(data[i].created)).format("YYYY-MM-DD HH:mm:ss");
            data[i].amount = zerosToSpaces(data[i].amount);
            data[i].fee = zerosToSpaces(data[i].fee);
            data[i].address = data[i].address ? data[i].address : "N/A";
            if (data[i].typ == 'd') {
                data[i].typ = "Deposit";
                data[i].klass = "success";
            } else {
                data[i].typ = "Withdrawal";
                data[i].klass = "danger";
            }
        }
        var html = dw_template(data);
        $('#deposit-withdraw-history').html(html);
    });
    API.trade_history().success(function(data){
        var i;
        for (i = 0; i < data.length; i++){
            data[i].value = zerosToSpaces(Number(data[i].amount) * Number(data[i].price));
            data[i].amount = zerosToSpaces(data[i].amount);
            data[i].price = zerosToSpaces(data[i].price);
            data[i].created = moment(Number(data[i].created)).format("YYYY-MM-DD HH:mm:ss");
            data[i].fee = zerosToSpaces(data[i].fee);
            if (data[i].typ == "ask") {
                data[i].fee_currency = data[i].counter;
                data[i].order_type = "Sell";
                data[i].klass = "danger";
            } else {
                data[i].fee_currency = data[i].base;
                data[i].order_type = "Buy";
                data[i].klass = "success";
            }
        }
        var html = trade_template(data);
        $('#trade-history').html(html);
    });
    API.login_history().success(function(data){
        var i;
        for (i = 0; i < data.length; i++){
            data[i].created = moment(Number(data[i].created)).format("YYYY-MM-DD HH:mm:ss");
        }
        var html = login_history_template(data);
        $('#login-history').html(html);
    });
});
