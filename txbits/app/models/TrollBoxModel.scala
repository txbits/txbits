package models

import scala.collection.mutable.MutableList
import scala.collection.mutable.HashMap
import play.api.Play
import play.api.db.DB
import play.api.Play.current
import play.api.libs.json.Json

import securesocial.core.{ Token, SocialUser }

case class TrollBoxMessage(
    var body: String,
    var user: String,
    var userID: Long,
    var messageID: Int = 0,
    var messageDate: Long = System.currentTimeMillis / 1000,
    var upvoteCount: Int = 0) {
  var upvotes: HashMap[String, Boolean] = HashMap()

  def updateUpvoteCount() = {
    upvoteCount = upvotes.values.count(_ == true);
  }
}

object TrollBoxMessage {
  implicit val writes = Json.writes[TrollBoxMessage]
  implicit val format = Json.format[TrollBoxMessage]
}

class TrollBoxModel(val db: String = "default") {
  def buildMessage(body: String, user: SocialUser): TrollBoxMessage = {
    TrollBoxModel._NextID += 1;
    TrollBoxMessage(body, user.username, user.id, TrollBoxModel._NextID)
  }

  def pushMessage(trollBoxMessage: TrollBoxMessage) = {
    if (trollBoxMessage.body.length > TrollBoxModel._MaxMessageLength) {
      throw new IllegalArgumentException("Trollbox message exceeds maximum message length.");
    }

    if (TrollBoxModel._Messages.size >= TrollBoxModel._MaxSavedMessages) {
      TrollBoxModel._Messages = TrollBoxModel._Messages.drop(1)
    }

    TrollBoxModel._Messages += trollBoxMessage
  }

  def messages(): List[TrollBoxMessage] = {
    TrollBoxModel._Messages.toList
  }

  def upvote(messageID: Int, email: String) = {
    var message = findMessageByID(messageID)
    if (message.upvotes.contains(email)) {
      message.upvotes += (email -> !message.upvotes(email))
    } else {
      message.upvotes += (email -> true)
    }

    message.updateUpvoteCount()
  }

  def findMessageByID(messageID: Int): TrollBoxMessage = {
    var message: TrollBoxMessage = null;

    for (currentMessage <- TrollBoxModel._Messages) {
      if (currentMessage.messageID == messageID) {
        message = currentMessage;
      }
    }

    message;
  }
}

object TrollBoxModel {
  private var _Messages: MutableList[TrollBoxMessage] = new MutableList()
  var _MaxSavedMessages: Int = 64
  var _MaxMessageLength: Int = 256
  var _NextID: Int = 0
}
