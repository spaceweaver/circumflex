package ru.circumflex.orm

import collection.mutable.HashMap
import java.sql.PreparedStatement
import java.util.UUID

/**
 * Contains base functionality for objects that can be retrieved from and
 * persisted to database relations.
 * There's a couple of things one must know about records.
 * <ul>
 * <li>Each record instance "knows" about it's main relation through
 * <code>relation</code> method.</li>
 * <li>Records carry the data around using <em>fields</em; internally they are
 * stored in <code>fieldsMap</code> in a "column-to-value" form.</li>
 * <li>Each record has a primary key field which identifies the record in database.
 * The <code>isIdentified</code> method determines, whether primary key field is set.</li>
 * <li>Two records are considered equal if their relations and primary key
 * fields are equal. If they are not identified, the internally generated uuid is
 * used for equality testing (so unidentified records never match each other).</li>
 * </ul>
 */
abstract class Record extends JDBCHelper {

  private val uuid = UUID.randomUUID.toString

  private val fieldsMap = HashMap[Column[_], Any]()
  private val manyToOneMap = HashMap[Association, Any]()
  private val oneToManyMap = HashMap[Association, Seq[Any]]()

  def relation: Relation
  def primaryKey: Option[_] = fieldsMap.get(relation.primaryKey.column)
  def isIdentified = primaryKey != None

  /* FIELDS-RELATED STUFF */

  def field[T](col: Column[T]) = new Field(this, col)

  def getFieldValue[T](col: Column[T]): Option[T] =
    fieldsMap.get(col).asInstanceOf[Option[T]]

  def setFieldValue[T](col: Column[T], value: T): Unit =
    setFieldValue(col, Some(value))

  def setFieldValue[T](col: Column[T], value: Option[T]) = {
    value match {
      case Some(value) => fieldsMap += (col -> value)
      case _ => fieldsMap -= col
    }
    relation.associations.foreach(a => if (a.localColumn == col) {
      manyToOneMap -= a
      oneToManyMap -= a
    })
  }

  /* ASSOCIATIONS-RELATED STUFF */

  def manyToOne[P <: Record](association: Association) =
    new ManyToOne[P](this, association)

  /* PERSISTENCE-RELATED STUFF */

  def insert(): Int = {
    val conn = relation.configuration.connectionProvider.getConnection
    val sql = relation.dialect.insertRecord(this)
    sqlLog.debug(sql)
    auto (conn.prepareStatement(sql)) (st => {
      setParams(st, relation.columns)
      return st.executeUpdate
    })
  }

  def update(): Int = {
    val conn = relation.configuration.connectionProvider.getConnection
    val sql = relation.dialect.updateRecord(this)
    sqlLog.debug(sql)
    auto (conn.prepareStatement(sql)) (st => {
      setParams(st, relation.nonPKColumns)
      relation.configuration.typeConverter.write(
        st,
        primaryKey.get,
        relation.nonPKColumns.size + 1)
      return st.executeUpdate
    })
  }

  def save() = if (isIdentified) update()
  else {
    generateFields
    insert()
  }

  def delete(): Int = {
    val conn = relation.configuration.connectionProvider.getConnection
    val sql = relation.dialect.deleteRecord(this)
    sqlLog.debug(sql)
    auto (conn.prepareStatement(sql)) (st => {
      relation.configuration.typeConverter.write(st, primaryKey.get, 1)
      return st.executeUpdate
    })
  }

  def generateFields(): Unit =
    relation.columns.flatMap(_.sequence).foreach(seq => {
      val nextval = seq.nextValue
      this.setFieldValue(seq.column, nextval)
    })

  private def setParams(st: PreparedStatement, cols: Seq[Column[_]]) =
    (0 until cols.size).foreach(ix => {
      val col = cols(ix)
      val value = this.getFieldValue(col) match {
        case Some (v) => v
        case _ => null
      }
      relation.configuration.typeConverter.write(st, value, ix + 1)
    })

  /* EQUALS BOILERPLATE */

  override def equals(obj: Any) = obj match {
    case r: Record if (r.relation == this.relation) =>
      this.primaryKey.getOrElse(this.uuid) == r.primaryKey.getOrElse(r.uuid)
    case _ => false
  }

  override def hashCode = this.primaryKey.getOrElse(uuid).hashCode

}

class Field[T](val record: Record,
               val column: Column[T]) {

  def get: Option[T] = record.getFieldValue(column)
  def set(value: T): Unit = record.setFieldValue(column,value)
  def setNull: Unit = record.setFieldValue(column, None)

  def <=(value: T): Unit = set(value)
  def :=(value: T): Unit = set(value)

  override def toString = get match {
    case Some(value) => value.toString
    case None => ""
  }
}

class ManyToOne[P <: Record](val record: Record,
                             val association: Association) {

  def get: Option[P] = None

}