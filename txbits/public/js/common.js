var refresh_ticker = function(){};

$(function(){
    var $ttemplate = $("#tickers-template");
    if ($ttemplate.length) {
        var template = Handlebars.compile($ttemplate.html());

        refresh_ticker = function(){
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
                    tickers[i].last = zerosTrim(tickers[i].last);
                }
                $('#ticker').html(template(tickers));
            });
        };
        refresh_ticker();
    }
    showEmails();
});

function showEmails() {
    $('.hidden-email').each(function(idx, el) {
        var $el = $(el);
        $el.text($el.attr('data-name') + '@' + $el.attr('data-domain'));
        $el.attr('href', 'mailto:' + $el.text());
    });
}

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


// Google analytics

(function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
    (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
    m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
})(window,document,'script','//www.google-analytics.com/analytics.js','ga');

ga('create', 'UA-54524343-1', 'auto');
ga('send', 'pageview');

// Include the UserVoice JavaScript SDK (only needed once on a page)
UserVoice=window.UserVoice||[];(function(){var uv=document.createElement('script');uv.type='text/javascript';uv.async=true;uv.src='//widget.uservoice.com/xDR0gQn4m8VcCpTur0j4Q.js';var s=document.getElementsByTagName('script')[0];s.parentNode.insertBefore(uv,s)})();

//
// UserVoice Javascript SDK developer documentation:
// https://www.uservoice.com/o/javascript-sdk
//

// Set colors
UserVoice.push(['set', {
    accent_color: '#808283',
    trigger_color: 'white',
    trigger_background_color: 'rgba(46, 49, 51, 0.6)'
}]);

// Identify the user and pass traits
// To enable, replace sample data with actual user traits and uncomment the line
UserVoice.push(['identify', {
    //email:      'john.doe@@example.com', // User’s email address
    //name:       'John Doe', // User’s real name
    //created_at: 1364406966, // Unix timestamp for the date the user signed up
    //id:         123, // Optional: Unique id of the user (if set, this should not change)
    //type:       'Owner', // Optional: segment your users by type
    //account: {
    //  id:           123, // Optional: associate multiple users with a single account
    //  name:         'Acme, Co.', // Account name
    //  created_at:   1364406966, // Unix timestamp for the date the account was created
    //  monthly_rate: 9.99, // Decimal; monthly rate of the account
    //  ltv:          1495.00, // Decimal; lifetime value of the account
    //  plan:         'Enhanced' // Plan name for the account
    //}
}]);

// Add default trigger to the bottom-right corner of the window:
UserVoice.push(['addTrigger', { mode: 'smartvote', trigger_position: 'bottom-right' }]);

// Or, use your own custom trigger:
//UserVoice.push(['addTrigger', '#id', { mode: 'smartvote' }]);

// Autoprompt for Satisfaction and SmartVote (only displayed under certain conditions)
UserVoice.push(['autoprompt', {}]);