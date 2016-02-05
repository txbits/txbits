// this executes when zxcvbn is done loading so we can start showing password hints
function zxcvbn_load_hook() {
    var $passfield = $('#password_password1');
    if (!$passfield.length) {
        $passfield = $('#newPassword_password1');
    }

    function declOfNum(number, titles)
    {
        cases = [2, 0, 1, 1, 1, 2];
        return titles[ (number%100>4 && number%100<20)? 2 : cases[(number%10<5)?number%10:5] ];
    }

    function update() {

        var res = zxcvbn($passfield.val());
        var message_arr = res.crack_time_display.split(' ');

        if (message_arr.length == 1){
            message = message_arr[0];
            result = Messages("zxvbn." + message) ;
        } else {
            message = message_arr[1];
            value = message_arr[0];

            switch (message) {
                case "minutes":
                    var titles = [Messages("zxvbn.minute"), Messages("zxvbn.minutes"), Messages("zxvbn.minute1")];
                    break;
                case "hours":
                    var titles = [Messages("zxvbn.hour"), Messages("zxvbn.hour1"), Messages("zxvbn.hours")];
                    break;
                case "days":
                    var titles = [Messages("zxvbn.day"), Messages("zxvbn.day1"), Messages("zxvbn.days")];
                    break;
                case "months":
                    var titles = [Messages("zxvbn.month"), Messages("zxvbn.month1"), Messages("zxvbn.months")];
                    break;
                case "years":
                    var titles = [Messages("zxvbn.year"), Messages("zxvbn.year1"), Messages("zxvbn.years")];
                    break;
            }

            result = value + " " + declOfNum(value, titles);
        }

        $('#strength').text(res.score);
        $('#crack_time').text(result);
    }

    $passfield.keypress(update).keyup(update).keydown(update)
}