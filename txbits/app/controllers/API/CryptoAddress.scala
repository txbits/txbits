// Copyright (c) 2014 Viktor Stanchev & Kirk Zathey.
// This file is licensed under the Affero General Public License version 3 or later,
// see the accompanying file COPYING or http://www.gnu.org/licenses/agpl.html.

package controllers.API

import com.google.bitcoin.core.Address
import com.google.bitcoin.core.NetworkParameters
import com.google.bitcoin.core.NetworkParameters._

object CryptoAddress {
  private val BitcoinTestnet = new NetworkParameters {
    id = ID_TESTNET
    port = 18333
    addressHeader = 111
    p2shHeader = 196
    acceptableAddressCodes = Array[Int](addressHeader, p2shHeader)

    val getPaymentProtocolId: String = PAYMENT_PROTOCOL_ID_TESTNET
  }

  private val Bitcoin = new NetworkParameters {
    id = ID_MAINNET
    port = 8333
    addressHeader = 0
    p2shHeader = 5
    acceptableAddressCodes = Array[Int](addressHeader, p2shHeader)

    val getPaymentProtocolId: String = PAYMENT_PROTOCOL_ID_MAINNET
  }

  private val LitecoinTestnet = new NetworkParameters {
    id = ID_TESTNET
    port = 19333
    addressHeader = 111
    p2shHeader = 196
    acceptableAddressCodes = Array[Int](addressHeader, p2shHeader)

    val getPaymentProtocolId: String = PAYMENT_PROTOCOL_ID_TESTNET
  }

  private val Litecoin = new NetworkParameters {
    id = ID_MAINNET
    port = 9333
    addressHeader = 48
    p2shHeader = 5
    acceptableAddressCodes = Array[Int](addressHeader, p2shHeader)

    val getPaymentProtocolId: String = PAYMENT_PROTOCOL_ID_MAINNET
  }

  private val PeercoinTestnet = new NetworkParameters {
    id = ID_TESTNET
    port = 9903
    addressHeader = 111
    p2shHeader = 196
    acceptableAddressCodes = Array[Int](addressHeader, p2shHeader)

    val getPaymentProtocolId: String = PAYMENT_PROTOCOL_ID_TESTNET
  }

  private val Peercoin = new NetworkParameters {
    id = ID_MAINNET
    port = 9901
    addressHeader = 55
    p2shHeader = 117
    acceptableAddressCodes = Array[Int](addressHeader, p2shHeader)

    val getPaymentProtocolId: String = PAYMENT_PROTOCOL_ID_MAINNET
  }

  private val PrimecoinTestnet = new NetworkParameters {
    id = ID_TESTNET
    port = 9913
    addressHeader = 111
    p2shHeader = 196
    acceptableAddressCodes = Array[Int](addressHeader, p2shHeader)

    val getPaymentProtocolId: String = PAYMENT_PROTOCOL_ID_TESTNET
  }

  private val Primecoin = new NetworkParameters {
    id = ID_MAINNET
    port = 9911
    addressHeader = 23
    p2shHeader = 83
    acceptableAddressCodes = Array[Int](addressHeader, p2shHeader)

    val getPaymentProtocolId: String = PAYMENT_PROTOCOL_ID_MAINNET
  }

  def isValid(address: String, currency: String, testnet: Boolean): Boolean = {
    val network = currency match {
      case "BTC" if testnet => BitcoinTestnet
      case "BTC" => Bitcoin
      case "LTC" if testnet => LitecoinTestnet
      case "LTC" => Litecoin
      case "PPC" if testnet => PeercoinTestnet
      case "PPC" => Peercoin
      case "XPM" if testnet => PrimecoinTestnet
      case "XPM" => Primecoin
      case _ =>
        return false
    }
    try {
      new Address(network, address)
    } catch {
      case _: Exception =>
        return false
    }
    true
  }
}