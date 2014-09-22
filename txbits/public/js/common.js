var refresh_ticker = function(){};
$(function(){
    var $ttemplate = $("#tickers-template");
    if ($ttemplate.length) {
        var template = Handlebars.compile($ttemplate.html());

        refresh_ticker = function(){
            API.ticker().success(function(res){
                var tickers = res.result;
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
                    tickers[i].last = zerosTrim(tickers[i].last);
                }
                $('#ticker').html(template(tickers));
            }).error(function(){
                $.pnotify({
                    title: 'Failed to load ticker.',
                    text: 'Reason: ' + (JSON.parse(res.responseText)).error,
                    styling: 'bootstrap',
                    type: 'error',
                    text_escape: true
                });
            });
        };
        refresh_ticker();
    }
});

function keepNumLength(m){
    var i = m.length - 1;
    while (m[i] == '0') {
        i--;
    }
    if (m[i] == '.') {
        i++;
    }
    return i;
}

function zerosToSpaces(m){
    m = Number(m).toFixed(8);
    var i = keepNumLength(m);
    return m.substring(0, i + 1) + Array(m.length - i).join('\xA0');
}

function zerosTrim(m){
    m = Number(m).toFixed(8);
    var i = keepNumLength(m);
    return m.substring(0, i + 1);
}
