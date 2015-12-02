package example.akkawschat.cli

object TTY {
  val ANSI_ESCAPE = "\u001b["
  val SAVE = ANSI_ESCAPE + "s"
  val RESTORE = ANSI_ESCAPE + "u"
  val ERASE_LINE = ANSI_ESCAPE + "K"
  val GRAY = ANSI_ESCAPE + "0;1m"

  def cursorLeft(chars: Int): String =
    s"$ANSI_ESCAPE${chars}D"

  import sys.process._
  // stty arguments shamelessly stolen from ammonite (https://github.com/lihaoyi/Ammonite/blob/master/terminal/src/main/scala/ammonite/terminal/Utils.scala#L71)
  def noEchoStty() = "stty -F /dev/tty -echo -icanon min 1 -icrnl -inlcr".!!
  def saneStty() = "stty -F /dev/tty sane".!!
}