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

  // touch this to ensure that the TTY isn't left in a broken state
  lazy val ensureShutdownHook: Unit =
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        saneStty()
      }
    })

  // stty arguments shamelessly stolen from ammonite (https://github.com/lihaoyi/Ammonite/blob/master/terminal/src/main/scala/ammonite/terminal/Utils.scala#L71)
  private val pathedStty = if (new java.io.File("/bin/stty").exists()) "/bin/stty" else "stty"

  private def sttyCmd(s: String) = {
    import sys.process._
    Seq("sh", "-c", s"$pathedStty $s < /dev/tty"): ProcessBuilder
  }

  private def stty(s: String) =
    sttyCmd(s).!!

  /*
   * Executes a stty command for which failure is expected, hence the return
   * status can be non-null and errors are ignored.
   * This is appropriate for `stty dsusp undef`, since it's unsupported on Linux
   * (http://man7.org/linux/man-pages/man3/termios.3.html).
   */
  private def sttyFailTolerant(s: String) =
    sttyCmd(s ++ " 2> /dev/null").!

  def noEchoStty() = {
    ensureShutdownHook
    stty("-icanon min 1 -icrnl -inlcr -ixon")
    sttyFailTolerant("dsusp undef")
    stty("-echo")
    stty("intr undef")
  }
  def saneStty() = "stty -F /dev/tty sane".!!
}