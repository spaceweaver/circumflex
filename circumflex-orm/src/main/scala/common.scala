package ru.circumflex.orm

import ru.circumflex.core._

/*!# SQLable & Expression

Every object capable of rendering itself into an SQL statement
should extend the `SQLable` trait.*/
trait SQLable {
  def toSql: String
}

/*!# Parameterized expressions

The `Expression` trait provides basic functionality for dealing
with SQL expressions with JDBC-style parameters.
*/
trait Expression extends SQLable {

  /**
   * The parameters associated with this expression. The order is important.
   */
  def parameters: Seq[Any]

  /**
   * Renders this query by replacing parameter placeholders with actual values.
   */
  def toInlineSql: String = parameters.foldLeft(toSql)((sql, p) =>
    sql.replaceFirst("\\?", dialect.escapeParameter(p)
        .replaceAll("\\\\", "\\\\\\\\")
        .replaceAll("\\$", "\\\\\\$")))

  override def equals(that: Any) = that match {
    case e: Expression =>
      e.toSql == this.toSql && e.parameters.toList == this.parameters.toList
    case _ => false
  }

  override def hashCode = 0

  override def toString = toSql
}

/*!# Schema Object

Every database object which could be created or dropped should
implement the `SchemaObject` trait.
*/
trait SchemaObject {
  /**
   * SQL statement to create this database object.
   */
  def sqlCreate: String

  /**
   * SQL statement to drop this database object.
   */
  def sqlDrop: String

  /**
   * SQL object name. It is used to uniquely identify this object
   * during schema creation by `DDL` to avoid duplicates and to print
   * nice messages on schema generation.
   *
   * We follow default convention to name objects:
   *
   *     <TYPE OF OBJECT> <qualified_name>
   *
   * where `TYPE OF OBJECT` is `TABLE`, `VIEW`, `SEQUENCE`, `TRIGGER`,
   * `FUNCTION`, `PROCEDURE`, `INDEX`, etc. (note the upper case), and
   * `qualified_name` is object's unique identifier.
   *
   * For equality testing, object names are taken in case-insensitive manner
   * (e.g. `MY_TABLE` and `my_table` are considered equal).
   */
  def objectName: String

  override def hashCode = objectName.toLowerCase.hashCode

  override def equals(obj: Any) = obj match {
    case so: SchemaObject => so.objectName.equalsIgnoreCase(this.objectName)
    case _ => false
  }

  override def toString = objectName
}

/*!# Value holders

Value holder is an atomic data-carrier unit of a record. It carries methods for
identifying and manipulating data fields inside persistent records.
*/
trait ValueHolder[T, R <: Record[_, R]] extends Container[T] with Wrapper[Option[T]] {
  def name: String
  def record: R
  def item = value

  /*!## Setters

  Setters provide a handy mechanism for preprocessing values before
  setting them. They are functions `T => T` which are applied one-by-one
  each time you set new non-null value. You can add a setter by invoking
  the `addSetter` method:

      val pkg = "package".TEXT.NOT_NULL
          .addSetter(_.trim)
          .addSetter(_.toLowerCase)
          .addSetter(_.replaceAll("/","."))

      pkg := "  ru/circumflex/ORM  "  // "ru.circumflex.orm" will be assigned

  ## Accessing & Setting Values

  Values are stored internally as `Option[T]`. `None` stands both for
  uninitialized and `null` values. Following examples show how field values
  can be accessed or set:

      val id = "id" BIGINT

      // accessing
      id.value    // Option[Long]
      id.get      // Option[Long]
      id()        // Long or exception
      getOrElse(default: Long)  // Long

      // setting
      id.set(Some(1l))
      id.setNull
      id := 1l

  The `null_?` method indicates whether the underlying value is `null` or not.

  ## Methods from `Option`

  Since `ValueHolder` is just a wrapper around `Option`, we provide
  some methods to work with your values in functional style
  (they delegate to their equivalents in `Option`).

  ## Equality & Others

  Two fields are considered equal if they belong to the same type of records
  and share the same name.

  The `hashCode` calculation is consistent with `equals` definition.

  The `canEqual` method indicates whether the two fields belong to the same
  type of records.

  Finally, `toString` returns the qualified name of relation which it
  belongs to followed by a dot and the field name.
  */
  override def equals(that: Any): Boolean = that match {
    case that: ValueHolder[_, _] => this.canEqual(that) &&
        this.name == that.name
    case _ => false
  }
  override lazy val hashCode: Int =  record.relation.qualifiedName.hashCode * 31 +
      name.hashCode
  def canEqual(that: Any): Boolean = that match {
    case that: ValueHolder[_, _] => this.record.canEqual(that.record)
    case _ => false
  }
  override def toString: String = record.relation.qualifiedName + "." + name

  /*! The `placeholder` method returns an expression which is used to mark a parameter
  inside JDBC `PreparedStatement` (usually `?` works, but custom data-type may require
  some special treatment).
   */
  def placeholder = dialect.placeholder

  /*! ## Composing predicates

  `ValueHolder` provides very basic functionality for predicates composition:

  * `aliasedName` returns the name of this holder qualified with node alias (in appropriate context);
  * `EQ` creates an equality predicate (i.e. `column = value` or `column = column`);
  * `NE` creates an inequality predicate (i.e. `column <> value` or `column <> column`).
  * `IS_NULL` and `IS_NOT_NULL` creates (not-)nullability predicates
    (i.e. `column IS NULL` or `column IS NOT NULL`).

  More specific predicates can be acquired from subclasses.
  */
  def aliasedName = aliasStack.pop match {
    case Some(alias: String) => alias + "." + name
    case _ => name
  }

  def EQ(value: T): Predicate =
    new SimpleExpression(dialect.EQ(aliasedName, placeholder), List(value))
  def EQ(col: ColumnExpression[_, _]): Predicate =
    new SimpleExpression(dialect.EQ(aliasedName, col.toSql), Nil)
  def NE(value: T): Predicate =
    new SimpleExpression(dialect.NE(aliasedName, placeholder), List(value))
  def NE(col: ColumnExpression[_, _]): Predicate =
    new SimpleExpression(dialect.NE(aliasedName, col.toSql), Nil)
  def IS_NULL: Predicate =
    new SimpleExpression(dialect.IS_NULL(aliasedName), Nil)
  def IS_NOT_NULL: Predicate =
    new SimpleExpression(dialect.IS_NOT_NULL(aliasedName), Nil)

}

class ColumnExpression[T, R <: Record[_, R]](column: ValueHolder[T, R])
    extends Expression {
  def parameters = Nil
  val toSql = column.aliasedName
}
