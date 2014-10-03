$(function(){
    $('#signup-form').submit(function(e) {
        if ($('#email').val() != $('#email2').val()) {
            e.preventDefault();
            $('#email2_field').addClass("has-error");
            $('#email2_error').show();
        }
    });
});