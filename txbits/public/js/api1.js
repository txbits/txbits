var API;
(function(){
    var prefix = '/iapi/1/';
    var limit = 20;

    // This creates a simple paginated function that makes calls to `path`
    function paginated(path) {
        return function(before, last_id) {
            return $.get(prefix+path, {before: before, limit: limit, last_id: last_id});
        };
    }

    // This adds an error handler to API calls to show the error in the UI
    var APIWrap = function(fn) {
        return function () {
            return fn.apply(this, arguments).error( function (res) {
                var err_text = '';
                try {
                    err_text = JSON.parse(res.responseText).message
                } catch (e) {
                    err_text = res.responseText;
                }
                $.pnotify({
                    title: 'API Error',
                    text: err_text,
                    styling: 'bootstrap',
                    type: 'error',
                    text_escape: true
                });
            });
        };
    };
    API = {
        balance: APIWrap(function() {
            return $.get(prefix+'balance', 'json');
        }),

        pending_withdrawals: APIWrap(function(){
            return $.get(prefix+'pending_withdrawals_all', 'json')
        }),

        pending_deposits: APIWrap(function(){
            return $.get(prefix+'pending_deposits_all', 'json')
        }),

        bid: APIWrap(function(base, counter, amount, price) {
            return $.ajax(prefix+'bid', {
                type: 'POST',
                data: JSON.stringify({base: base, counter: counter, amount: amount, price: price}),
                dataType: 'json',
                contentType: 'application/json'
            });
        }),

        ask: APIWrap(function(base, counter, amount, price) {
            return $.ajax(prefix+'ask', {
                type: 'POST',
                data: JSON.stringify({base: base, counter: counter, amount: amount, price: price}),
                dataType: 'json',
                contentType: 'application/json'
            });
        }),

        cancel: APIWrap(function(id) {
            return $.ajax(prefix+'cancel', {
                type: 'POST',
                data: JSON.stringify({order: Number(id)}),
                dataType: 'json',
                contentType: 'application/json'
            });
        }),

        withdraw: APIWrap(function(currency, amount, address, tfa_code) {
            return $.ajax(prefix+'withdraw', {
                type: 'POST',
                data: JSON.stringify({currency: currency, amount: amount, address: address, tfa_code: tfa_code}),
                dataType: 'json',
                contentType: 'application/json'
            });
        }),

        pairs: APIWrap(function() {
            return $.get(prefix+'pairs', 'json');
        }),

        currencies: APIWrap(function() {
            return $.get(prefix+'currencies', 'json');
        }),

        deposit_crypto: APIWrap(function(currency) {
            return $.get(prefix+'deposit_crypto/'+currency, 'json');
        }),

        deposit_crypto_all: APIWrap(function() {
            return $.get(prefix+'deposit_crypto_all', 'json');
        }),

        ticker: APIWrap(function() {
            return $.get('/api/1/ticker', 'json');
        }),

        trade_history: APIWrap(paginated('trade_history')),

        login_history: APIWrap(paginated('login_history')),

        deposit_withdraw_history: APIWrap(paginated('deposit_withdraw_history')),

        pending_trades: APIWrap(function() {
            return $.get(prefix+'pending_trades', 'json');
        }),

        trade_fees: APIWrap(function() {
            return $.get(prefix+'trade_fees', 'json');
        }),

        dw_fees: APIWrap(function() {
            return $.get(prefix+'dw_fees', 'json');
        }),

        dw_limits: APIWrap(function() {
            return $.get(prefix+'dw_limits', 'json');
        }),

        required_confirms: APIWrap(function() {
            return $.get(prefix+'required_confirms', 'json');
        }),

        open_trades: APIWrap(function(first, second) {
            return $.get('/api/1/open_trades/'+first+'/'+second, 'json');
        }),

        recent_trades: APIWrap(function(first, second) {
            return $.get('/api/1/recent_trades/'+first+'/'+second, 'json');
        }),

        user: APIWrap(function() {
            return $.get(prefix+'user', 'json');
        }),

        turnoff_tfa: APIWrap(function(code, password) {
            return $.ajax(prefix+'turnoff_tfa', {
                type: 'POST',
                data: JSON.stringify({tfa_code: code, password: password}),
                dataType: 'json',
                contentType: 'application/json'
            });
        }),

        turnoff_emails: APIWrap(function() {
            return $.ajax(prefix+'turnoff_emails', {
                type: 'POST',
                data: "{}",
                dataType: 'json',
                contentType: 'application/json'
            });
        }),

        turnon_emails: APIWrap(function() {
            return $.ajax(prefix+'turnon_emails', {
                type: 'POST',
                data: "{}",
                dataType: 'json',
                contentType: 'application/json'
            });
        }),

        gen_totp_secret: APIWrap(function() {
            return $.post(prefix+'gen_totp_secret', 'json');
        }),

        turnon_tfa: APIWrap(function(code, password) {
            return $.ajax(prefix+'turnon_tfa', {
                type: 'POST',
                data: JSON.stringify({tfa_code: code, password: password}),
                dataType: 'json',
                contentType: 'application/json'
            });
        }),

        add_pgp: APIWrap(function(password, code, pgp) {
            return $.ajax(prefix+'add_pgp', {
                type: 'POST',
                data: JSON.stringify({tfa_code: code, password: password, pgp: pgp}),
                dataType: 'json',
                contentType: 'application/json'
            });
        }),

        remove_pgp: APIWrap(function(password, code) {
            return $.ajax(prefix+'remove_pgp', {
                type: 'POST',
                data: JSON.stringify({tfa_code: code, password: password}),
                dataType: 'json',
                contentType: 'application/json'
            });
        }),

        add_api_key: APIWrap(function() {
            return $.ajax(prefix+'add_api_key', {
                type: 'POST',
                data: "{}",
                dataType: 'json',
                contentType: 'application/json'
            });
        }),

        update_api_key: APIWrap(function(code, api_key, comment, trading, trade_history, list_balance) {
            return $.ajax(prefix+'update_api_key', {
                type: 'POST',
                data: JSON.stringify({tfa_code: code, api_key: api_key, comment: comment, trading: trading, trade_history: trade_history, list_balance: list_balance}),
                dataType: 'json',
                contentType: 'application/json'
            });
        }),

        disable_api_key: APIWrap(function(code, api_key) {
            return $.ajax(prefix+'disable_api_key', {
                type: 'POST',
                data: JSON.stringify({tfa_code: code, api_key: api_key}),
                dataType: 'json',
                contentType: 'application/json'
            });
        }),

        get_api_keys: APIWrap(function() {
            return $.get(prefix+'get_api_keys', 'json');
        }),

        chart: APIWrap(function(base, counter) {
            return $.get(prefix+'chart/'+base+'/'+counter, 'json');
        })
    };
})();
