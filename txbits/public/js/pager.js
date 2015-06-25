
var Pager = function(parent, child, spinner, requestContentCb, last_timestamp, last_id, options) {
    options = options || {};
    options.offset = options.offset || 100;
    this.rows_shown = 20;
    this.last_timestamp = last_timestamp;
    this.last_id = last_id;
    this.debounce_hold = false;
    this.requestContentCb = requestContentCb;
    this.parent = parent;
    this.child = child;
    this.spinner = spinner;
    this.done = false;
    this.options = options;

    $(parent).scroll(this.scroll_cb.bind(this));
};
Pager.prototype.success_cb = function(data) {
    this.rows_shown += data.length;
    if (data.length == 0) {
        this.done = true;
    }
};
Pager.prototype.finish_request_cb = function() {
    this.debounce_hold = false;
    this.spinner.hide();
};
Pager.prototype.scroll_cb = function() {
    if (this.parent.scrollTop() > this.child.height() - this.parent.height() - this.options.offset) {
        if (this.debounce_hold == false && this.done != true) {
            var promise = this.requestContentCb();
            this.spinner.show();
            promise.always(this.finish_request_cb.bind(this));
            promise.success(this.success_cb.bind(this));
            this.debounce_hold = true;
        }
    }
};

Pager.make_easy_pager = function(api, $parent, table_selector, body_selector, spinner_selector, template, row_template, preprocess) {
    api().success(function(data){
        if(data.length > 0) {
            var last_timestamp = data[data.length-1].created;
            var last_id = data[data.length-1].id;
            if(preprocess) {
                data = preprocess(data);
            }
        }

        var html = template(data);
        $parent.html(html);

        if(data.length > 0) {
            new Pager($(table_selector),
                $(body_selector),
                $(spinner_selector),
                function request_next_page() {
                    var that = this; // Pager
                    var promise = api(this.last_timestamp, this.last_id);
                    promise.success(function(data){
                        if(data.length > 0) {
                            that.last_timestamp = data[data.length-1].created;
                            that.last_id = data[data.length-1].id;
                            if(preprocess) {
                                data =  preprocess(data);
                            }
                            for (var d in data) {
                                var row = data[d];
                                $(body_selector).append(row_template(row))
                            }
                        }
                    });
                    return promise;
                },
                last_timestamp,
                last_id
            );
        }
    });
};