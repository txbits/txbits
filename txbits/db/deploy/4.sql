-- TxBits - An open source Bitcoin and crypto currency exchange
-- Copyright (C) 2014-2015  Viktor Stanchev & Kirk Zathey
--
-- This program is free software: you can redistribute it and/or modify
-- it under the terms of the GNU Affero General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
--
-- This program is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU Affero General Public License for more details.
--
-- You should have received a copy of the GNU Affero General Public License
-- along with this program.  If not, see <http://www.gnu.org/licenses/>.


BEGIN;
--SET ROLE txbits__owner;

alter table users add column language varchar(10) default 'en' not null;
alter table tokens add column language varchar(10) default 'en' not null;
alter table trusted_action_requests add column language varchar(10) default 'en' not null;

drop function if exists create_user (varchar(256), text, bool, text) cascade;
drop function if exists update_user (bigint, varchar(256), bool) cascade;


create or replace function
change_language (
  a_id bigint,
  a_language varchar(10)
) returns boolean as $$
begin
  if a_id = 0 then
    raise 'User id 0 is not allowed to use this function.';
  end if;
  update users set language=a_language
  where id=a_id;
  return true;
end;
$$ language plpgsql volatile security definer set search_path = public, pg_temp cost 100;


-- NOT "security definer", must be privileged user to use this function directly
create or replace function
create_user (
  a_email varchar(256),
  a_password text,
  a_onMailingList bool,
  a_pgp text,
  a_language varchar(10),
  a_username varchar(256)
) returns bigint as $$
declare
  new_user_id bigint;
begin
  insert into users(id, email, on_mailing_list, pgp, language, username) values (
      generate_random_user_id(),
      a_email,
      a_onMailingList,
      a_pgp,
      a_language,
      a_username
    ) returning id into new_user_id;
  -- create balances associated with users
  insert into balances (user_id, currency) select new_user_id, currency from currencies;
  insert into users_passwords (user_id, password) values (
    new_user_id,
    crypt(a_password, gen_salt('bf', 8))
  );
  return new_user_id;
end;
$$ language plpgsql volatile security invoker set search_path = public, pg_temp cost 100;

create or replace function
create_user_complete (
  a_email varchar(256),
  a_password text,
  a_onMailingList bool,
  a_pgp text,
  a_token varchar(256),
  a_username varchar(256)
) returns bigint as $$
declare
  valid_token boolean;
  token_language text;
begin
  if a_email = '' then
    raise 'User id 0 is not allowed to use this function.';
  end if;
  select true, language into valid_token, token_language from tokens where token = a_token and lower(email) = lower(a_email) and is_signup = true and expiration >= current_timestamp;
  if valid_token is null then
    return null;
  end if;
  delete from tokens where lower(email) = lower(a_email) and is_signup = true;
  return create_user(a_email, a_password, a_onMailingList, a_pgp, token_language, a_username);
end;
$$ language plpgsql volatile security definer set search_path = public, pg_temp cost 100;

create or replace function
update_user (
  a_id bigint,
  a_email varchar(256),
  a_onMailingList bool,
  a_language varchar(10),
  a_username varchar(256)
) returns void as $$
begin
  if a_id = 0 then
    raise 'User id 0 is not allowed to use this function.';
  end if;
  update users set email=a_email, on_mailing_list=a_onMailingList, "language"=a_language, username=a_username where id=a_id;
  return;
end;
$$ language plpgsql volatile security definer set search_path = public, pg_temp cost 100;

create or replace function
find_token (
  a_token varchar(256),
  out tokens
) returns setof tokens as $$
  select * from tokens where token = a_token;
$$ language sql stable security definer set search_path = public, pg_temp cost 100;

create or replace function
trusted_action_start (
  a_email varchar(256),
  a_is_signup boolean,
  a_language varchar(10)
) returns boolean as $$
declare
  email_exists boolean;
  lang varchar(10);
begin
  select true into email_exists from trusted_action_requests where lower(email) = lower(a_email) and is_signup = a_is_signup;
  if email_exists then
    return false;
  end if;
  if a_language = '' or a_language is null then
    select language into lang from users where lower(email) = lower(a_email);
  else
    select a_language into lang;
  end if;
  insert into trusted_action_requests values (a_email, a_is_signup, lang);
  return true;
end;
$$ language plpgsql volatile security definer set search_path = public, pg_temp cost 100;
COMMIT;
