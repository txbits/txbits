$(function(){
    // Hides the errors from all inputs and only shows the error on
    // $input. If no $input is provided, it hides all errors.
    function show_error($fields, $field, error) {
        var $parent;
        for (var i = 0; i < $fields.length; i++) {
            var $curr = $fields[i];
            if ($curr == $field) {
                continue;
            }
            $curr.removeClass("has-error");
            $curr.find('.help-block').remove();
        }
        if ($field && error) {
            $field.addClass("has-error");
            var $help = $field.find('.help-block');
            if ($help.length > 0) {
                $help.text(error);
            } else {
                $field.append($('<div>').addClass('help-block').text(error));
            }
        }
    }

    $('.password-form').each(function(){
        var $this = $(this);
        var min_len = $this.attr('data-password-length');
        var $currentPasswordField = $this.find('#currentPassword_field');
        var $currentPassword = $this.find('#currentPassword');
        var $password1Field = $this.find('#password_password1_field');
        var $password1 = $this.find('#password_password1');
        var $password2Field = $this.find('#password_password2_field');
        var $password2 = $this.find('#password_password2');
        var $accepttos = $this.find('#accepttos');
        var $fields = [$currentPasswordField, $password1Field, $password2Field];
        function check_form() {
            if ($password1.val().length < min_len && $password1.val().length > 0) {
                show_error($fields, $password1Field, "Password not long enough.");
            } else if ($password1.val() != $password2.val()) {
                show_error($fields, $password2Field, "Passwords don't match.");
            } else {
                show_error($fields);
            }
            if ($accepttos.length != 0) {
                if(!$accepttos.prop('checked')) {
                    $('#accepttos-label').addClass("text-danger");
                } else {
                    $('#accepttos-label').removeClass("text-danger");
                }
            }
        }
        $currentPassword.keyup(check_form);
        $password1.keyup(check_form);
        $password2.keyup(check_form);
        $accepttos.change(check_form);
        $this.submit(function(e) {
            check_form();
            // extra checks only when submitting
            if ($password1.val().length == 0) {
                show_error($fields, $password1Field, "Please enter a password.");
            }
            if ($this.find(".has-error").length > 0 || ($accepttos.length != 0 && !$accepttos.prop('checked'))) {
                e.preventDefault();
            }
        })
    });
});