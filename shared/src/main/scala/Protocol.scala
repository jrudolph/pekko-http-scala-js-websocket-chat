package shared

object Protocol {
  sealed trait Message
  case class ChatMessage(sender: String, message: String) extends Message
  case class Joined(member: String, allMembers: Seq[String]) extends Message
  case class Left(member: String, allMembers: Seq[String]) extends Message
}
