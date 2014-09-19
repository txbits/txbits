-- Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
-- This file is licensed under the Affero General Public License version 3 or later,
-- see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

# Initial database

# --- !Ups

CREATE TABLE currencies (
    currency varchar(4) NOT NULL PRIMARY KEY,
    precision int -- Used for rounding
);

CREATE TABLE dw_fees (
    currency varchar(4) NOT NULL,
    method varchar(10) NOT NULL,
    deposit_constant numeric(23,8) NOT NULL,
    deposit_linear numeric(23,8) NOT NULL,
    withdraw_constant numeric(23,8) NOT NULL,
    withdraw_linear numeric(23,8) NOT NULL,
    UNIQUE (currency,method),
    FOREIGN KEY (currency) REFERENCES currencies(currency)
);

CREATE TABLE trade_fees (
    linear numeric(23,8) NOT NULL
);

CREATE SEQUENCE users_id_seq;
CREATE TABLE users (
    id bigint DEFAULT nextval('users_id_seq') PRIMARY KEY,
    created timestamp DEFAULT current_timestamp NOT NULL,
    email varchar(256) NOT NULL UNIQUE,
    password varchar(256) NOT NULL, -- salt is a part of the password field
    hasher varchar(256) NOT NULL,
    on_mailing_list bool DEFAULT false,
    tfa_withdrawal bool DEFAULT false,
    tfa_login bool DEFAULT false,
    tfa_secret varchar(256), -- TODO: pick a size
    tfa_type varchar(6) check(tfa_type IS NULL OR tfa_type = 'TOTP'), -- TOTP
    verification int DEFAULT 0 NOT NULL,
    active bool DEFAULT true NOT NULL
);

CREATE TABLE totp_tokens_blacklist (
    user_id bigint NOT NULL,
    token char(6) NOT NULL,
    expiration timestamp NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE SEQUENCE event_log_id_seq;
CREATE TABLE event_log (
    id bigint DEFAULT nextval('event_log_id_seq') PRIMARY KEY,
    created timestamp DEFAULT current_timestamp NOT NULL,
    email varchar(256),
    user_id bigint,
    -- I don't think we should store passwords attempted. It's a security risk.
    ipv4 integer,
    ipv6 numeric(40),
    browser_headers text, -- these can be parsed later to produce country info,
    browser_id text, -- result of deanonymization
    ssl_info text, -- what ciphers were offered, what cipher was accepted, etc.
    type text -- One of: login_partial_success, login_success, login_failure, logout, session_expired
);

CREATE TABLE balances (
    user_id bigint NOT NULL,
    currency varchar(4) NOT NULL,
    balance numeric(23,8) DEFAULT 0 NOT NULL,
    hold numeric(23,8) DEFAULT 0 NOT NULL,
    CONSTRAINT positive_balance check(balance >= 0),
    CONSTRAINT positive_hold check(hold >= 0),
    CONSTRAINT no_hold_above_balance check(balance >= hold),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (currency) REFERENCES currencies(currency),
    PRIMARY KEY (user_id, currency)
);

CREATE TABLE withdrawal_limits (
    currency varchar(4) NOT NULL,
    limit_min numeric(23,8) NOT NULL,
    limit_max numeric(23,8) NOT NULL,
    FOREIGN KEY (currency) REFERENCES currencies(currency),
    PRIMARY KEY (currency)
);

CREATE SEQUENCE transaction_id_seq;
CREATE TABLE transactions (
    id bigint DEFAULT nextval('transaction_id_seq') PRIMARY KEY,
    from_user_id bigint, -- can be null when it's a deposit
    to_user_id bigint, -- can be null when it's a withdrawal or fee
    amount numeric(23,8) NOT NULL,
    currency varchar(4) NOT NULL,
    created timestamp DEFAULT current_timestamp,
    type varchar(1) NOT NULL, -- D=deposit W=withdrawal M=match F=fee
    FOREIGN KEY (from_user_id, currency) REFERENCES balances(user_id, currency),
    FOREIGN KEY (to_user_id, currency) REFERENCES balances(user_id, currency)
);

CREATE SEQUENCE market_id_seq;
CREATE TABLE markets (
    id bigint DEFAULT nextval('market_id_seq') PRIMARY KEY,
    base varchar(4) NOT NULL, -- BTC in BTC/USD
    counter varchar(4) NOT NULL, -- USD in BTC/USD
    UNIQUE (base, counter),
    limit_min numeric(23,8) NOT NULL, -- minimum amount of base currency in an order
    active bool DEFAULT true NOT NULL, -- false prevents new orders from being inserted
    FOREIGN KEY (base) REFERENCES currencies(currency),
    FOREIGN KEY (counter) REFERENCES currencies(currency)
);

CREATE SEQUENCE order_id_seq;
CREATE TABLE orders (
    id bigint DEFAULT nextval('order_id_seq') PRIMARY KEY,
    created timestamp DEFAULT current_timestamp NOT NULL,
    original numeric(23,8) NOT NULL check(original > 0),
    closed bool DEFAULT false NOT NULL,
    remains numeric(23,8) NOT NULL check(original >= remains AND (remains >= 0 OR remains = 0 AND closed = true)),
    price numeric(23,8) NOT NULL check(price > 0),
    user_id bigint NOT NULL,
    base varchar(4) NOT NULL,
    counter varchar(4) NOT NULL,
    type varchar(3) NOT NULL, -- "bid" or "ask"
    unique (id, user_id, base, counter),
    FOREIGN KEY (base, counter) REFERENCES markets(base, counter),
    FOREIGN KEY (user_id) REFERENCES users(id)
);
--TODO: consider using partitioning instead: http://www.postgresql.org/docs/9.1/static/ddl-partitioning.html
create index orders_closed_idx on orders(closed) where closed = false;
create index orders_remains_idx on orders(remains) where remains > 0;

CREATE TABLE matches (
    ask_user_id bigint NOT NULL,
    bid_user_id bigint NOT NULL,
    ask_order_id bigint NOT NULL,
    bid_order_id bigint NOT NULL,
    amount numeric(23,8) NOT NULL,
    ask_fee numeric(23,8) NOT NULL,
    bid_fee numeric(23,8) NOT NULL,
    price numeric(23,8) NOT NULL check(price > 0),
    created timestamp DEFAULT current_timestamp NOT NULL,
    type varchar(3) NOT NULL,
    base varchar(4) NOT NULL,
    counter varchar(4) NOT NULL,

-- TODO: we won't have references to transactions for now.. for testing... later we should add them
--     first_transaction_id bigint not null,
--     second_transaction_id bigint not null,
--     ask_fee_transaction_id bigint,
--     bid_fee_transaction_id bigint,

--     FOREIGN KEY (first_transaction_id) REFERENCES transactions(id),
--     FOREIGN KEY (second_transaction_id) REFERENCES transactions(id),
--     FOREIGN KEY (ask_fee_transaction_id) REFERENCES transactions(id),
--     FOREIGN KEY (bid_fee_transaction_id) REFERENCES transactions(id),

    FOREIGN KEY (base, counter) REFERENCES markets(base, counter),
    PRIMARY KEY (ask_order_id, bid_order_id),
    FOREIGN KEY (ask_order_id, ask_user_id, base, counter) REFERENCES orders(id, user_id, base, counter),
    FOREIGN KEY (bid_order_id, bid_user_id, base, counter) REFERENCES orders(id, user_id, base, counter)
);

CREATE TABLE currencies_crypto (
    currency varchar(4) PRIMARY KEY,
    active bool DEFAULT true NOT NULL,
    min_deposit_confirmations integer DEFAULT 3 NOT NULL check(min_deposit_confirmations > 0),
    min_withdrawal_confirmations integer DEFAULT 3 NOT NULL check(min_withdrawal_confirmations > 0),
    FOREIGN KEY (currency) REFERENCES currencies(currency)
);

CREATE TABLE wallets_crypto (
    currency varchar(4) NOT NULL,
    node_id integer DEFAULT 0 NOT NULL,
    retired bool DEFAULT false NOT NULL, -- set to true when a wallet.dat file is no longer used
    last_block_read integer,
    last_withdrawal_time_received integer,
    balance numeric(23,8) DEFAULT 0 NOT NULL,
    balance_min numeric(23,8) NOT NULL check(balance_min >= 0),
    balance_warn numeric(23,8) NOT NULL check(balance_warn >= balance_min),
    balance_target numeric(23,8) NOT NULL check(balance_target > balance_warn),
    balance_max numeric(23,8) NOT NULL check(balance_max > balance_target),
    PRIMARY KEY (currency, node_id),
    FOREIGN KEY (currency) REFERENCES currencies(currency)
);

CREATE TABLE users_addresses (
    address varchar(34) PRIMARY KEY,
    user_id bigint DEFAULT 0 NOT NULL,
    currency varchar(4) NOT NULL,
    node_id integer NOT NULL,
    created timestamp DEFAULT current_timestamp NOT NULL,
    assigned timestamp,
    FOREIGN KEY (currency) REFERENCES currencies(currency),
    FOREIGN KEY (currency, node_id) REFERENCES wallets_crypto(currency, node_id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE SEQUENCE deposit_id_seq;
CREATE TABLE deposits (
    id bigint DEFAULT nextval('deposit_id_seq') PRIMARY KEY,
    amount numeric(23,8) NOT NULL, --before the fee
    created timestamp DEFAULT current_timestamp NOT NULL,
    user_id bigint NOT NULL,
    currency varchar(4) NOT NULL,
  --TODO: require transaction ids to be referenced
  --transaction_id bigint not null,
    fee numeric(23,8) NOT NULL,
    --FOREIGN KEY (transaction_id) REFERENCES transactions(id),
    FOREIGN KEY (currency) REFERENCES currencies(currency),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE deposits_crypto (
    id bigint NOT NULL PRIMARY KEY,
    tx_hash varchar(64) NOT NULL UNIQUE,
    address varchar(34) NOT NULL,
    confirmed timestamp,
    FOREIGN KEY (address) REFERENCES users_addresses(address),
    FOREIGN KEY (id) REFERENCES deposits(id)
);

CREATE SEQUENCE withdrawal_id_seq;
CREATE TABLE withdrawals (
    id bigint DEFAULT nextval('withdrawal_id_seq') PRIMARY KEY,
    amount numeric(23,8) NOT NULL, --before the fee
    created timestamp DEFAULT current_timestamp NOT NULL,
    user_id bigint NOT NULL,
    currency varchar(4) NOT NULL,
    --TODO: require transaction ids to be referenced
    --transaction_id bigint not null,
    fee numeric(23,8) not null,
    --FOREIGN KEY (transaction_id) REFERENCES transactions(id),
    FOREIGN KEY (currency) REFERENCES currencies(currency),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE SEQUENCE withdrawals_crypto_tx_id_seq;
CREATE TABLE withdrawals_crypto_tx (
    id bigint DEFAULT nextval('withdrawals_crypto_tx_id_seq') PRIMARY KEY,
    tx_hash varchar(64) UNIQUE,
    currency varchar(4) NOT NULL,
    tx_fee numeric(23,8),
    node_id integer NOT NULL,
    created timestamp DEFAULT current_timestamp NOT NULL,
    sent timestamp,
    confirmed timestamp,
    FOREIGN KEY (currency) REFERENCES currencies(currency),
    FOREIGN KEY (currency, node_id) REFERENCES wallets_crypto(currency, node_id)
);

CREATE TABLE withdrawals_crypto_tx_cold_storage (
    id bigint NOT NULL PRIMARY KEY,
    address varchar(34) NOT NULL,
    value numeric(23,8) NOT NULL,
    FOREIGN KEY (id) REFERENCES withdrawals_crypto_tx(id)
);

CREATE TABLE withdrawals_crypto_tx_mutated (
    id bigint NOT NULL PRIMARY KEY,
    tx_hash_mutated varchar(64) NOT NULL UNIQUE,
    FOREIGN KEY (id) REFERENCES withdrawals_crypto_tx(id)
);

CREATE TABLE withdrawals_crypto (
    id bigint NOT NULL PRIMARY KEY,
    withdrawals_crypto_tx_id bigint,
    address varchar(34) NOT NULL,
    FOREIGN KEY (id) REFERENCES withdrawals(id),
    FOREIGN KEY (withdrawals_crypto_tx_id) REFERENCES withdrawals_crypto_tx(id)
);

CREATE TABLE tokens (
    token varchar(256) NOT NULL PRIMARY KEY,
    email varchar(256),
    creation timestamp,
    expiration timestamp,
    is_signup smallint
);

# --- !Downs

drop table if exists balances cascade;
drop table if exists orders cascade;
drop table if exists currencies cascade;
drop table if exists deposits cascade;
drop table if exists deposits_crypto cascade;
drop table if exists matches cascade;
drop table if exists markets cascade;
drop table if exists tokens cascade;
drop table if exists users cascade;
drop table if exists users_addresses cascade;
drop table if exists withdrawals cascade;
drop table if exists withdrawals_crypto cascade;
drop table if exists withdrawals_crypto_tx cascade;
drop table if exists withdrawals_crypto_tx_cold_storage cascade;
drop table if exists withdrawals_crypto_tx_mutated cascade;
drop table if exists currencies_crypto cascade;
drop table if exists wallets_crypto cascade;
drop table if exists transactions cascade;
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
drop sequence if exists transaction_id_seq cascade;
drop sequence if exists event_log_id_seq cascade;
drop sequence if exists withdrawals_crypto_tx_id_seq cascade;

