package com.featurefm.riversong

/**
 * Created by yardena on 11/8/15.
 */
package object message {

  trait GenericMessage

  case class Message(message: String,
                     cause: Option[String] = None,
                     operation: Option[String] = None,
                     id: Option[String] = None ) extends GenericMessage

  sealed trait Operation
  case object Add extends Operation { override def toString = "add"}
  case object Update extends Operation { override def toString = "update"}
  case object Delete extends Operation { override def toString = "remove"}
  case object Read extends Operation { override def toString = "read"}
  case object Find extends Operation { override def toString = "find"}
  case class Op(op: String) extends Operation { override def toString = op }


  object Successfully {
    def apply(op: Operation, subject: String): Message =
      Message(s"Successfully performed $op $subject", operation = Some(op.toString), id = None)
    def apply(op: Operation, subject: String, subjectId: String): Message =
      Message(s"Successfully performed $op $subject", operation = Some(op.toString), id = Some(subjectId))
    def apply[T](op: Operation, subject: String, subjectId: String, result: T): Message = //todo handle result
      Message(s"Successfully performed $op $subject", operation = Some(op.toString), id = Some(subjectId))
  }

  object FailedTo {
    def apply(op: Operation, subject: String, cause: Throwable): Message =
      Message(s"Failed to $op $subject", operation = Some(op.toString), id = None, cause = Some(cause.toString))
    def apply(op: Operation, subject: String, subjectId: String, cause: Throwable): Message =
      Message(s"Failed to $op $subject", operation = Some(op.toString), id = Some(subjectId), cause = Some(cause.toString))
    def apply(op: Operation, subject: String, subjectId: String): Message =
      Message(s"Failed to $op $subject", operation = Some(op.toString), id = Some(subjectId))
  }
}
