package shared

object Protocol {
  case class ChatMessage(sender: String, message: String)
}
