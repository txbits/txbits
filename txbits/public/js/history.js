$(function(){
    var dw_template = Handlebars.compile($("#deposit-withdraw-history-template").html());
    var trade_template = Handlebars.compile($("#trade-history-template").html());
    var login_history_template = Handlebars.compile($("#login-history-template").html());

    Handlebars.registerPartial("login-history-row", $("#login-history-row-template").html());
    var login_history_row_template = Handlebars.compile($("#login-history-row-template").html());
    Handlebars.registerPartial("trade-history-row", $("#trade-history-row-template").html());
    var trade_history_row_template = Handlebars.compile($("#trade-history-row-template").html());
    Handlebars.registerPartial("dw-history-row", $("#deposit-withdraw-history-row-template").html());
    var dw_history_row_template = Handlebars.compile($("#deposit-withdraw-history-row-template").html());

    Pager.make_easy_pager(
        API.login_history,
        $('#login-history'),
        '#login-history>.tablescroller',
        '#login-history>.tablescroller>.table>tbody',
        '#login-history>.tablescroller>.spinner',
        login_history_template,
        login_history_row_template,
        function(data) {
            var i;
            for (i = 0; i < data.length; i++){
                data[i].created = moment(Number(data[i].created)).format("YYYY-MM-DD HH:mm:ss");
            }
            return data;
        }
    );

    Pager.make_easy_pager(
        API.trade_history,
        $('#trade-history'),
        '#trade-history>.tablescroller',
        '#trade-history>.tablescroller>.table>tbody',
        '#trade-history>.tablescroller>.spinner',
        trade_template,
        trade_history_row_template, function(data) {
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
            return data;
        }
    );

    Pager.make_easy_pager(
        API.deposit_withdraw_history,
        $('#deposit-withdraw-history'),
        '#deposit-withdraw-history>.tablescroller',
        '#deposit-withdraw-history>.tablescroller>.table>tbody',
        '#deposit-withdraw-history>.tablescroller>.spinner',
        dw_template,
        dw_history_row_template, function (data) {
            var i;
            for (i = 0; i < data.length; i++){
                data[i].created = moment(Number(data[i].created)).format("YYYY-MM-DD HH:mm:ss");
                data[i].amount = zerosToSpaces(data[i].amount);
                data[i].fee = zerosToSpaces(data[i].fee);
                data[i].note = data[i].rejected ? "Cancelled" : "";
                if (data[i].typ == 'd') {
                    data[i].typ = "Deposit";
                    data[i].klass = "success";
                } else {
                    data[i].typ = "Withdrawal";
                    data[i].klass = "danger";
                }
            }
            return data;
        }
    );
});
