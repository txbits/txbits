package usertrust

import anorm._

object SQLText {
  val getTrustedActionRequests = SQL(
    """
    | select email, is_signup from trusted_action_requests
    |""".stripMargin)

  val getPendindWithdrawalRequests = SQL(
    """
      | select w.*, u.email, u.pgp, wc.address as destination from withdrawals w
      | left join users u on w.user_id = u.id
      | left join withdrawals_crypto wc on w.id = wc.id
      | where confirmation_token is null
      |""".stripMargin)

  val getWithdrawalsCryptoById = SQL(
    """
    | select * from withdrawals_crypto where id = {id}
    |""".stripMargin)

  val saveWithdrawalToken = SQL(
    """
    | update withdrawals set confirmation_token = {token}, token_expiration = {expiration} where id = {id}
    |""".stripMargin)

  val trustedActionProcessed = SQL(
    """
    | delete from trusted_action_requests where email = {email} and is_signup = {is_signup}
    |""".stripMargin)

  val saveToken = SQL(
    """
    | insert into tokens (token, email, creation, expiration, is_signup)
    | values ({token}, {email}, {creation}, {expiration}, {is_signup})
    |""".stripMargin)
}
