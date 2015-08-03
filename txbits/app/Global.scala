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

import com.googlecode.jsonrpc4j.JsonRpcHttpClient
import controllers.IAPI.CryptoAddress
import java.net.{ PasswordAuthentication, URL }
import java.net.{ PasswordAuthentication, Authenticator, URL }
import play.api.db.DB
import play.api.i18n.{ MessagesApi, I18nSupport }
import play.api.mvc.Result
import play.api.Play.current
import scala.concurrent.duration._
import models._
import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.libs.json.Json
import play.libs.Akka
import scala.collection.JavaConverters._
import scala.concurrent.Future
import service.txbitsUserService
import usertrust.{ UserTrustModel, UserTrustService }
import wallet.{ WalletModel, Wallet }
import anorm._

package object globals {
  val masterDB = "default"
  val masterDBWallet = "wallet"
  val masterDBTrusted = "trust"

  if (Play.current.configuration.getBoolean("meta.devdb").getOrElse(false)) {
    DB.withConnection(globals.masterDB)({ implicit c =>
      SQL"""
      begin;
      delete from deposits_crypto;
      delete from deposits_other;
      delete from deposits;
      delete from users_passwords;
      delete from users_api_keys;
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
      delete from stats_30_min;
      delete from orders;
      delete from markets;
      delete from withdrawal_limits;
      delete from currencies;
      delete from event_log;
      delete from users;

      select currency_insert('BTC',10);
      select currency_insert('LTC',20);
      select currency_insert('USD',30);
      select currency_insert('CAD',40);

      insert into markets(base,counter, limit_min, position) values ('BTC','USD',0.01,10);
      insert into markets(base,counter, limit_min, position) values ('LTC','USD',0.1,20);
      insert into markets(base,counter, limit_min, position) values ('LTC','BTC',0.1,30);
      insert into markets(base,counter, limit_min, position) values ('USD','CAD',1.00,40);

      insert into dw_fees(currency, method, deposit_constant, deposit_linear, withdraw_constant, withdraw_linear) values ('BTC', 'blockchain', 0.000, 0.000, 0.001, 0.000);
      insert into dw_fees(currency, method, deposit_constant, deposit_linear, withdraw_constant, withdraw_linear) values ('LTC', 'blockchain', 0.000, 0.000, 0.010, 0.000);
      insert into dw_fees(currency, method, deposit_constant, deposit_linear, withdraw_constant, withdraw_linear) values ('USD', 'wire', 0.000, 0.000, 0.000, 0.000);
      insert into dw_fees(currency, method, deposit_constant, deposit_linear, withdraw_constant, withdraw_linear) values ('CAD', 'wire', 0.000, 0.000, 0.000, 0.000);

      insert into trade_fees(linear, one_way) values (0.005, true);

      insert into withdrawal_limits(currency, limit_min, limit_max) values ('BTC', 0.010, 10);
      insert into withdrawal_limits(currency, limit_min, limit_max) values ('LTC', 0.100, 100);
      insert into withdrawal_limits(currency, limit_min, limit_max) values ('USD', 1, 10000);
      insert into withdrawal_limits(currency, limit_min, limit_max) values ('CAD', 1, 10000);

      insert into currencies_crypto(currency) values('BTC');
      insert into currencies_crypto(currency) values('LTC');

      insert into wallets_crypto(currency, last_block_read, balance_min, balance_warn, balance_target, balance_max) values('LTC', 42, 0, 0, 1000, 10000);
      insert into wallets_crypto(currency, last_block_read, balance_min, balance_warn, balance_target, balance_max) values('BTC', 42, 0, 0, 100, 1000);

      insert into users(id, email) values (0, '');
      insert into balances (user_id, currency) select 0, currency from currencies;

      select create_user('me@viktorstanchev.com', 'password', true, null);
      select create_user('a@a.com', 'qwerty123', false, null);

      update balances set balance = 1000 where user_id in (select id from users where email in ('me@viktorstanchev.com', 'a@a.com')) and currency in ('USD', 'CAD');
      commit;
      """.execute()
    })
  }

  val userModel = new UserModel(masterDB)
  val metaModel = new MetaModel(masterDB)
  val engineModel = new EngineModel(masterDB)
  val logModel = new LogModel(masterDB)

  val walletModel = new WalletModel(masterDBWallet)

  val userTrustModel = new UserTrustModel(masterDBTrusted)

  // create UserTrust actor
  val userTrustActor = current.configuration.getBoolean("usertrustservice.enabled").getOrElse(false) match {
    case true => Some(Akka.system.actorOf(UserTrustService.props(userTrustModel)))
    case false => None
  }

  // set up rpc authenticator for wallets
  val rpcAuth = DefaultAuthenticator.getInstance()

  // create wallet actors from config
  //TODO: separate wallet from frontend
  val currencies = List(
    "bitcoin" -> Wallet.CryptoCurrency.BTC,
    "litecoin" -> Wallet.CryptoCurrency.LTC,
    "peercoin" -> Wallet.CryptoCurrency.PPC,
    "primecoin" -> Wallet.CryptoCurrency.XPM)

  val enabledCurrencies = currencies.filter(c =>
    Play.current.configuration.getBoolean("wallet.%s.enabled".format(c._1)).getOrElse(false))

  val wallets = for {
    (currencyName, currency) <- enabledCurrencies
    nodeId <- Play.current.configuration.getIntList("wallet.%s.node.ids".format(currencyName)).get.asScala
  } yield {
    val result = for {
      rpcUrlString <- Play.current.configuration.getString("wallet.%s.node.%s.rpc.url".format(currencyName, nodeId))
      rpcUser <- Play.current.configuration.getString("wallet.%s.node.%s.rpc.user".format(currencyName, nodeId))
      rpcPassword <- Play.current.configuration.getString("wallet.%s.node.%s.rpc.password".format(currencyName, nodeId))
      checkDelay <- Play.current.configuration.getInt("wallet.%s.node.%s.checkDelay".format(currencyName, nodeId))
      checkInterval <- Play.current.configuration.getInt("wallet.%s.node.%s.checkInterval".format(currencyName, nodeId))
      addressDelay <- Play.current.configuration.getInt("wallet.%s.node.%s.addressDelay".format(currencyName, nodeId))
      addressInterval <- Play.current.configuration.getInt("wallet.%s.node.%s.addressInterval".format(currencyName, nodeId))
      addressPool <- Play.current.configuration.getInt("wallet.%s.node.%s.addressPool".format(currencyName, nodeId))
    } yield {
      val backupPath = Play.current.configuration.getString("wallet.%s.node.%s.backupPath".format(currencyName, nodeId)) match {
        case Some(path) if path.startsWith("/") => Some(path)
        case Some(_) =>
          Logger.warn("Backup path specified, but is not absolute (starting with /). Backups are disabled."); None
        case None => None
      }
      val coldAddress = Play.current.configuration.getString("wallet.%s.node.%s.coldAddress".format(currencyName, nodeId)) match {
        case Some(address) if CryptoAddress.isValid(address, currency.toString, Play.current.configuration.getBoolean("fakeexchange").get) => Some(address)
        case Some(_) =>
          Logger.warn("Invalid cold storage address for %s wallet. Cold storage disabled.".format(currency)); None
        case None => None
      }
      val refillEmail = Play.current.configuration.getString("wallet.%s.node.%s.refillEmail".format(currencyName, nodeId)) match {
        case Some(email) if email.contains("@") => Some(email)
        case Some(_) =>
          Logger.warn("Invalid email address for %s wallet. Refill notifications disabled.".format(currency)); None
        case None => None
      }
      val maxTxFee = Play.current.configuration.getString("wallet.%s.node.%s.maxTxFee".format(currencyName, nodeId)) match {
        case Some(fee) =>
          try {
            val f = BigDecimal(fee)
            if (f > 0) {
              f
            } else {
              Logger.warn("Invalid max tx fee for %s wallet.".format(currency))
              BigDecimal(0)
            }
          } catch {
            case _: Throwable =>
              Logger.warn("Invalid max tx fee for %s wallet.".format(currency))
              BigDecimal(0)
          }
        case _ => BigDecimal(0)
      }

      val rpcUrl = new URL(rpcUrlString)
      rpcAuth.register(rpcUrl, new PasswordAuthentication(rpcUser, rpcPassword.toCharArray))
      val params = Wallet.WalletParams(checkDelay.seconds, checkInterval.seconds, addressDelay.seconds, addressInterval.seconds, addressPool, backupPath, coldAddress, refillEmail, maxTxFee)
      Akka.system.actorOf(Wallet.props(new JsonRpcHttpClient(rpcUrl), currency, nodeId, params, walletModel))
    }

    if (result.isEmpty) {
      Logger.warn("One or more required parameters not provided for %s wallet. %s wallet disabled. Required parameters: %s".format(currency, currency, "url, user, password, checkDelay, checkInterval, addressDelay, addressInterval, addressPool"))
    }
    ((currency, nodeId), result)
  }

}

class Global(val messagesApi: MessagesApi) extends GlobalSettings with I18nSupport {

  override def onError(request: RequestHeader, ex: Throwable): Future[Result] = {
    implicit val r = request
    request.contentType.map {
      case "application/json" =>
        Future.successful(InternalServerError(Json.toJson(Map("error" -> ("Internal Error: " + ex.getMessage)))))
      case _ =>

        Future.successful(InternalServerError(views.html.meta.error(ex)))
    }.getOrElse(Future.successful(InternalServerError(views.html.meta.error(ex))))
  }

  override def onHandlerNotFound(request: RequestHeader) = {
    implicit val r = request
    request.contentType.map {
      case "application/json" =>
        Future.successful(NotFound(Json.toJson(Map("error" -> ("Not found: " + request.path)))))
      case _ =>
        Future.successful(NotFound(views.html.meta.notFound(request.path)))
    }.getOrElse(Future.successful(NotFound(views.html.meta.notFound(request.path))))
  }

  override def onBadRequest(request: RequestHeader, error: String) = {
    implicit val r = request
    request.contentType.map {
      case "application/json" =>
        Future.successful(BadRequest(Json.toJson(Map("error" -> ("Bad Request: " + error)))))
      case _ =>
        Future.successful(BadRequest(views.html.meta.badRequest(error)))
    }.getOrElse(Future.successful(BadRequest(views.html.meta.badRequest(error))))
  }

  override def onStart(app: Application) {
    Logger.info("Application has started")
    // This is a somewhat hacky way to exit after statup so that we can apply database changes without stating the app
    if (Play.current.configuration.getBoolean("meta.exitimmediately").getOrElse(false)) {
      Logger.warn("Exiting because of meta.exitimmediately config set to true.")
      System.exit(0)
    }
    txbitsUserService.onStart()
  }

  override def onStop(app: Application) {
    Logger.info("Application shutdown...")
    txbitsUserService.onStop()
  }
}
