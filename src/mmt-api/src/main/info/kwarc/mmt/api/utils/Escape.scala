package info.kwarc.mmt.api.utils

object Escape {
   /**
    * encode all occurrences of certain characters in a string
    */
   def apply(s: String, escapes: (Char,String)*) : String = {
      var in = s
      var out = ""
      // invariant: s = out + in
      while (in != "") {
         val c = in(0)
         val e = escapes.find(_._1 == c).map(_._2).getOrElse(c.toString)
         in = in.substring(1)
         out = out + e
      }
      out
   }
}

/** escapes all non-legal characters according to some rules */
abstract class Escaping {
  /** the escape character */
  val escapeChar: Char
  /** characters that are not escaped
   *  default: ASCII 32-126 without the plain/custom escapes; override as needed
   */
  def legal(c: Char) = {
    c.toInt >= 32 && c.toInt <= 126 && !escapedChars.contains(c)
  }
  /** characters that are escaped by escape-char + themselves
   *  default: only escapeChar; override as needed
   */
  def usePlainEscape: List[Char] = List(escapeChar)
  private lazy val usePlainEscapeVal = usePlainEscape
  /** characters that are escaped by a custom string
   *  default: n/t/s for newline/tab/space; override as needed
   */
  def useCustomEscape: List[(Char,String)] = List('\n' -> "n", '\t' -> "t")
  private lazy val useCustomEscapeVal = useCustomEscape

  /** characters that are escaped because of an escape rule */
  protected lazy val escapedChars = usePlainEscapeVal ::: useCustomEscapeVal.map(_._1)

  /** check invariant that guarantees invertibility of escaping */
  def check {
    useCustomEscapeVal foreach {case (c,s) =>
      if (s == "")
        throw Error("empty custom escape")
      if (usePlainEscapeVal contains s(0))
        throw Error("clash between encoding of " + c + " and " + s(0))
      if (s.length >= 4 && s.substring(0,4).toLowerCase.forall(c => c.isDigit || "abcdef".contains(c)))
        throw Error("clash between encoding of " + c + " and arbitrary characters")
      useCustomEscapeVal.find(a => a._1 != c && a._2.startsWith(s)) foreach {a =>
        throw Error(s"clash between encoding of '$c' and '${a._1}'")
      }
    }
  }

  def apply(c: Char): String = {
     if (legal(c)) c.toString
     else {
       val e = if (usePlainEscapeVal contains c)
         c.toString
       else
         useCustomEscapeVal.find(_._1 == c).map(_._2).getOrElse {
            c.toInt.formatted("%4h").replace(" ", "0")
         }
       escapeChar + e
     }
  }
  def apply(s: String): String = s flatMap {c => apply(c)}

  case class Error(msg: String) extends Exception(msg)

  def unapply(s: String): String = {
      var escaped = s
      var unescaped = ""
      while (escaped.nonEmpty) {
        val first = escaped(0)
        val rest = escaped.substring(1)
        // the next character to produce, and the number of characters that were consumed
        val (next, length): (Char,Int) = if (first != escapeChar) {
          (first,1)
        } else {
            useCustomEscapeVal.find(cs => rest.startsWith(cs._2)) match {
              case Some((c,s)) => (c,1+s.length)
              case None =>
                 val second = rest(0)
                if (usePlainEscapeVal contains second)
                  (second,2)
                else {
                  try {
                     val hex = rest.substring(0,4)
                   val char = Integer.parseInt(hex, 16).toChar
                   (char, 5)
                  } catch {case _: Exception =>
                    throw Error("illegal escape: " + escaped)
                  }
                }
            }
        }
         unescaped += next
       escaped = escaped.substring(length)
      }
      unescaped
  }
}

object StandardStringEscaping extends Escaping {
  val escapeChar = '\\'
  override def usePlainEscape = super.usePlainEscape ::: List('"')
}

/** escapes a string into one that is a legal file name on Windows and Unix */
object FileNameEscaping extends Escaping {
  val escapeChar = '$'

  override def usePlainEscape = Range(0,26).toList.map(i => (65+i).toChar)
  override def useCustomEscape = super.useCustomEscape ::: List(
      ' '  -> "sp", '/' -> "sl", '?' -> "qm", '<' -> "le", '>' -> "gr", '\\' -> "bs",
      ':' -> "co", '*' -> "st", '|' -> "pi", '"' -> "qu", '^' -> "ca",
      '\'' -> "pr" // not sure why this is escaped; it's legal in file names but MMT archives used to escape it anyway
  )
}

/** escapes a string using the &-escapes of XML for <>&" */
object XMLEscaping extends Escaping {
  val escapeChar = '&'
  override def usePlainEscape = Nil
  override def useCustomEscape = List(
    '>' -> "gt;", '<' -> "lt;", '"' -> "quot;", '&' -> "amp;"//, '\'' -> "#39;"
  )
}

/** escapes a string using the %-escapes for URLs */
object URLEscaping extends Escaping {
  val escapeChar = '%'
  override def usePlainEscape = Nil
  override def useCustomEscape = List(
    ' ' -> "20"
  )
}
