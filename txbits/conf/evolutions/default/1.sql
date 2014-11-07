-- Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
-- This file is licensed under the Affero General Public License version 3 or later,
-- see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

# Initial database

# --- !Ups

create extension pgcrypto;

grant select on play_evolutions to public;
grant all on play_evolutions_lock to public;

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

create sequence users_id_seq;
create table users (
    id bigint default nextval('users_id_seq') primary key,
    created timestamp default current_timestamp not null,
    email varchar(256) not null,
    on_mailing_list bool default false,
    tfa_withdrawal bool default false,
    tfa_login bool default false,
    tfa_secret varchar(256), -- todo: pick a size
    tfa_type varchar(6), -- totp
    verification int default 0 not null,
    active bool default true not null
);
create unique index unique_lower_email on users (lower(email));

create table passwords (
    user_id bigint not null,
    password varchar(256) not null, -- salt is a part of the password field
    created timestamp default current_timestamp not null,
    foreign key (user_id) references users(id),
    primary key (user_id, created)
);

create table totp_tokens_blacklist (
    user_id bigint not null,
    token char(6) not null,
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
    ipv4 integer,
    ipv6 numeric(40),
    browser_headers text, -- these can be parsed later to produce country info
    browser_id text, -- result of deanonymization
    ssl_info text, -- what ciphers were offered, what cipher was accepted, etc.
    type text -- one of: login_partial_success, login_success, login_failure, logout, session_expired
);

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
    foreign key (currency) references currencies(currency),
    foreign key (user_id) references users(id)
);

create table deposits_crypto (
    id bigint not null primary key,
    tx_hash varchar(64) not null unique,
    address varchar(34) not null,
    confirmed timestamp,
    foreign key (address) references users_addresses(address),
    foreign key (id) references deposits(id)
);
create index deposits_crypto_address_idx on deposits_crypto(address);

create sequence withdrawal_id_seq;
create table withdrawals (
    id bigint default nextval('withdrawal_id_seq') primary key,
    amount numeric(23,8) not null check(amount > 0), -- before the fee
    created timestamp default current_timestamp not null,
    user_id bigint not null,
    currency varchar(4) not null,
    fee numeric(23,8) not null check(fee >= 0),
    foreign key (currency) references currencies(currency),
    foreign key (user_id) references users(id)
);
create index withdrawals_limit_idx on withdrawals(user_id, currency, created desc);

create sequence withdrawals_crypto_tx_id_seq;
create table withdrawals_crypto_tx (
    id bigint default nextval('withdrawals_crypto_tx_id_seq') primary key,
    tx_hash varchar(64) unique,
    currency varchar(4) not null,
    tx_fee numeric(23,8),
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
    foreign key (id) references withdrawals(id),
    foreign key (withdrawals_crypto_tx_id) references withdrawals_crypto_tx(id)
);
create index withdrawals_crypto_tx_idx on withdrawals_crypto(withdrawals_crypto_tx_id);

create table tokens (
    token varchar(256) not null primary key,
    email varchar(256) not null,
    creation timestamp not null,
    expiration timestamp not null,
    is_signup bool not null
);
create index tokens_expiration_idx on tokens(expiration desc);

# --- !Downs

drop table if exists balances cascade;
drop table if exists orders cascade;
drop table if exists currencies cascade;
drop table if exists deposits cascade;
drop table if exists deposits_crypto cascade;
drop table if exists matches cascade;
drop table if exists markets cascade;
drop table if exists tokens cascade;
drop table if exists passwords cascade;
drop table if exists users cascade;
drop table if exists users_addresses cascade;
drop table if exists withdrawals cascade;
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
drop sequence if exists order_id_seq cascade;
drop sequence if exists deposit_id_seq cascade;
drop sequence if exists users_id_seq cascade;
drop sequence if exists withdrawal_id_seq cascade;
drop sequence if exists market_id_seq cascade;
drop sequence if exists event_log_id_seq cascade;
drop sequence if exists withdrawals_crypto_tx_id_seq cascade;
drop extension pgcrypto;
