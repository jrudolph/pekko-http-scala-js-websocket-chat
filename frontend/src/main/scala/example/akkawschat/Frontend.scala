package example.akkawschat

import org.scalajs.dom.raw._

import scala.scalajs.js
import org.scalajs.dom

object Frontend extends js.JSApp {
  val joinButton = dom.document.getElementById("join").asInstanceOf[HTMLButtonElement]
  val sendButton = dom.document.getElementById("send").asInstanceOf[HTMLButtonElement]

  def main(): Unit = {
    val nameField = dom.document.getElementById("name").asInstanceOf[HTMLInputElement]
    joinButton.onclick = { (event: MouseEvent) ⇒
      joinChat(nameField.value)
      event.preventDefault()
    }
  }

  def joinChat(name: String): Unit = {
    joinButton.disabled = true
    val playground = dom.document.getElementById("playground")
    playground.innerHTML = s"Trying to join chat as '$name'..."
    val chat = new WebSocket(s"ws://localhost:8080/chat?name=$name")
    chat.onopen = { (event: Event) ⇒
      playground.insertBefore(p("Chat connection was successful!"), playground.firstChild)
      sendButton.disabled = false

      val messageField = dom.document.getElementById("message").asInstanceOf[HTMLInputElement]
      sendButton.onclick = { (event: Event) ⇒
        chat.send(messageField.value)
        messageField.value = ""
        event.preventDefault()
      }

      event
    }
    chat.onerror = { (event: ErrorEvent) ⇒
      playground.insertBefore(p(s"Failed: code: ${event.colno}"), playground.firstChild)
      joinButton.disabled = false
      sendButton.disabled = true
    }
    chat.onmessage = { (event: MessageEvent) ⇒
      playground.insertBefore(p(event.data.toString), playground.firstChild)
    }
    chat.onclose = { (event: Event) ⇒
      playground.insertBefore(p("Connection to chat lost. You can try to rejoin manually."), playground.firstChild)
      joinButton.disabled = false
      sendButton.disabled = true
    }
  }

  def p(msg: String) = {
    val paragraph = dom.document.createElement("p")
    paragraph.innerHTML = msg
    paragraph
  }
}