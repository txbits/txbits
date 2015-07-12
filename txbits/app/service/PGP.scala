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

package service

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp._

import org.bouncycastle.openpgp.operator.bc._
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyConverter
import javax.crypto.Cipher
import org.bouncycastle.util.encoders.Base64Encoder
import org.bouncycastle.bcpg.{ SymmetricKeyAlgorithmTags, ArmoredOutputStream }
import org.bouncycastle.openpgp.operator.PGPDataEncryptorBuilder
import java.util.Date

//import Decoder.BASE64Encoder
import java.io._
import java.security.{ SecureRandom, PublicKey, Security }

object PGP {
  def simple_encrypt(pubStr: String, message: String) = {
    val pgpkey = parsePublicKey(pubStr)
    encrypt(pgpkey.get._1, message)
  }

  def encrypt(pgpkey: PGPPublicKey, message: String) = {
    val bytes = message.getBytes("UTF-8")
    val out = new ByteArrayOutputStream()
    val armoredOutputStream = new ArmoredOutputStream(out)
    val encryptGen = new PGPEncryptedDataGenerator(new BcPGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256).setWithIntegrityPacket(true).setSecureRandom(new SecureRandom()))
    encryptGen.addMethod(new BcPublicKeyKeyEncryptionMethodGenerator(pgpkey))
    val encryptedOut = encryptGen.open(armoredOutputStream, bytes.length)
    val literalGen = new PGPLiteralDataGenerator()
    val literalOut = literalGen.open(encryptedOut, PGPLiteralDataGenerator.UTF8, PGPLiteralData.CONSOLE, bytes.length, new Date())
    literalOut.write(bytes)
    literalOut.close()
    encryptedOut.close()
    armoredOutputStream.close()
    out.toString("UTF-8")
  }

  def parsePublicKey(pubStr: String): Option[(PGPPublicKey, String)] = {
    try {
      val pubIn: InputStream = new ByteArrayInputStream(pubStr.getBytes("UTF-8"))
      val pgpPub: PGPPublicKeyRingCollection = new PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(pubIn), new BcKeyFingerprintCalculator)

      val keyRingIter = pgpPub.getKeyRings
      while (keyRingIter.hasNext) {
        val keyRing = keyRingIter.next().asInstanceOf[PGPPublicKeyRing]

        val keyIter = keyRing.getPublicKeys
        while (keyIter.hasNext) {
          val key = keyIter.next().asInstanceOf[PGPPublicKey]
          val out = new ByteArrayOutputStream()
          val armored = new ArmoredOutputStream(out)
          key.encode(armored)
          out.close()
          armored.close()
          val keyString = out.toString("UTF-8")

          if (key.isEncryptionKey) {
            return Some(key, keyString)
          }
        }
      }
      None
    } catch {
      case ex: Throwable =>
        None
    }
  }
}
