package usertrust

import anorm._

object SQLText {
  val getTrustedActionRequests = SQL(
    """
    | select email, is_signup from trusted_action_requests
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
