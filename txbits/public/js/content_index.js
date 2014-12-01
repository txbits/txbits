$(function(){
    var tickers_template = Handlebars.compile($("#big-tickers-template").html());

    API.ticker().success(function(tickers){
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
});