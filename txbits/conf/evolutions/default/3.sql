-- Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
-- This file is licensed under the Affero General Public License version 3 or later,
-- see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

# Data

# --- !Ups

insert into currencies(currency) values('BTC');
insert into currencies(currency) values('LTC');
insert into currencies(currency) values('USD');
insert into currencies(currency) values('CAD');

insert into markets(base,counter,limit_min) values('BTC','USD',0.01);
insert into markets(base,counter,limit_min) values('LTC','USD',0.1);
insert into markets(base,counter,limit_min) values('LTC','BTC',0.1);
insert into markets(base,counter,limit_min) values('USD','CAD',1.00);

insert into dw_fees(currency, method, deposit_constant, deposit_linear, withdraw_constant, withdraw_linear) values ('BTC', 'blockchain', 0.000, 0.000, 0.001, 0.000);
insert into dw_fees(currency, method, deposit_constant, deposit_linear, withdraw_constant, withdraw_linear) values ('LTC', 'blockchain', 0.000, 0.000, 0.010, 0.000);
insert into dw_fees(currency, method, deposit_constant, deposit_linear, withdraw_constant, withdraw_linear) values ('USD', 'wire', 0.000, 0.000, 0.000, 0.000);
insert into dw_fees(currency, method, deposit_constant, deposit_linear, withdraw_constant, withdraw_linear) values ('CAD', 'wire', 0.000, 0.000, 0.000, 0.000);

insert into trade_fees(linear) values (0.005);

insert into withdrawal_limits(currency, limit_min, limit_max) values ('BTC', 0.010, 10);
insert into withdrawal_limits(currency, limit_min, limit_max) values ('LTC', 0.100, 100);
insert into withdrawal_limits(currency, limit_min, limit_max) values ('USD', 1, 10000);
insert into withdrawal_limits(currency, limit_min, limit_max) values ('CAD', 1, 10000);

insert into currencies_crypto(currency) values('BTC');
insert into currencies_crypto(currency) values('LTC');

insert into wallets_crypto(currency, last_block_read, balance_min, balance_warn, balance_target, balance_max) values('LTC', 42, 0, 0, 1000, 10000);
insert into wallets_crypto(currency, last_block_read, balance_min, balance_warn, balance_target, balance_max) values('BTC', 42, 0, 0, 100, 1000);

insert into users(id, email) values (0, '');

with new_user_row as (
  insert into users(email, on_mailing_list)
  values ('me@viktorstanchev.com', true)
  returning id
)
insert into passwords (user_id, password, hasher) values (
 (select id from new_user_row),
 '$2a$10$aYNjzFmwBSeqJeccRqKkN.Onh4co9wQVJn40Pv5GQwAEuwjBfmNYO',
 'bcrypt'
);

with new_user_row as (
  insert into users(email, on_mailing_list)
  values ('a@a.com', true)
  returning id
)
insert into passwords (user_id, password, hasher) values (
 (select id from new_user_row),
 '$2a$10$.bJb4l.7.75zgvYgd4mU8ejjT5C.6VrSMirgc3qGvdWsB8dXV0Bc2',
 'bcrypt'
);

insert into transactions(from_user_id, to_user_id, amount, currency, type) select null, id, 1000 , 'USD' , 'X' from users where email in ('me@viktorstanchev.com', 'a@a.com');
insert into transactions(from_user_id, to_user_id, amount, currency, type) select null, id, 1000 , 'CAD' , 'X' from users where email in ('me@viktorstanchev.com', 'a@a.com');

# --- !Downs

delete from deposits_crypto;
delete from deposits;
delete from passwords;
delete from users_addresses;
delete from dw_fees;
delete from trade_fees;
delete from transactions;
delete from totp_tokens_blacklist;
delete from withdrawals_crypto;
delete from withdrawals_crypto_tx_mutated;
delete from withdrawals_crypto_tx_cold_storage;
delete from withdrawals_crypto_tx;
delete from withdrawals;
delete from currencies_crypto;
delete from wallets_crypto;
delete from balances; -- after transactions
delete from matches;
delete from orders; -- after matches
delete from markets; -- after orders
delete from withdrawal_limits;
delete from currencies; -- after balances, wallets_crypto, markets, withdrawal_limits
delete from users; -- after a lot of stuff

