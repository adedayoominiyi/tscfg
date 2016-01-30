package tscfg

import java.io.PrintWriter
import java.util.Date

import tscfg.generator._

object scalaGenerator {

  def generate(node: Node, out: PrintWriter)
              (implicit genOpts: GenOpts): Unit = {

    out.println(s"// generated by tscfg $version on ${new Date()}")
    genOpts.preamble foreach { p =>
      out.println(s"// ${p.replace("\n", "\n// ")}\n")
    }
    out.println(s"package ${genOpts.packageName}\n")
    out.println(s"import com.typesafe.config.Config\n")

    gen(node, out)
  }

  def gen(n: Node, out: PrintWriter)
         (implicit genOpts: GenOpts): Unit = {

    val simple = n.key.simple
    val symbol = if (simple == "/") genOpts.className else simple

    val scalaId = scalaIdentifier(symbol)

    n match {
      case ln: LeafNode  => genForLeaf(ln)
      case n: BranchNode => genForBranch(n)
    }

    def genForLeaf(ln: LeafNode): Unit = {
      out.println(s"  $scalaId: ${ln.accessor.`type`}")
    }

    def genForBranch(bn: BranchNode): Unit = {
      val className = upperFirst(symbol)

      val orderedNames = bn.map.keys.toList.sorted

      // <class>
      out.println(s"case class $className(")
      var comma = ""
      orderedNames foreach { name =>
        out.print(comma)
        out.print(s"  ${scalaIdentifier(name)} : ")  // note, space before : for proper tokenization
        bn.map(name) match {
          case ln@LeafNode(k, v) =>
            out.print(s"""${ln.accessor.`type`}""")

          case BranchNode(k)  =>
            val className = upperFirst(k.simple)
            out.print(s"""$className""")
        }
        comma = ",\n"
      }
      out.println("\n)")
      // </class>

      // recurse to the subnodes:
      orderedNames foreach { name =>
        bn.map(name) match {
          case sbn@BranchNode(k) => gen(sbn, out)
          case _ =>
        }
      }

      // <constructor>
      // <object>
      out.println(s"object $className {")
      out.println(s"  def apply(c: Config): $className = {")
      out.println(s"    $className(")

      comma = ""
      orderedNames foreach { name =>
        out.print(comma)
        bn.map(name) match {
          case ln@LeafNode(k, v) =>
            out.print(s"""      ${ln.accessor.instance(k.simple)}""")

          case BranchNode(k)  =>
            val className = upperFirst(k.simple)
            out.print(s"""      $className(c.getConfig("${k.simple}"))""")
        }
        comma = ",\n"
      }
      out.println("\n    )")
      out.println(s"  }")
      out.println(s"}")
      // </object>
      // </constructor>
    }
  }

  /**
    * Returns a valid scala identifier from the given symbol:
    *
    * - encloses the symbol in backticks if the symbol is a scala reserved word;
    * - otherwise, returns symbol if it is a valid java identifier
    * - otherwise, returns `javaGenerator.javaIdentifier(symbol)`
    */
  def scalaIdentifier(symbol: String): String = {
    if (scalaReservedWords.contains(symbol)) "`" + symbol + "`"
    else if (javaGenerator.isJavaIdentifier(symbol)) symbol
    else javaGenerator.javaIdentifier(symbol)
  }

  private def upperFirst(symbol:String) = symbol.charAt(0).toUpper + symbol.substring(1)

  /**
    * from Sect 1.1 of the Scala Language Spec, v2.9
    */
  val scalaReservedWords: List[String] = List(
    "abstract", "case",     "catch",   "class",   "def",
    "do",       "else",     "extends", "false",   "final",
    "finally",  "for",      "forSome", "if",      "implicit",
    "import",   "lazy",     "match",   "new",     "null",
    "object",   "override", "package", "private", "protected",
    "return",   "sealed",   "super",   "this",    "throw",
    "trait",    "try",      "true",    "type",    "val",
    "var",      "while",    "with",    "yield"
  )
}
