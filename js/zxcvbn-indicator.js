// this executes when zxcvbn is done loading so we can start showing password hints
function zxcvbn_load_hook() {
    var $passfield = $('#password_password1');
    if (!$passfield.length) {
        $passfield = $('#newPassword_password1');
    }
    function update() {
        var res = zxcvbn($passfield.val());
        $('#strength').text(res.score);
        $('#crack_time').text(res.crack_time_display);
    }

    $passfield.keypress(update).keyup(update).keydown(update)
}