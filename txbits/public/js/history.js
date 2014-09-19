$(function(){
    var dw_template = Handlebars.compile($("#deposit-withdraw-history-template").html());
    var trade_template = Handlebars.compile($("#trade-history-template").html());
    var login_history_template = Handlebars.compile($("#login-history-template").html());
    API.deposit_withdraw_history().success(function(data){
        var i;
        for (i = 0; i < data.result.length; i++){
            data.result[i].created = moment(Number(data.result[i].created)).format("YYYY-MM-DD HH:mm:ss");
            data.result[i].typ = data.result[i].typ == 'd' ? "Deposit" : "Withdrawal";
            data.result[i].address = data.result[i].address ? data.result[i].address : "N/A";
        }
        var html = dw_template(data.result);
        $('#deposit-withdraw-history').html(html);
    });
    API.trade_history().success(function(data){
        var i;
        for (i = 0; i < data.result.length; i++){
            data.result[i].value = zerosToSpaces(Number(data.result[i].amount) * Number(data.result[i].price));
            data.result[i].amount = zerosToSpaces(data.result[i].amount);
            data.result[i].price = zerosToSpaces(data.result[i].price);
            data.result[i].created = moment(Number(data.result[i].created)).format("YYYY-MM-DD HH:mm:ss");
            data.result[i].bought = data.result[i].typ == 'bid' ? data.result[i].base : data.result[i].counter;
            data.result[i].bought_amount = data.result[i].typ == 'bid' ? data.result[i].amount : data.result[i].value;
            data.result[i].sold = data.result[i].typ == 'ask' ? data.result[i].base : data.result[i].counter;
            data.result[i].sold_amount = data.result[i].typ == 'ask' ? data.result[i].amount : data.result[i].value;
        }
        var html = trade_template(data.result);
        $('#trade-history').html(html);
    });
    API.login_history().success(function(data){
        var i;
        for (i = 0; i < data.result.length; i++){
            data.result[i].created = moment(Number(data.result[i].created)).format("YYYY-MM-DD HH:mm:ss");
        }
        var html = login_history_template(data.result);
        $('#login-history').html(html);
    });
});
