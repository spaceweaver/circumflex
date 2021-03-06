package ru.circumflex.orm

import ru.circumflex.core._
import java.util.Date
import xml._
import java.sql.ResultSet
import scala.math.BigDecimal._

/*!# Field

The `Field` class holds atomic values of records. It wraps actual value
and provides methods for constructing column definitions for enclosing
tables. It also contains the `REFERENCES` method which is used to create
associations and various methods for composing simple predicates.
*/
class Field[T, R <: Record[_, R]](val name: String,
                                  val record: R,
                                  val sqlType: String)
    extends ValueHolder[T, R] with SQLable {

  def uuid = record.getClass.getName + "." + name

  def toSql: String = dialect.columnDefinition(this)

  def read(rs: ResultSet, alias: String): Option[T] = {
    val o = rs.getObject(alias)
    if (rs.wasNull) None
    else Some(o.asInstanceOf[T])
  }

  /*!## Column Definition Methods

  Following methods help you construct a definition of the column where
  the field will be persisted:

    * `NOT_NULL` will render `NOT NULL` constraint in column's definition;
    note that starting from 2.0, by default the `NOT NULL` constraint is
    omitted and `NULLABLE` construct is no longer supported; this method
    can also be used as a shortcut for specifying the `NOT NULL` constraint
    and assigning default field value:

        // following declarations are identical
        val createdAt = "created_at".TIMESTAMP.NOT_NULL.set(new Date())
        val createdAt = "created_at".TIMESTAMP.NOT_NULL(new Date())


    * `DEFAULT` will render the `DEFAULT` expression in column's definition
    (if not overriden by dialect);
    * `UNIQUE` will create a `UNIQUE` constraint for enclosing table on
    the field.
  */
  protected var _notNull: Boolean = false
  def notNull_?(): Boolean = _notNull
  def NOT_NULL(): this.type = {
    _notNull = true
    return this
  }
  def NOT_NULL(initialValue: T): this.type = NOT_NULL().set(initialValue)

  protected var _unique: Boolean = false
  def unique_?(): Boolean = _unique
  def UNIQUE(): this.type = {
    _unique = true
    return this
  }

  protected var _defaultExpression: Option[String] = None
  def defaultExpression: Option[String] = _defaultExpression
  def DEFAULT(expr: String): this.type = {
    _defaultExpression = Some(expr)
    return this
  }

  def REFERENCES[P <: Record[T, P]](relation: Relation[T, P]): Association[T, R, P] =
    new Association(this, relation)

  /*!## Simple expressions

  Simple expressions are used to compose predicates in a DSL-style.
  */
  def GT(value: T): Predicate =
    new SimpleExpression(dialect.GT(aliasedName, placeholder), List(value))
  def GT(col: ColumnExpression[_, _]): Predicate =
    new SimpleExpression(dialect.GT(aliasedName, col.toSql), Nil)
  def GE(value: T): Predicate =
    new SimpleExpression(dialect.GE(aliasedName, placeholder), List(value))
  def GE(col: ColumnExpression[_, _]): Predicate =
    new SimpleExpression(dialect.GE(aliasedName, col.toSql), Nil)
  def LT(value: T): Predicate =
    new SimpleExpression(dialect.LT(aliasedName, placeholder), List(value))
  def LT(col: ColumnExpression[_, _]): Predicate =
    new SimpleExpression(dialect.LT(aliasedName, col.toSql), Nil)
  def LE(value: T): Predicate =
    new SimpleExpression(dialect.LE(aliasedName, placeholder), List(value))
  def LE(col: ColumnExpression[_, _]): Predicate =
    new SimpleExpression(dialect.LE(aliasedName, col.toSql), Nil)

  def IN(params: Seq[T]): Predicate =
    new SimpleExpression(dialect.parameterizedIn(aliasedName, params.map(p => placeholder)), params.toList)
  def BETWEEN(lowerValue: T, upperValue: T) =
    new SimpleExpression(dialect.BETWEEN(aliasedName, placeholder, placeholder), List(lowerValue, upperValue))

  def IN(query: SQLQuery[_]): Predicate =
    new SubqueryExpression(dialect.IN(aliasedName), query)
  def NOT_IN(query: SQLQuery[_]): Predicate =
    new SubqueryExpression(dialect.NOT_IN(aliasedName), query)

  def EQ_ALL(query: SQLQuery[_]): Predicate =
    new SubqueryExpression(dialect.EQ(aliasedName, dialect.ALL), query)
  def NE_ALL(query: SQLQuery[_]): Predicate =
    new SubqueryExpression(dialect.NE(aliasedName, dialect.ALL), query)
  def GT_ALL(query: SQLQuery[_]): Predicate =
    new SubqueryExpression(dialect.GT(aliasedName, dialect.ALL), query)
  def GE_ALL(query: SQLQuery[_]): Predicate =
    new SubqueryExpression(dialect.GE(aliasedName, dialect.ALL), query)
  def LT_ALL(query: SQLQuery[_]): Predicate =
    new SubqueryExpression(dialect.LT(aliasedName, dialect.ALL), query)
  def LE_ALL(query: SQLQuery[_]): Predicate =
    new SubqueryExpression(dialect.LE(aliasedName, dialect.ALL), query)

  def EQ_SOME(query: SQLQuery[_]): Predicate =
    new SubqueryExpression(dialect.EQ(aliasedName, dialect.SOME), query)
  def NE_SOME(query: SQLQuery[_]): Predicate =
    new SubqueryExpression(dialect.NE(aliasedName, dialect.SOME), query)
  def GT_SOME(query: SQLQuery[_]): Predicate =
    new SubqueryExpression(dialect.GT(aliasedName, dialect.SOME), query)
  def GE_SOME(query: SQLQuery[_]): Predicate =
    new SubqueryExpression(dialect.GE(aliasedName, dialect.SOME), query)
  def LT_SOME(query: SQLQuery[_]): Predicate =
    new SubqueryExpression(dialect.LT(aliasedName, dialect.SOME), query)
  def LE_SOME(query: SQLQuery[_]): Predicate =
    new SubqueryExpression(dialect.LE(aliasedName, dialect.SOME), query)

}

trait AutoIncrementable[T, R <: Record[_, R]] extends Field[T, R] {
  protected var _autoIncrement: Boolean = false
  def autoIncrement_?(): Boolean = _autoIncrement
  def AUTO_INCREMENT(): this.type = {
    _autoIncrement = true
    return this
  }
}

class IntField[R <: Record[_, R]](name: String, record: R)
    extends XmlSerializable[Int, R](name, record, dialect.integerType)
    with AutoIncrementable[Int, R] {
  def from(str: String): Option[Int] =
    try Some(str.toInt) catch { case _ => None }
}

class LongField[R <: Record[_, R]](name: String, record: R)
    extends XmlSerializable[Long, R](name, record, dialect.longType)
    with AutoIncrementable[Long, R]{
  def from(str: String): Option[Long] =
    try Some(str.toLong) catch { case _ => None }
}

class DoubleField[R <: Record[_, R]](name: String,
                                     record: R,
                                     val precision: Int = -1,
                                     val scale: Int = 0)
    extends XmlSerializable[Double, R](name, record, dialect.numericType(precision, scale)) {
  override def read(rs: ResultSet, alias: String): Option[Double] = {
    val d = rs.getDouble(alias)
    if (rs.wasNull) None
    else Some(d)
  }
  def from(str: String): Option[Double] =
    try Some(str.toDouble) catch { case _ => None }
}

class NumericField[R <: Record[_, R]](name: String,
                                      record: R,
                                      val precision: Int = -1,
                                      val scale: Int = 0,
                                      val roundingMode: RoundingMode.RoundingMode = RoundingMode.HALF_EVEN)
    extends XmlSerializable[BigDecimal, R](name, record, dialect.numericType(precision, scale)) {
  def from(str: String): Option[BigDecimal] =
    try Some(BigDecimal(str)) catch { case _ => None }
  override def read(rs: ResultSet, alias: String): Option[BigDecimal] = {
    val bd = rs.getBigDecimal(alias)
    if (rs.wasNull) None
    else Some(new BigDecimal(bd))
  }
  addSetter(v => v.setScale(scale, roundingMode))
}

class TextField[R <: Record[_, R]](name: String, record: R, sqlType: String)
    extends XmlSerializable[String, R](name, record, sqlType) {
  def this(name: String, record: R, length: Int = -1) =
    this(name, record, dialect.varcharType(length))
  def from(str: String): Option[String] =
    if (str == "") None else Some(str)

  def LIKE(value: String) = new SimpleExpression(dialect.LIKE(aliasedName, placeholder), List(value))
  def LIKE(col: ColumnExpression[String, _]) =
    new SimpleExpression(dialect.LIKE(aliasedName, col.toSql), Nil)
  def ILIKE(value: String) = new SimpleExpression(dialect.ILIKE(aliasedName, placeholder), List(value))
  def ILIKE(col: ColumnExpression[String, _]) =
    new SimpleExpression(dialect.ILIKE(aliasedName, col.toSql), Nil)
}

class BooleanField[R <: Record[_, R]](name: String, record: R)
    extends XmlSerializable[Boolean, R](name, record, dialect.booleanType) {
  def from(str: String): Option[Boolean] =
    try Some(str.toBoolean) catch { case _ => None }
}

class TimestampField[R <: Record[_, R]](name: String, record: R)
    extends XmlSerializable[Date, R](name, record, dialect.timestampType) {
  def from(str: String): Option[Date] =
    try Some(new Date(java.sql.Timestamp.valueOf(str).getTime)) catch { case _ => None }
  override def to(value: Option[Date]): String =
    value.map(v => new java.sql.Timestamp(v.getTime).toString).getOrElse("")
}

class DateField[R <: Record[_, R]](name: String, record: R)
    extends XmlSerializable[Date, R](name, record, dialect.dateType) {
  def from(str: String): Option[Date] =
    try Some(new Date(java.sql.Date.valueOf(str).getTime)) catch { case _ => None }
  override def to(value: Option[Date]): String =
    value.map(v => new java.sql.Date(v.getTime).toString).getOrElse("")
}

class TimeField[R <: Record[_, R]](name: String, record: R)
    extends XmlSerializable[Date, R](name, record, dialect.timeType) {
  def from(str: String): Option[Date] =
    try Some(new Date(java.sql.Time.valueOf(str).getTime)) catch { case _ => None }
  override def to(value: Option[Date]): String =
    value.map(v => new java.sql.Time(v.getTime).toString).getOrElse("")
}

class XmlField[R <: Record[_, R]](name: String, record: R, val root: String)
    extends XmlSerializable[Elem, R](name, record, dialect.xmlType) {
  def from(str: String): Option[Elem] = Some(XML.loadString(
    "<" + root + ">" + str + "</" + root +">"))
  override def read(rs: ResultSet, alias: String) =
    any2option(rs.getString(alias)).map(x => XML.loadString(x))
  override def placeholder = dialect.xmlPlaceholder
}

class FieldComposition2[T1, T2, R <: Record[_, R]](val _1: Field[T1, R],
                                                   val _2: Field[T2, R],
                                                   val record: R)
    extends ValueHolder[(T1, T2), R] {

  def name = dialect.compositeFieldName(_1.name, _2.name)

  override def value: Option[(T1, T2)] = (_1.get, _2.get) match {
    case (Some(v1), Some(v2)) => Some(v1 -> v2)
    case _ => None
  }

  override def set(v: Option[(T1, T2)]): this.type = {
    v match {
      case Some(Pair(v1, v2)) =>
        _1.set(v1)
        _2.set(v2)
      case _ =>
        _1.setNull
        _2.setNull
    }
    return this
  }

  protected def _getPrefix = aliasStack.pop.map(_ + ".").getOrElse("")

  override def aliasedName: String = {
    val prefix = _getPrefix
    return dialect.compositeFieldName(prefix + _1.name, prefix + _2.name)
  }

  override def EQ(value: (T1, T2)) = {
    val prefix = _getPrefix
    AND(new SimpleExpression(dialect.EQ(prefix + _1.name, placeholder), List(value._1)),
      new SimpleExpression(dialect.EQ(prefix + _2.name, placeholder), List(value._2)))
  }
  override def NE(value: (T1, T2)) = {
    val prefix = _getPrefix
    AND(new SimpleExpression(dialect.NE(prefix + _1.name, placeholder), List(value._1)),
      new SimpleExpression(dialect.NE(prefix + _2.name, placeholder), List(value._2)))
  }
  override def IS_NULL = {
    val prefix = _getPrefix
    AND(new SimpleExpression(dialect.IS_NULL(prefix + _1.name), Nil),
      new SimpleExpression(dialect.IS_NULL(prefix + _2.name), Nil))
  }
  override def IS_NOT_NULL = {
    val prefix = _getPrefix
    AND(new SimpleExpression(dialect.IS_NOT_NULL(prefix + _1.name), Nil),
      new SimpleExpression(dialect.IS_NOT_NULL(prefix + _2.name), Nil))
  }
}
