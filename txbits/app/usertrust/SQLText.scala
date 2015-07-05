// TxBits - An open source Bitcoin and crypto currency exchange
// Copyright (C) 2014-2015  Viktor Stanchev & Kirk Zathey
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

package usertrust

import anorm._

object SQLText {
  val getTrustedActionRequests = SQL(
    """
    | select email, is_signup from trusted_action_requests
    |""".stripMargin)

  val getPendingWithdrawalRequests = SQL(
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
