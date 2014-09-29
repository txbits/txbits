//TODO: use a real framework
var exchangeModel = {};

$(function(){
    var pp_template = Handlebars.compile($("#pair-picker-template").html());
    var bid_template = Handlebars.compile($("#bid-template").html());
    var ask_template = Handlebars.compile($("#ask-template").html());
    API.pairs().success(function(data){
        exchangeModel.pairs = data.result;

        $('#pair-picker').html(pp_template(data.result));
        $('#pair-picker a').click(function(e){
            var $this = $(this);
            //TODO: mark as active only after the page is done loading; maybe show some kind of spinner
            pick_pair($this.attr('exchange-base'), $this.attr('exchange-counter'));
            $this.parent().siblings().removeClass('active');
            $this.parent().addClass('active');
            e.preventDefault();
        });

        pick_pair(data.result[0].base, data.result[0].counter);
    });

    function pick_pair(base, counter) {
        if(base && counter) {
            exchangeModel.base = base;
            exchangeModel.counter = counter;
        } else {
            base = exchangeModel.base;
            counter = exchangeModel.counter;
        }

        var pt_template = Handlebars.compile($("#pending-trades-template").html());
        API.pending_trades().success(function(data){
            var i;
            var trades = data.result;
            for (i = 0; i < trades.length; i++){
                trades[i].value = zerosToSpaces((Number(trades[i].amount) * Number(trades[i].price)));
                trades[i].amount = zerosToSpaces(trades[i].amount);
                trades[i].price = zerosToSpaces(trades[i].price);
                trades[i].created = moment(Number(trades[i].created)).format("YYYY-MM-DD HH:mm:ss");
                trades[i].bought = trades[i].typ == 'bid' ? trades[i].base : trades[i].counter;
                trades[i].bought_amount = trades[i].typ == 'bid' ? trades[i].amount : trades[i].value;
                trades[i].sold = trades[i].typ == 'ask' ? trades[i].base : trades[i].counter;
                trades[i].sold_amount = trades[i].typ == 'ask' ? trades[i].amount : trades[i].value;
            }
            $('#pending-trades').html(pt_template(trades)).find('a').click(function(){
                var $this = $(this);
                var id = $this.attr('exchange-trade-id');
                API.cancel(id).success(function(){
                    $.pnotify({
                        title: 'Trade cancelled.',
                        text: 'Trade cancelled successfully.',
                        styling: 'bootstrap',
                        type: 'success',
                        text_escape: true
                    });
                    pick_pair();
                    refresh_ticker();
                });
            });

        });

        //XXX: Not the best way to do things... these calls should either be done in parallel or as one call

        var ob_template = Handlebars.compile($("#open-bids-template").html());
        var oa_template = Handlebars.compile($("#open-asks-template").html());
        API.balance().success(function(data){
            var balances = data.result;

            var balance_base = "0.00000000";
            var balance_counter = "0.00000000";

            for (var i = 0; i < balances.length; i++){
                var bal = balances[i];
                if (bal.currency == base) {
                    balance_base = zerosTrim((bal.amount - bal.hold));
                }
                if (bal.currency == counter) {
                    balance_counter = zerosTrim((bal.amount - bal.hold));
                }
            }

            API.trade_fees().success().success(function(data){
                var fee = data.result;

                API.open_trades(base, counter).success(function(data){
                    var i;
                    var bids = {};
                    bids.orders = data.result.bids;
                    bids.base = base;
                    bids.counter = counter;
                    for (i = 0; i < bids.orders.length; i++){
                        bids.orders[i].value = zerosToSpaces((Number(bids.orders[i].amount) * Number(bids.orders[i].price)));
                        bids.orders[i].amount = zerosToSpaces(bids.orders[i].amount);
                        bids.orders[i].price = zerosToSpaces(bids.orders[i].price);
                    }
                    var asks = {};
                    asks.orders = data.result.asks;
                    asks.base = base;
                    asks.counter = counter;
                    for (i = 0; i < asks.orders.length; i++){
                        asks.orders[i].value = zerosToSpaces((Number(asks.orders[i].amount) * Number(asks.orders[i].price)));
                        asks.orders[i].amount = zerosToSpaces(asks.orders[i].amount);
                        asks.orders[i].price = zerosToSpaces(asks.orders[i].price);
                    }

                    $('#open-bids').html(ob_template(bids));
                    $('#open-asks').html(oa_template(asks));


                    var bid_price = asks.orders.length > 0 ? asks.orders[0].price.trim() : "";
                    var ask_price = bids.orders.length > 0 ? bids.orders[0].price.trim() : "";

                    $('#bid').html(bid_template({balance: balance_counter, price: bid_price, base: base, counter: counter})).off("submit").submit(function(e){
                        $('#bid-submit').addClass('disabled');
                        var $form = $('#bid');
                        var amount = Number($form.find(".amount").val());
                        var price = Number($form.find(".price").val());
                        API.bid(base, counter, amount, price).success(function(){
                            $.pnotify({
                                title: 'Buy order placed.',
                                text: 'Market: ' + base + "/" + counter + ', Amount: ' + amount + ', Price: ' + price,
                                styling: 'bootstrap',
                                type: 'success',
                                text_escape: true
                            });
                            pick_pair();
                            refresh_ticker();
                        }).error(function(){
                            $('#bid-submit').removeClass('disabled');
                        });
                        e.preventDefault();
                    });

                    $('#ask').html(ask_template({balance: balance_base, price: ask_price, base: base, counter: counter})).off("submit").submit(function(e){
                        $('#ask-submit').addClass('disabled');
                        var $form = $('#ask');
                        var amount = Number($form.find(".amount").val());
                        var price = Number($form.find(".price").val());
                        API.ask(base, counter, amount, price).success(function(){
                            $.pnotify({
                                title: 'Sell order placed.',
                                text: 'Market: ' + base + "/" + counter + ', Amount: ' + amount + ', Price: ' + price,
                                styling: 'bootstrap',
                                type: 'success',
                                text_escape: true
                            });
                            pick_pair();
                            refresh_ticker();
                            $('#bid-submit').removeClass('disabled');
                        }).error(function(){
                            $('#ask-submit').removeClass('disabled');
                        });
                        e.preventDefault();
                    });


                    function update_bid(){
                        $form = $('#bid');
                        var order_amount = $form.find(".amount").val();
                        var order_price = $form.find(".price").val();
                        var order_value = order_amount * order_price;
                        $form.find(".counter").val(zerosTrim((order_value > 0 ? order_value : 0)));
                        var order_fee = order_amount * fee;
                        $form.find(".fee").val(zerosTrim((order_fee > 0 ? order_fee : 0)));
                    }
                    update_bid();
                    $('#bid').find('.amount,.price').keyup(update_bid).change(update_bid);

                    function update_ask(){
                        $form = $('#ask');
                        var order_amount = $form.find(".amount").val();
                        var order_price = $form.find(".price").val();
                        var order_value = order_amount * order_price;
                        $form.find(".counter").val(zerosTrim((order_value > 0 ? order_value : 0)));
                        var order_fee = order_value * fee;
                        $form.find(".fee").val(zerosTrim((order_fee > 0 ? order_fee : 0)));
                    }
                    update_ask();
                    $('#ask').find('.amount,.price').keyup(update_ask).change(update_ask);
                });
            });
        });
        var rt_template = Handlebars.compile($("#recent-trades-template").html());
        API.recent_trades(base, counter).success(function(data){
            var i;
            var trades = {};
            trades.orders = data.result;
            trades.base = base;
            trades.counter = counter;
            for (i = 0; i < trades.orders.length; i++){
                trades.orders[i].value = zerosToSpaces((Number(trades.orders[i].amount) * Number(trades.orders[i].price)));
                trades.orders[i].amount = zerosToSpaces(trades.orders[i].amount);
                trades.orders[i].price = zerosToSpaces(trades.orders[i].price);
                trades.orders[i].created = moment(Number(trades.orders[i].created)).format("YYYY-MM-DD HH:mm:ss");
                trades.orders[i].klass = trades.orders[i].typ == "ask" ? "danger" : "success";
            }
            $('#recent-trades').html(rt_template(trades));

        });
        API.chart(base, counter).success(function(data){
            $(document.getElementById("graph")).height("300px");

            var d2 = [];
            var d3 = [];
            var max = 0;
            if (data.result.length) {
                var min = data.result[0][2];
                var last = data.result[data.result.length-1][1];
            }
            for (i=0; i < data.result.length; i++){
                var d = data.result[i];
                max = Math.max(d[1], max);
                min = Math.min(d[2], min);
                d2.push([d[0], d[1], d[2], d[3], d[4]]);
                d3.push([d[0], d[5]]);
            }

            function ticksFn(tick){
                var num = Number(tick);
                if(num > 1000000000){
                    return num/1000000000+'G';
                }
                if(num > 1000000){
                    return num/1000000+'M';
                }
                if(num > 1000){
                    return num/1000+'k';
                }
                return num;
            }

            function trackFormatter1(data){
                var point = data.series.data[data.index];
                return "open " + point[1] + " high " + point[2] + " low " + point[3] + " close " + point[4];
            }
            function trackFormatter2(data){
                return data.y + " " + base;
            }

            // Graph
            graph = Flotr.draw(document.getElementById("graph"), [
                {
                    data: d3,
                    bars: {
                        show: true,
                        shadowSize: 0,
                        barWidth: 25*60*1000,
                        lineWidth: 1
                    },
                    yaxis: 2,
                    color: "#999999",
                    mouse: {
                        trackFormatter: trackFormatter2
                    }
                },
                {
                    data: d2,
                    candles: {
                        show: true,
                        candleWidth: 20*60*1000,
                        lineWidth: 0,
                        upFillColor: "#4FB34F",
                        downFillColor: "#DF6262",
                        fillOpacity: 1
                    },
                    yaxis: 1,
                    mouse: {
                        trackFormatter: trackFormatter1
                    }
                }
            ], {
                title: base + "/" + counter + " (last: " + last + ")",
                xaxis: {
                    noTicks: 8,
                    mode: "time"
                },
                yaxis: {
                    // XXX: setting these makes the axis labels disappear
                    //max: max + (max-min) * 0.2,
                    //min: min - (max-min) * 0.2,
                    title: counter + ' per ' + base
                },
                y2axis: {
                    title: base + ' Volume',
                    min: 0,
                    tickFormatter: ticksFn
                },
                HtmlText: false,
                mouse: {
                    track: true,
                    relative: true,
                    lineColor: null,
                    lineWidth: 0
                }
            });
        });
    }
});
