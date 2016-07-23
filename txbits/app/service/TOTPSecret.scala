/**
 * Copyright (c) 2011 IETF Trust and the persons identified as
 * authors of the code. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, is permitted pursuant to, and subject to the license
 * terms contained in, the Simplified BSD License set forth in Section
 * 4.c of the IETF Trust's Legal Provisions Relating to IETF Documents
 * (http://trustee.ietf.org/license-info).
 *
 * @author Johan Rydell, PortWise, Inc.
 * @author Mark Lister.
 */

package service

import java.security.SecureRandom
import scala.math.BigInt
import scala.util.Random

/**
 * The secret is represented as a BigInt.
 *  BigInts make it easy to convert between Hex and Base32.
 *  Method toByteArray lets you interface with standard crypto.
 *  Base32 is the defacto format used by Google authenticator.  The OAuthTool uses hex :(
 */

class TOTPSecret(private val underlying: BigInt) {

  private val B32 = ('A' to 'Z') ++ ('2' to '7')

  /**
   * Convert this secret into its Base 32 string representation.
   */
  def toBase32: String = new String(underlying.toString(32).toCharArray.map(_.asDigit).map(B32(_)))

  /**
   * java.math.BigInteger.toByteArray adds a zero byte to the head of the byte array when the
   * first bit is a '1'.  The first bit is a sign bit.  We strip this byte to match the RI
   * behaviour.
   */
  def toByteArray: Array[Byte] = {
    val b = underlying.toByteArray
    if (b(0) == 0) b.tail else b
  }
  def toString(n: Int) = underlying.toString(n)
}

object TOTPSecret {

  private val B32 = ('A' to 'Z') ++ ('2' to '7')
  /**
   * Create a secret from a Base 32 String
   *  No error checking.
   *  Leading 'A's are ignored see the discussion at https://github.com/marklister/scala-totp-auth/issues/3
   */
  def apply(base32: String): TOTPSecret = {
    new TOTPSecret(base32.toUpperCase.map(B32.indexOf(_)).foldLeft(0: BigInt)((a, b) => a * 32 + b))
  }

  /**
   * Create a new TOTP secret key of b32Digits * 5 bits in length using a custom random source
   * The first digit will not be an 'A' see the discussion at https://github.com/marklister/scala-totp-auth/issues/2
   */
  def apply(b32Digits: Int, r: Random): TOTPSecret =
    new TOTPSecret((2 to b32Digits).foldLeft(BigInt(r.nextInt(31)) + 1: BigInt)((a, b) => a * 32 + r.nextInt(32)))

  /**
   * Create a new TOTP secret key of b32Digits * 5 bits in length using the default random source
   * The first digit will not be an 'A' see the discussion at https://github.com/marklister/scala-totp-auth/issues/2
   */
  def apply(b32Digits: Int): TOTPSecret = apply(b32Digits, new Random(new SecureRandom))

  /**
   * Create a new TOTP secret key 80 bits (16 Base 32 char) long using the default random source
   * The first digit will not be an 'A' see the discussion at https://github.com/marklister/scala-totp-auth/issues/2
   */
  def apply(): TOTPSecret = apply(16)

  /**
   * Create a new TOTP secret key from an Array[Byte]
   * Leading zeros will be stripped off when the array is used to calculate the totp.
   */
  def apply(bytes: Array[Byte]) =
    new TOTPSecret(BigInt(bytes))

  /**
   * Create a secret from a hex String
   * No error checking.
   * Leading '0's are ignored see the discussion at https://github.com/marklister/scala-totp-auth/issues/3
   */
  def fromHex(hex: String): TOTPSecret = {
    new TOTPSecret(BigInt(hex, 16))
  }
}
