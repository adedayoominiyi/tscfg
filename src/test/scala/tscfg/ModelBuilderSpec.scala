package tscfg

import org.specs2.mutable.Specification
import model.durations._
import scalax.collection.Graph
import scalax.collection.GraphEdge.DiEdge
import tscfg.Struct.{MemberStruct, SharedObjectStruct}
import tscfg.buildWarnings.{DefaultListElemWarning, MultElemListWarning, OptListElemWarning}
import tscfg.exceptions.{LinearizationException, ObjectDefinitionException}
import tscfg.model._

import scala.collection.mutable


class ModelBuilderSpec extends Specification {

  def build(source: String, showOutput: Boolean = false): ModelBuildResult = {

    if (showOutput)
      println("\nsource:\n  |" + source.replaceAll("\n", "\n  |"))

    val result = ModelBuilder(source)
    val objectType = result.objectType

    if (showOutput) {
      println("\nobjectType: " + objectType)
      println("\nobjectType:\n  |" + model.util.format(objectType).replaceAll("\n", "\n  |"))
    }

    result
  }

  private def verify(objType: ObjectType,
                     memberName: String,
                     t: Type,
                     optional: Boolean = false,
                     default: Option[String] = None,
                     comments: Option[String] = None
                    ) = {
    val at = objType.members(memberName)
    at.t === t
    at.optional === optional
    at.default === default
    at.comments === comments
  }

  "with empty input" should {
    val result = build("")
    "build empty ObjectType" in {
      result.objectType === ObjectType()
    }
  }

  "with empty list" should {
    def a = build(
      """
        |my_list: [ ]
      """.stripMargin)

    "throw IllegalArgumentException" in {
      a must throwA[IllegalArgumentException]
    }
  }

  "with list with multiple elements" should {
    val result = build("my_list: [ true, false ]")

    "generate warning" in {
      val warns = result.warnings.filter(_.isInstanceOf[MultElemListWarning])
      warns.map(_.source) must contain("[true,false]")
    }
  }

  "with list element indicating optional" should {
    val result = build("""my_list: [ "string?" ]""")

    "generate warning" in {
      val warns = result.warnings.filter(_.isInstanceOf[OptListElemWarning])
      warns.map(_.source) must contain("string?")
    }
  }

  "with list element indicating a default value" should {
    val result = build(
      """
        |my_list: [ "double | 3.14" ]
      """.stripMargin)

    "generate warning" in {
      val warns = result.warnings.filter(_.isInstanceOf[DefaultListElemWarning]).
        asInstanceOf[List[DefaultListElemWarning]]
      warns.map(_.default) must contain("3.14")
    }
  }

  "with list with literal int" should {
    val result = build(
      """
        |my_list: [ 99999999 ]
      """.stripMargin)

    "translate into ListType(INTEGER)" in {
      result.objectType.members("my_list").t === ListType(INTEGER)
    }
  }

  "with list with literal long" should {
    val result = build(
      """
        |my_list: [ 99999999999 ]
      """.stripMargin)

    "translate into ListType(LONG)" in {
      result.objectType.members("my_list").t === ListType(LONG)
    }
  }

  "with list with literal double" should {
    val result = build(
      """
        |my_list: [ 3.14 ]
      """.stripMargin)

    "translate into ListType(DOUBLE)" in {
      result.objectType.members("my_list").t === ListType(DOUBLE)
    }
  }

  "with list with literal boolean" should {
    val result = build(
      """
        |my_list: [ false ]
      """.stripMargin)

    "translate into ListType(BOOLEAN)" in {
      result.objectType.members("my_list").t === ListType(BOOLEAN)
    }
  }

  "with literal integer" should {
    val result = build(
      """
        |optInt: 21
      """.stripMargin)

    "translate into ListType(BOOLEAN)" in {
      val at = result.objectType.members("optInt")
      at.t === INTEGER
      at.optional must beTrue
      at.default must beSome("21")
    }
  }

  // TODO
  "with enum" should {
    val result = build(
      """#@define enum
        |FruitType = [apple, banana, pineapple]
      """.stripMargin)

    "translate member into EnumObjectType" in {
      val at = result.objectType.members("FruitType")
      // println(s"FruitType = $at")
      true
    }
  }

  "with literal duration (issue 22)" should {
    val result = build(
      """
        |idleTimeout = 75 seconds
      """.stripMargin)

    "translate into DURATION(ms) with given default" in {
      val at = result.objectType.members("idleTimeout")
      at.t === DURATION(ms)
      at.optional must beTrue
      at.default must beSome("75 seconds")
    }
  }

  "with good input" should {
    val result = build(
      """
        |foo {
        |  reqStr        = string
        |  reqInt        = integer
        |  reqLong       = long
        |  reqDouble     = double
        |  reqBoolean    = boolean
        |  reqDuration   = duration
        |  duration_ns   = "duration : ns"
        |  duration_µs   = "duration : us"
        |  duration_ms   = "duration : ms"
        |  duration_se   = "duration : s"
        |  duration_mi   = "duration : m"
        |  duration_hr   = "duration : h"
        |  duration_dy   = "duration : d"
        |  optStr        = "string?"
        |  optInt        = "int?"
        |  optLong       = "long?"
        |  optDouble     = "double?"
        |  optBoolean    = "boolean?"
        |  optDuration   = "duration?"
        |  dflStr        = "string   | hi"
        |  dflInt        = "int      | 3"
        |  dflLong       = "long     | 999999999"
        |  dflDouble     = "double   | 3.14"
        |  dflBoolean    = "boolean  | false"
        |  dflDuration   = "duration | 21d"
        |  listStr       = [ string ]
        |  listInt       = [ integer ]
        |  listLong      = [ long ]
        |  listDouble    = [ double ]
        |  listBoolean   = [ boolean ]
        |  listDuration  = [ duration ]
        |  listDuration_se  = [ "duration : second" ]
        |}
      """.stripMargin)

    val objType = result.objectType

    "build expected objType" in {
      objType.members.keySet === Set("foo")
      val foo = objType.members("foo")
      foo.optional === false
      foo.default must beNone
      foo.comments must beNone
      foo.t must beAnInstanceOf[ObjectType]
      val fooObj = foo.t.asInstanceOf[ObjectType]
      fooObj.members.keySet === Set(
        "reqStr",
        "reqInt",
        "reqLong",
        "reqDouble",
        "reqBoolean",
        "reqDuration",
        "duration_ns",
        "duration_µs",
        "duration_ms",
        "duration_se",
        "duration_mi",
        "duration_hr",
        "duration_dy",
        "optStr",
        "optInt",
        "optLong",
        "optDouble",
        "optBoolean",
        "optDuration",
        "dflStr",
        "dflInt",
        "dflLong",
        "dflDouble",
        "dflBoolean",
        "dflDuration",
        "listStr",
        "listInt",
        "listLong",
        "listBoolean",
        "listDouble",
        "listDuration",
        "listDuration_se"
      )
      verify(fooObj, "reqStr",      STRING)
      verify(fooObj, "reqInt",      INTEGER)
      verify(fooObj, "reqLong",     LONG)
      verify(fooObj, "reqDouble",   DOUBLE)
      verify(fooObj, "reqBoolean",  BOOLEAN)
      verify(fooObj, "reqDuration", DURATION(ms))
      verify(fooObj, "duration_ns", DURATION(ns))
      verify(fooObj, "duration_µs", DURATION(us))
      verify(fooObj, "duration_ms", DURATION(ms))
      verify(fooObj, "duration_se", DURATION(second))
      verify(fooObj, "duration_mi", DURATION(minute))
      verify(fooObj, "duration_hr", DURATION(hour))
      verify(fooObj, "duration_dy", DURATION(day ))
      verify(fooObj, "optStr" ,     STRING,   optional = true)
      verify(fooObj, "optInt" ,     INTEGER,  optional = true)
      verify(fooObj, "optLong",     LONG,     optional = true)
      verify(fooObj, "optDouble",   DOUBLE,   optional = true)
      verify(fooObj, "optBoolean",  BOOLEAN,  optional = true)
      verify(fooObj, "optDuration", DURATION(ms), optional = true)
      verify(fooObj, "dflStr" ,     STRING,   optional = true, default = Some("hi"))
      verify(fooObj, "dflInt" ,     INTEGER,  optional = true, default = Some("3"))
      verify(fooObj, "dflLong",     LONG,     optional = true, default = Some("999999999"))
      verify(fooObj, "dflDouble",   DOUBLE,   optional = true, default = Some("3.14"))
      verify(fooObj, "dflBoolean",  BOOLEAN,  optional = true, default = Some("false"))
      verify(fooObj, "dflDuration", DURATION(ms), optional = true, default = Some("21d"))
      verify(fooObj, "listStr",      ListType(STRING))
      verify(fooObj, "listInt",      ListType(INTEGER))
      verify(fooObj, "listLong",     ListType(LONG))
      verify(fooObj, "listDouble",   ListType(DOUBLE))
      verify(fooObj, "listBoolean",  ListType(BOOLEAN))
      verify(fooObj, "listDuration", ListType(DURATION(ms)))
      verify(fooObj, "listDuration_se", ListType(DURATION(second)))
    }
  }

  "Considering the inheritance structure" should {
    /* Valid inheritance hierarchy:
     * A
     * |- B
     *    |- D
     *    |- E
     * |- C
     */
    val a = SharedObjectStruct(name = "a", abstractObject = true, maybeParentId = None)
    val b = SharedObjectStruct(name = "b", abstractObject = true, maybeParentId = Some("a"))
    val c = SharedObjectStruct(name = "c", abstractObject = false, maybeParentId = Some("a"))
    val d = SharedObjectStruct(name = "d", abstractObject = false, maybeParentId = Some("b"))
    val e = SharedObjectStruct(name = "e", abstractObject = false, maybeParentId = Some("b"))
    val nodes = Set(a, b, c, d, e)
    val edges = Set(
      DiEdge(a, b),
      DiEdge(a, c),
      DiEdge(b, d),
      DiEdge(b, e)
    )
    val modelBuilder = new ModelBuilder(false)

    /*
    val expectedLinearization = Vector(a, b, c, d, e)
    // (67) Only this one? Unless also including some form of lexicographic ordering on top
    // of the breadth-first traversal (and noting that I'm not actually sure about the concrete
    // linearization algorithm that's being used), other possible linearizations would include:
    //    Vector(a, c, b, d, e), Vector(a, b, c, e, d), Vector(a, c, b, e, d).
    // So, assertions for equality against only this vector would be incorrect: they may pass
    // sometimes or fail other times. In fact, I saw that happening under 2.13 (although the
    // version is irrelevant.  So, I'm commenting out those for now.
    // Probably a more appropriate representation of the linearization output would be a
    // Vector (or List) of Sets, so one could indicate the expected output as, e.g.,
    //      val expectedLinearization = Vector(a, Set(b, c), Set(d, e))
    */

/*
    "traverse a sub graph correctly" in {
      val graph = Graph.from(nodes, edges)

      /* Get the actual order */
      val actual = modelBuilder.traverseSubGraph(graph, a)

      actual mustEqual expectedLinearization
    }
*/

    "correctly builds the inheritance graph from shared object structs" in {
      val expected = Graph.from(nodes, edges)

      val actual = modelBuilder.buildInheritanceGraph(nodes.toVector)

      actual mustEqual expected
    }

    "returns an empty Vector, when shared objects to linearize are empty as well" in {
      val actual = modelBuilder.linearizeSharedObjects(Vector.empty[SharedObjectStruct])

      actual mustEqual Vector.empty[SharedObjectStruct]
    }

/*
    "returns correct linearization on valid, simple inheritance hierarchy" in {
      val actual = modelBuilder.linearizeSharedObjects(nodes.toVector)

      actual mustEqual expectedLinearization
    }
*/

/*
    "returns correct linearization on valid, multiple inheritance hierarchy" in {
      /*
       * Second valid hierarchy:
       *
       * F
       * |- G
       * |- H
       * |- I
       */
      val f = SharedObjectStruct(name = "f", abstractObject = true, maybeParentId = None)
      val g = SharedObjectStruct(name = "g", abstractObject = false, maybeParentId = Some("f"))
      val h = SharedObjectStruct(name = "h", abstractObject = false, maybeParentId = Some("f"))
      val i = SharedObjectStruct(name = "i", abstractObject = false, maybeParentId = Some("f"))

      val structs = nodes.toVector ++ Vector(f, g, h, i)

      val actual = modelBuilder.linearizeSharedObjects(structs)

      actual mustEqual Vector(f, g, h, i) ++ expectedLinearization
    }
*/

    "throws an Exception, when the hierarchy has a cycle" in {
      val j = SharedObjectStruct(name = "j", abstractObject = true, maybeParentId = Some("l"))
      val k = SharedObjectStruct(name = "k", abstractObject = true, maybeParentId = Some("j"))
      val l = SharedObjectStruct(name = "l", abstractObject = true, maybeParentId = Some("k"))

      val structs = Vector(j, k, l)

      modelBuilder.linearizeSharedObjects(structs) must throwA[LinearizationException].like {
        case e: LinearizationException => e.getMessage mustEqual "The inheritance graph is cyclic. Make sure there are no cycles in your inheritance structure."
      }
    }
  }

  "Getting parents' struct members" should {
    val modelBuilder = new ModelBuilder(assumeAllRequired = false)

    "return None, when a non SharedObjectStruct is passed in" in {
      val struct = MemberStruct("meh...")
      val structByName = Map("meh..." -> struct)
      val namespace = Namespace.root

      modelBuilder.ancestorClassMembers(struct, structByName, namespace) must beNone
    }

    "return None, when a struct without parent is passed in" in {
      val struct = SharedObjectStruct("orphan", mutable.HashMap.empty[String, Struct], abstractObject = false, maybeParentId = None)
      val structByName = Map("orphan" -> struct)
      val namespace = Namespace.root

      modelBuilder.ancestorClassMembers(struct, structByName, namespace) must beNone
    }

    val parent = SharedObjectStruct(
      "parent",
      mutable.HashMap(
        "b" -> MemberStruct("b")
      ),
      abstractObject = true,
      None
    )
    val child = SharedObjectStruct(
      "child",
      mutable.HashMap(
        "c" -> MemberStruct("c")
      ),
      abstractObject = false,
      Some("parent")
    )
    val structByName = Map("parent" -> parent, "child" -> child)
    val namespace = Namespace.root
    namespace.addDefine(
      "parent",
      AbstractObjectType(
        Map(
          "b" -> AnnType(STRING, optional = false, None, None, None)
        )
      )
    )
    namespace.addDefine(
      "child",
      AbstractObjectType(
        Map(
          "c" -> AnnType(STRING, optional = false, None, None, Some(
              Map(
                "b" -> AnnType(STRING, optional = false, None, None, None)
              )
            )
          )
        )
      )
    )

    "return correct ancestor members on one-level hierarchy" in {
      val actual = modelBuilder.ancestorClassMembers(child, structByName, namespace)
      actual must beSome(
        Map("b" -> AnnType(STRING, optional = false, None, None, None))
      )
    }

    "throw an exception, if the parent struct is not among the mapping from id to struct" in {
      val erroneousStructByName = Map("child" -> child)
      modelBuilder.ancestorClassMembers(child, erroneousStructByName, namespace) must
        throwAn[ObjectDefinitionException].like {
          case e: ObjectDefinitionException => e.getMessage shouldEqual "Cannot find definition for parent struct " +
            "'parent', although it is supposed to be parent of 'child'"
        }
    }

    "throw an exception, if the parent is not among the namespace" in {
      val erroneousNamespace = Namespace.root
      erroneousNamespace.addDefine(
        "child",
        AbstractObjectType(
          Map(
            "c" -> AnnType(STRING, optional = false, None, None,
              Some(
                Map(
                  "b" -> AnnType(STRING, optional = false, None, None, None)
                )
              )
            )
          )
        )
      )
      modelBuilder.ancestorClassMembers(child, structByName, erroneousNamespace) must
        throwAn[ObjectDefinitionException].like {
          case e: ObjectDefinitionException => e.getMessage shouldEqual "Unable to find definition for super class " +
            "'parent' in namespace."
        }
    }

    "return the correct ancestor members on multi level hierarchy" in {
      val grandParent = SharedObjectStruct(
        "grandparent",
        mutable.HashMap(
          "a" -> MemberStruct("a")
        ),
        abstractObject = true,
        None
      )
      val parent = SharedObjectStruct(
        "parent",
        mutable.HashMap(
          "b" -> MemberStruct("b")
        ),
        abstractObject = true,
        Some("grandparent")
      )
      val structByName = Map("grandparent" -> grandParent, "parent" -> parent, "child" -> child)
      val namespace = Namespace.root
      namespace.addDefine(
        "grandparent",
        AbstractObjectType(
          Map(
            "a" -> AnnType(STRING, optional = false, None, None, None)
          )
        )
      )
      namespace.addDefine(
        "parent",
        AbstractObjectType(
          Map(
            "b" -> AnnType(STRING, optional = false, None, None,
              Some(
                Map(
                  "a" -> AnnType(STRING, optional = false, None, None, None)
                )
              )
            )
          )
        )
      )
      namespace.addDefine(
        "child",
        AbstractObjectType(
          Map(
            "c" -> AnnType(STRING, optional = false, None, None,
              Some(
                Map(
                  "b" -> AnnType(STRING, optional = false, None, None, None)
                )
              )
            )
          )
        )
      )

      val actual = modelBuilder.ancestorClassMembers(child, structByName, namespace)
      actual should beSome(
        Map(
          "a" -> AnnType(STRING, optional = false, None, None, None),
          "b" -> AnnType(STRING, optional = false, None, None,
            Some(
              Map(
                "a" -> AnnType(STRING, optional = false, None, None, None)
              )
            )
          )
        )
      )
    }
  }
}
