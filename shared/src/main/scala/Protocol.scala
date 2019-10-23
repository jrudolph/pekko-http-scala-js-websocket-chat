package shared

object Protocol {
  sealed trait Message
  case class ChatMessage(sender: String, message: String) extends Message
  case class Joined(member: String, allMembers: Seq[String]) extends Message
  case class Left(member: String, allMembers: Seq[String]) extends Message

  import upickle.default._
  implicit val messageRW: ReadWriter[Message] = {
    implicit val cmRW = macroRW[ChatMessage]
    implicit val jRW = macroRW[Joined]
    implicit val lRW = macroRW[Left]

    macroRW[Message]
  }
}
