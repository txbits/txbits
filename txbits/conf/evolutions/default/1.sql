-- Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
-- This file is licensed under the Affero General Public License version 3 or later,
-- see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

# Initial database

# --- !Ups

create extension pgcrypto;

grant select on play_evolutions to public;
revoke create on schema public from public;

create table currencies (
    currency varchar(4) not null primary key,
    position int not null -- used for displaying
);

create table dw_fees (
    currency varchar(4) not null,
    method varchar(10) not null,
    deposit_constant numeric(23,8) not null check(deposit_constant >= 0),
    deposit_linear numeric(23,8) not null check(deposit_linear >= 0),
    withdraw_constant numeric(23,8) not null check(withdraw_constant >= 0),
    withdraw_linear numeric(23,8) not null check(withdraw_linear >= 0),
    primary key (currency, method),
    foreign key (currency) references currencies(currency)
);

create table trade_fees (
    linear numeric(23,8) not null check(linear >= 0)
);

create table users (
    id bigint primary key,
    created timestamp default current_timestamp not null,
    email varchar(256) not null,
    on_mailing_list bool default false not null,
    tfa_enabled bool default false not null,
    verification int default 0 not null,
    pgp text,
    active bool default true not null
);
create unique index unique_lower_email on users (lower(email));

create table users_passwords (
    user_id bigint not null,
    password varchar(256) not null, -- salt is a part of the password field
    created timestamp default current_timestamp not null,
    foreign key (user_id) references users(id),
    primary key (user_id, created)
);

create table users_api_keys (
    user_id bigint not null,
    api_key text not null unique check(length(api_key) = 24), -- 18 bytes in base64
    comment text not null default '',
    created timestamp default current_timestamp not null,
    active bool default true not null,
    trading bool default false not null,
    trade_history bool default false not null,
    list_balance bool default false not null,
    foreign key (user_id) references users(id),
    primary key (user_id, created)
);

create table users_tfa_secrets (
    user_id bigint not null,
    tfa_secret varchar(256),
    created timestamp default current_timestamp not null,
    foreign key (user_id) references users(id),
    primary key (user_id, created)
);

create table users_backup_otps (
    user_id bigint not null,
    otp int not null,
    created timestamp default current_timestamp not null,
    foreign key (user_id) references users(id),
    primary key (user_id, otp)
);

create table trusted_action_requests (
    email varchar(256),
    is_signup boolean not null,
    primary key (email, is_signup)
);

create table totp_tokens_blacklist (
    user_id bigint not null,
    token int not null,
    expiration timestamp not null,
    foreign key (user_id) references users(id)
);
create index totp_tokens_blacklist_expiration_idx on totp_tokens_blacklist(expiration desc);

create sequence event_log_id_seq;
create table event_log (
    id bigint default nextval('event_log_id_seq') primary key,
    created timestamp default current_timestamp not null,
    email varchar(256),
    user_id bigint,
    ip inet,
    browser_headers text, -- these can be parsed later to produce country info
    browser_id text, -- result of deanonymization
    ssl_info text, -- what ciphers were offered, what cipher was accepted, etc.
    type text -- one of: login_partial_success, login_success, login_failure, logout, session_expired
);
create index login_log_idx on event_log(user_id, created desc) where type in ('login_success', 'login_failure', 'logout', 'session_expired');

create table balances (
    user_id bigint not null,
    currency varchar(4) not null,
    balance numeric(23,8) default 0 not null,
    hold numeric(23,8) default 0 not null,
    constraint positive_balance check(balance >= 0),
    constraint positive_hold check(hold >= 0),
    constraint no_hold_above_balance check(balance >= hold),
    foreign key (user_id) references users(id),
    foreign key (currency) references currencies(currency),
    primary key (user_id, currency)
);

create table withdrawal_limits (
    currency varchar(4) not null,
    limit_min numeric(23,8) not null check(limit_min > 0),
    limit_max numeric(23,8) not null check(limit_max >= 0),
    foreign key (currency) references currencies(currency),
    primary key (currency)
);

create sequence market_id_seq;
create table markets (
    id bigint default nextval('market_id_seq') primary key,
    base varchar(4) not null, -- BTC in BTC/USD
    counter varchar(4) not null, -- USD in BTC/USD
    unique (base, counter),
    limit_min numeric(23,8) not null check(limit_min > 0), -- minimum amount of base currency in an order
    active bool default true not null, -- false prevents new orders from being inserted
    position int not null, -- used for displaying
    foreign key (base) references currencies(currency),
    foreign key (counter) references currencies(currency)
);

create sequence order_id_seq;
create table orders (
    id bigint default nextval('order_id_seq') primary key,
    created timestamp default current_timestamp not null,
    original numeric(23,8) not null check(original > 0),
    closed bool default false not null,
    remains numeric(23,8) not null check(original >= remains and (remains > 0 or remains = 0 and closed = true)),
    price numeric(23,8) not null check(price > 0),
    user_id bigint not null,
    base varchar(4) not null,
    counter varchar(4) not null,
    is_bid bool not null,
    foreign key (base, counter) references markets(base, counter),
    foreign key (user_id) references users(id)
);
create index bid_idx on orders(base, counter, price desc, created asc) where closed = false and remains > 0 and is_bid = true;
create index ask_idx on orders(base, counter, price asc, created asc) where closed = false and remains > 0 and is_bid = false;
create index user_pending_trades_idx on orders(user_id, created desc) where closed = false and remains > 0;

create table matches (
    ask_user_id bigint not null,
    bid_user_id bigint not null,
    ask_order_id bigint not null,
    bid_order_id bigint not null,
    amount numeric(23,8) not null check(amount > 0),
    ask_fee numeric(23,8) not null check(ask_fee >= 0),
    bid_fee numeric(23,8) not null check(bid_fee >= 0),
    price numeric(23,8) not null check(price > 0),
    created timestamp default current_timestamp not null,
    is_bid bool not null, -- true when an ask was matched by a new bid
    base varchar(4) not null,
    counter varchar(4) not null,
    foreign key (base, counter) references markets(base, counter),
    foreign key (ask_order_id) references orders(id),
    foreign key (bid_order_id) references orders(id),
    foreign key (ask_user_id) references users(id),
    foreign key (bid_user_id) references users(id),
    primary key (ask_order_id, bid_order_id)
);
create index matches_bid_user_idx on matches(bid_user_id, created desc);
create index matches_ask_user_idx on matches(ask_user_id, created desc);
create index recent_trades_idx on matches(base, counter, created desc);

create table currencies_crypto (
    currency varchar(4) primary key,
    active bool default true not null,
    min_deposit_confirmations integer default 3 not null check(min_deposit_confirmations > 0),
    min_withdrawal_confirmations integer default 3 not null check(min_withdrawal_confirmations > 0),
    foreign key (currency) references currencies(currency)
);

create table wallets_crypto (
    currency varchar(4) not null,
    node_id integer default 0 not null,
    retired bool default false not null, -- set to true when a wallet.dat file is no longer used
    last_block_read integer,
    last_withdrawal_time_received integer,
    balance numeric(23,8) default 0 not null check(balance >= 0),
    balance_min numeric(23,8) not null check(balance_min >= 0),
    balance_warn numeric(23,8) not null check(balance_warn >= balance_min),
    balance_target numeric(23,8) not null check(balance_target > balance_warn),
    balance_max numeric(23,8) not null check(balance_max > balance_target),
    foreign key (currency) references currencies(currency),
    primary key (currency, node_id)
);

create table users_addresses (
    address varchar(34) primary key,
    user_id bigint default 0 not null,
    currency varchar(4) not null,
    node_id integer not null,
    created timestamp default current_timestamp not null,
    assigned timestamp,
    foreign key (currency) references currencies(currency),
    foreign key (currency, node_id) references wallets_crypto(currency, node_id),
    foreign key (user_id) references users(id)
);
create index users_addresses_idx on users_addresses(user_id, currency, assigned desc);

create sequence deposit_id_seq;
create table deposits (
    id bigint default nextval('deposit_id_seq') primary key,
    amount numeric(23,8) not null check(amount > 0), -- before the fee
    created timestamp default current_timestamp not null,
    user_id bigint not null,
    currency varchar(4) not null,
    fee numeric(23,8) not null check(fee >= 0),
    unique(id, amount), -- for foreign key constraint on deposits_crypto
    foreign key (currency) references currencies(currency),
    foreign key (user_id) references users(id)
);
create index deposits_idx on deposits(user_id, created desc);

create table deposits_crypto (
    id bigint not null primary key,
    amount numeric(23,8) not null, -- already in deposits but needed here for a unique constraint
    tx_hash varchar(64) not null,
    address varchar(34) not null,
    confirmed timestamp,
    unique(address, tx_hash, amount),
    foreign key (id, amount) references deposits(id, amount),
    foreign key (address) references users_addresses(address),
    foreign key (id) references deposits(id)
);

create table deposits_other (
    id bigint not null primary key,
    reason text not null,
    foreign key (id) references deposits(id)
);

create sequence withdrawal_id_seq;
create table withdrawals (
    id bigint default nextval('withdrawal_id_seq') primary key,
    amount numeric(23,8) not null check(amount > 0), -- before the fee
    created timestamp default current_timestamp not null,
    user_id bigint not null,
    currency varchar(4) not null,
    fee numeric(23,8) not null check(fee >= 0),
    confirmation_token varchar(256) default null, -- this is set to non-null when the user trust service sends out the token
    token_expiration timestamp, -- this is set to non-null when the user trust service sends out the token
    user_confirmed boolean not null default false,
    user_rejected boolean not null default false,
    check(not (user_confirmed and user_rejected)),
    foreign key (currency) references currencies(currency),
    foreign key (user_id) references users(id)
);
create index withdrawals_confirmation_token_idx on withdrawals(confirmation_token);
create index withdrawals_limit_idx on withdrawals(user_id, currency, created desc);
create index withdrawals_idx on withdrawals(user_id, created desc);

create sequence withdrawals_crypto_tx_id_seq;
create table withdrawals_crypto_tx (
    id bigint default nextval('withdrawals_crypto_tx_id_seq') primary key,
    tx_hash varchar(64) unique,
    currency varchar(4) not null,
    tx_amount numeric(23,8) check(tx_amount >= 0),
    tx_fee numeric(23,8) check(tx_fee >= 0),
    node_id integer not null,
    created timestamp default current_timestamp not null,
    sent timestamp,
    confirmed timestamp,
    foreign key (currency) references currencies(currency),
    foreign key (currency, node_id) references wallets_crypto(currency, node_id)
);

create table withdrawals_crypto_tx_cold_storage (
    id bigint not null primary key,
    address varchar(34) not null,
    value numeric(23,8) not null check(value > 0),
    foreign key (id) references withdrawals_crypto_tx(id)
);

create table withdrawals_crypto_tx_mutated (
    id bigint not null primary key,
    tx_hash_mutated varchar(64) not null unique,
    foreign key (id) references withdrawals_crypto_tx(id)
);

create table withdrawals_crypto (
    id bigint not null primary key,
    withdrawals_crypto_tx_id bigint,
    address varchar(34) not null,
    foreign key (id) references withdrawals(id) on delete cascade,
    foreign key (withdrawals_crypto_tx_id) references withdrawals_crypto_tx(id)
);
create index withdrawals_crypto_tx_idx on withdrawals_crypto(withdrawals_crypto_tx_id, address);

create table withdrawals_other (
    id bigint not null primary key,
    reason text not null,
    foreign key (id) references withdrawals(id) on delete cascade
);

create table tokens (
    token varchar(256) not null primary key,
    email varchar(256) not null,
    creation timestamp not null,
    expiration timestamp not null,
    is_signup bool not null
);
create index tokens_expiration_idx on tokens(expiration desc);

-- aggregate match statistics in 30 minute buckets
create table stats_30_min (
    base varchar(4) not null,
    counter varchar(4) not null,
    start_of_period timestamp not null check(extract(minute from start_of_period) in (0, 30)),
    volume numeric(23,8) not null,
    low numeric(23,8) not null,
    high numeric(23,8) not null,
    open numeric(23,8) not null,
    close numeric(23,8) not null,
    foreign key (base, counter) references markets(base, counter),
    primary key (base, counter, start_of_period)
);

# --- !Downs

drop table if exists balances cascade;
drop table if exists orders cascade;
drop table if exists currencies cascade;
drop table if exists deposits cascade;
drop table if exists deposits_crypto cascade;
drop table if exists deposits_other cascade;
drop table if exists matches cascade;
drop table if exists stats_30_min cascade;
drop table if exists markets cascade;
drop table if exists tokens cascade;
drop table if exists users cascade;
drop table if exists users_passwords cascade;
drop table if exists users_api_keys cascade;
drop table if exists users_backup_otps cascade;
drop table if exists users_tfa_secrets cascade;
drop table if exists users_addresses cascade;
drop table if exists withdrawals cascade;
drop table if exists withdrawals_other cascade;
drop table if exists withdrawals_crypto cascade;
drop table if exists withdrawals_crypto_tx cascade;
drop table if exists withdrawals_crypto_tx_cold_storage cascade;
drop table if exists withdrawals_crypto_tx_mutated cascade;
drop table if exists currencies_crypto cascade;
drop table if exists wallets_crypto cascade;
drop table if exists dw_fees cascade;
drop table if exists trade_fees cascade;
drop table if exists totp_tokens_blacklist cascade;
drop table if exists event_log cascade;
drop table if exists withdrawal_limits cascade;
drop table if exists trusted_action_requests cascade;
drop sequence if exists order_id_seq cascade;
drop sequence if exists deposit_id_seq cascade;
drop sequence if exists withdrawal_id_seq cascade;
drop sequence if exists market_id_seq cascade;
drop sequence if exists event_log_id_seq cascade;
drop sequence if exists withdrawals_crypto_tx_id_seq cascade;
drop extension pgcrypto;
