$(function(){
    var $email = $('#email');
    var $email2 = $('#email2');
    var match_error = false;
    $('#signup-form').on('submit', function(e) {
        if ($email.val() != $email2.val()) {
            e.preventDefault();
            e.returnValue = false;
            if (!match_error) {
                $('#email2_field').addClass("has-error").append(
                    '<span class="help-block">Emails do not match</span>');
                match_error = true;
            }
        }
    });
});