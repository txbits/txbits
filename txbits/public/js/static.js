$(function(){
    $.get('/api/1').success(function(){
        $('.navbar .navbar-right .loggedin').show();
        $('.navbar .navbar-right .loggedout').hide();
    });
});