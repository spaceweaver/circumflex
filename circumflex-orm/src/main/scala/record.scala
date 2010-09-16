package ru.circumflex.orm

/*!# Record

The record is a central abstraction in Circumflex ORM. Every object persisted into
database should extend the `Record` class. It provides data definition methods
necessary to create a domain model for your application as well as methods for
saving and updating your record to backend database.

Circumflex ORM is all about type safety and domain-specific languages (at a first
glance the record definition may seem a little verbose). Here's the sample definition
of fictional record `Country`:

    class Country extends Record[String, Country] {
      val code = "code".VARCHAR(2)
      val name = "name".TEXT

      def PRIMARY_KEY = code
      val relation = Country
    }

*/
abstract class Record[PK, R <: Record[PK, R]] extends Equals { this: R =>

  implicit def str2ddlHelper(str: String): DefinitionHelper[R] =
    new DefinitionHelper(str, this)

  /*!## Record State

  Records in relational theory are distinguished from each other by the value of their
  *primary key*. You should specify what field hold the primary key of your record
  by implementing the `PRIMARY_KEY` method.

  The `transient_?` method indicates, whether the record was not persisted into a database
  yet or it was. The default logic is simple: if the primary key contains `null` then the
  record is *transient* (i.e. not persisted), otherwise the record is considered persistent.

  The `relation` method points to the relation from which a record came or to which it
  should go. In general this method should point to the companion object. However, if
  you do not convey to Circumflex ORM conventions, you may specify another object which
  will act a relation for this type of records.
  */
  def PRIMARY_KEY: Field[PK, R]
  def transient_?(): Boolean = PRIMARY_KEY.null_?
  def relation: Relation[PK, R]

  /*!## Persistence

  The `insert_!`, `update_!` and `delete_!` methods are used to insert, update or delete a single
  record. The `insert` and `update` do the same as their equivalents except that validation
  is performed before actual execution. The `refresh` method performs select with primary key
  criteria and updates the fields with retrieved values.

  When inserting new record into database the primary key should be generated. It is done either
  by polling database, by supplying `NULL` in primary key and then querying last generated identifier
  or manually by application. The default implementation relies on application-assigned identifiers;
  to use different strategy mix in one of the `Generator` traits or simply override the `persist`
  method.
  */

  def INSERT_!(fields: Field[_, R]*): Int = if (relation.readOnly_?)
    throw new ORMException("The relation " + relation.qualifiedName + " is read-only.")
  else {
    // Execute events
    relation.beforeInsert.foreach(c => c(this))
    // Collect fields which will participate in query
    var f: Seq[Field[_, R]] = evalFields(fields)
    // Prepare and execute query
    val result = persist(f)
    // Execute events
    relation.afterInsert.foreach(c => c(this))
    return result
  }

  protected def evalFields(fields: Seq[Field[_, R]]): Seq[Field[_, R]] =
    (if (fields.size == 0) relation.fields else fields)
        .map(f => relation.methodsMap(f).invoke(this).asInstanceOf[Field[_, R]])

  protected def persist(fields: Seq[Field[_, R]]): Int = {
    //    val sql = dialect.insertRecord(this, f)
    //    val result = tx.execute(sql) { st =>
    //      relation.setParams(this, st, f)
    //      st.executeUpdate
    //    } { throw _ }
    //    relation.refetchLast(this)
    0
  }

  /*!## Equality & Others
  
  Two record are considered equal if they share the same type and have same primary keys.
  Transient records are never equal to each other.

  The `hashCode` method delegates to record's primary key.

  The `canEqual` method indicates that two records share the same type.

  Finally, the default implementation of `toString` returns fully-qualified class name
  of the record followed by "@" and it's primary key value (or `TRANSIENT` word if
  primary key is `null`).
  */
  override def equals(that: Any) = that match {
    case that: Record[_, _] =>
      !(this.PRIMARY_KEY.null_? || that.PRIMARY_KEY.null_?) &&
          this.PRIMARY_KEY.value == that.PRIMARY_KEY.value &&
          this.getClass == that.getClass
    case _ => false
  }

  override def hashCode = PRIMARY_KEY.hashCode

  def canEqual(that: Any): Boolean = that match {
    case that: Record[_, _] =>
      this.getClass == that.getClass
    case _ => false
  }

  override def toString = getClass.getSimpleName + "@" +
      PRIMARY_KEY.map(_.toString).getOrElse("TRANSIENT")

}
