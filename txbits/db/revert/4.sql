-- Revert txbits:4 from pg

BEGIN;
--SET ROLE txbits__owner;

alter table users drop column language;
alter table tokens drop column language;
alter table trusted_action_requests drop column language;

drop function if exists change_language (bigint, varchar(10)) cascade;
drop function if exists create_user (varchar(256), text, bool, text, varchar(10)) cascade;
drop function if exists update_user (bigint, varchar(256), bool, varchar(10)) cascade;
-- all of the following functions are copied from 1.sql

-- NOT "security definer", must be privileged user to use this function directly
create or replace function
create_user (
  a_email varchar(256),
  a_password text,
  a_onMailingList bool,
  a_pgp text
) returns bigint as $$
declare
  new_user_id bigint;
begin
  insert into users(id, email, on_mailing_list, pgp) values (
      generate_random_user_id(),
      a_email,
      a_onMailingList,
      a_pgp
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
  a_token varchar(256)
) returns bigint as $$
declare
  valid_token boolean;
begin
  if a_email = '' then
    raise 'User id 0 is not allowed to use this function.';
  end if;
  select true into valid_token from tokens where token = a_token and email = a_email and is_signup = true and expiration >= current_timestamp;
  if valid_token is null then
    return null;
  end if;
  delete from tokens where email = a_email and is_signup = true;
  return create_user(a_email, a_password, a_onMailingList, a_pgp);
end;
$$ language plpgsql volatile security definer set search_path = public, pg_temp cost 100;

create or replace function
update_user (
  a_id bigint,
  a_email varchar(256),
  a_onMailingList bool,
  a_username varchar(256)
) returns void as $$
begin
  if a_id = 0 then
    raise 'User id 0 is not allowed to use this function.';
  end if;
  update users set email=a_email, on_mailing_list=a_onMailingList, username=a_username where id=a_id;
  return;
end;
$$ language plpgsql volatile security definer set search_path = public, pg_temp cost 100;

create or replace function
find_token (
  a_token varchar(256),
  out tokens
) returns setof tokens as $$
  select token, email, creation, expiration, is_signup from tokens where token = a_token;
$$ language sql stable security definer set search_path = public, pg_temp cost 100;

create or replace function
trusted_action_start (
  a_email varchar(256),
  a_is_signup boolean
) returns boolean as $$
declare
  email_exists boolean;
begin
  select true into email_exists from trusted_action_requests where email = a_email and is_signup = a_is_signup;
  if email_exists then
    return false;
  end if;
  insert into trusted_action_requests values (a_email, a_is_signup);
  return true;
end;
$$ language plpgsql volatile security definer set search_path = public, pg_temp cost 100;


COMMIT;

-- vi: expandtab ts=2 sw=2
