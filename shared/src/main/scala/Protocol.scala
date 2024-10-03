package shared

object Protocol {
  sealed trait Message
  case class ChatMessage(sender: String, message: String) extends Message
  case class Joined(member: String, allMembers: Seq[String]) extends Message
  case class Left(member: String, allMembers: Seq[String]) extends Message

  import upickle.default._
  implicit val messageRW: ReadWriter[Message] = {
    implicit val cmRW: ReadWriter[ChatMessage] = macroRW[ChatMessage]
    implicit val jRW: ReadWriter[Joined] = macroRW[Joined]
    implicit val lRW: ReadWriter[Left] = macroRW[Left]

    macroRW[Message]
  }
}
