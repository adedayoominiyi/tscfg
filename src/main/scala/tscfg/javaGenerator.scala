package tscfg

import java.io.PrintWriter
import java.util.Date

import tscfg.generator._
import tscfg.nodes._


object javaGenerator {

  def generate(node: Node, out: PrintWriter)
              (implicit genOpts: GenOpts): GenResult = {

    out.println(s"// generated by tscfg $version on ${new Date()}")
    genOpts.preamble foreach { p =>
      out.println(s"// ${p.replace("\n", "\n// ")}\n")
    }
    out.println(s"package ${genOpts.packageName};\n")

    var results = GenResult()

    gen(node)

    def gen(n: Node, indent: String = ""): Unit = {
      val simple = n.key.simple
      val isRoot = simple == "/"
      val symbol = if (simple == "/") genOpts.className else simple

      val javaId = javaIdentifier(symbol)

      n match {
        case ln: LeafNode  => genForLeaf(ln)
        case n: BranchNode => genForBranch(n)
      }

      def genForLeaf(ln: LeafNode): Unit = {
        out.println(s"${indent}public final ${ln.accessor.`type`} $javaId;")
      }

      def genForBranch(bn: BranchNode): Unit = {
        val className = upperFirst(symbol)

        if (!isRoot) {
          // declare symbol:
          out.println(s"${indent}public final $className $javaId;")
        }

        // <class>
        results = results.copy(classNames = results.classNames + className)

        val classDecl = if (isRoot) "class" else "static class"
        out.println(s"${indent}public $classDecl $className {")

        val orderedNames = bn.keys.toList.sorted

        // generate for members:
        orderedNames foreach { name => gen(bn(name), indent + "  ") }

        // <constructor>
        out.println(s"$indent  public $className($TypesafeConfigClassName c) {")
        orderedNames foreach { name =>
          val javaId = javaIdentifier(name)
          results = results.copy(fieldNames = results.fieldNames + javaId)
          out.print(s"$indent    this.$javaId = ")
          bn(name) match {
            case ln@LeafNode(k, v) =>
              val path = k.simple
              val instance = ln.accessor.instance(path)
              out.println(s"""$instance;""")

            case BranchNode(k, _)  =>
              val className = upperFirst(k.simple)
              out.println(s"""new $className(c != null && c.hasPath("${k.simple}") ? c.getConfig("${k.simple}") : null);""")
          }
        }
        out.println(s"$indent  }")
        // </constructor>

        // toString():
        out.println(s"""$indent  public String toString() { return toString(""); }""")

        // <toString(String i)>
        out.println(s"$indent  public String toString(String i) {")
        val ids = orderedNames map { name =>
          val id = javaIdentifier(name)

          bn(name) match {
            case ln@LeafNode(k, v) =>
              (if(ln.accessor.`type` == "String") {
                if (ln.type_.required || ln.type_.value.isDefined)
                  s"""i+ "$id = " + '"' + this.$id + '"'"""
                else
                  s"""i+ "$id = " + (this.$id == null ? null : '"' + this.$id + '"')"""
              }
              else {
                s"""i+ "$id = " + this.$id"""
              }) + s""" + "\\n""""

            case BranchNode(k, _) =>
              s"""i+ "$id {\\n" + this.$id.toString(i+"    ") +i+ "}\\n""""
          }
        }
        out.println(s"$indent    return ${ids.mkString("\n" +indent + "        +")};")
        out.println(s"$indent  }")
        // </toString(String i)>

        out.println(s"$indent}")
        // </class>
      }
    }
    results
  }

  /**
    * Returns a valid Java identifier from the given symbol:
    *
    * - appends a '_' in case the symbol is a java keyword or special literal ("null", "true", "false");
    * - otherwise, returns the given symbol if already a valid java identifier;
    * - otherwise, prefixes the symbol with '_' if first character is valid but not at first position, and
    *   replaces any character that cannot be part of a java identifier with '_'.
    */
  def javaIdentifier(symbol: String): String = {
    if (javaKeywords.contains(symbol)) symbol + "_"
    else if (isJavaIdentifier(symbol)) symbol else {
      val c0 = symbol.charAt(0)
      val first: String = if (Character.isJavaIdentifierStart(c0)) String.valueOf(c0)
      else if (Character.isJavaIdentifierPart(c0)) "_" + c0 else "_"
      val rest = symbol.substring(1) map { c =>
        if (Character.isJavaIdentifierPart(c)) c else '_'
      }
      first + rest
    }
  }

  def isJavaIdentifier(symbol: String): Boolean = {
    Character.isJavaIdentifierStart(symbol.charAt(0)) &&
      symbol.substring(1).forall(Character.isJavaIdentifierPart)
  }

  private def upperFirst(symbol:String) = symbol.charAt(0).toUpper + symbol.substring(1)

  /**
    * Set of java keywords plus the literals "null", "true", "false".
    * (from Sect 3.9 of the Java Language Spec, Java SE 8 Edition)
    */
  val javaKeywords: List[String] = List(
    "abstract", "continue", "for",        "new",       "switch",
    "assert",   "default",  "if",         "package",   "synchronized",
    "boolean",  "do",       "goto",       "private",   "this",
    "break",    "double",   "implements", "protected", "throw",
    "byte",     "else",     "import",     "public",    "throws",
    "case",     "enum",     "instanceof", "return",    "transient",
    "catch",    "extends",  "int",        "short",     "try",
    "char",     "final",    "interface",  "static",    "void",
    "class",    "finally",  "long",       "strictfp",  "volatile",
    "const",    "float",    "native",     "super",     "while",

    "null",     "true",     "false"
  )

  val TypesafeConfigClassName = classOf[com.typesafe.config.Config].getName
}
