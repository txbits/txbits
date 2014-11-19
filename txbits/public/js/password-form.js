$(function(){
    // Hides the errors from all inputs and only shows the error on
    // $input. If no $input is provided, it hides all errors.
    function show_error($inputs, $input, error) {
        var $parent;
        for (var i = 0; i < $inputs.length; i++) {
            var $in = $inputs[i];
            if ($in == $input) {
                continue;
            }
            $parent = $in.parent();
            $parent.removeClass("has-error");
            $parent.find('.help-block').remove();
        }
        if ($input && error) {
            $parent = $input.parent();
            $parent.addClass("has-error");
            var $help = $parent.find('.help-block');
            if ($help.length > 0) {
                $help.text(error);
            } else {
                $parent.append($('<div>').addClass('help-block').text(error));
            }
        }
    }

    $('.password-form').each(function(){
        var $this = $(this);
        var min_len = $this.attr('data-password-length');
        var $password1 = $this.find('#password_password1');
        var $password2 = $this.find('#password_password2');
        var $inputs = [$password1, $password2];
        function check_form() {
            if ($password1.val().length < min_len) {
                show_error($inputs, $password1, "Password not long enough.");
            } else if ($password1.val() != $password2.val()) {
                show_error($inputs, $password2, "Passwords don't match.");
            } else {
                show_error($inputs);
            }
        }
        $password1.keyup(check_form);
        $password2.keyup(check_form);
    });
});