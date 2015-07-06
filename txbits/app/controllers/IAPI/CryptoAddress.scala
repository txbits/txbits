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

package controllers.IAPI

import org.bitcoinj.core.Address
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.NetworkParameters._

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