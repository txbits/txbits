-- Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
-- This file is licensed under the Affero General Public License version 3 or later,
-- see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

# Data

# --- !Ups

insert into currencies(currency, position) values ('BTC',10);
insert into currencies(currency, position) values ('LTC',20);
insert into currencies(currency, position) values ('USD',30);
insert into currencies(currency, position) values ('CAD',40);

insert into markets(base,counter, limit_min, position) values ('BTC','USD',0.01,10);
insert into markets(base,counter, limit_min, position) values ('LTC','USD',0.1,20);
insert into markets(base,counter, limit_min, position) values ('LTC','BTC',0.1,30);
insert into markets(base,counter, limit_min, position) values ('USD','CAD',1.00,40);

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

select create_user('me@viktorstanchev.com', 'password', true);
select create_user('a@a.com', 'qwerty123', false);

update balances set balance = 1000 where user_id in (select id from users where email in ('me@viktorstanchev.com', 'a@a.com')) and currency in ('USD', 'CAD');

# --- !Downs

delete from deposits_crypto;
delete from deposits_other;
delete from deposits;
delete from users_passwords;
delete from users_tfa_secrets;
delete from users_backup_otps;
delete from users_addresses;
delete from dw_fees;
delete from trade_fees;
delete from totp_tokens_blacklist;
delete from withdrawals_other;
delete from withdrawals_crypto;
delete from withdrawals_crypto_tx_mutated;
delete from withdrawals_crypto_tx_cold_storage;
delete from withdrawals_crypto_tx;
delete from withdrawals;
delete from currencies_crypto;
delete from wallets_crypto;
delete from balances;
delete from matches;
delete from orders; -- after matches
delete from markets; -- after orders
delete from withdrawal_limits;
delete from currencies; -- after balances, wallets_crypto, markets, withdrawal_limits
delete from users; -- after a lot of stuff

