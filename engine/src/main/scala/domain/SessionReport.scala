package domain

/** What one fuzz session produced — pure data, no I/O types. `pool` is the literals the strategy was actually given (empty for non-pool strategies).
  */
final case class SessionReport[A](
    methodName: String,
    sourceName: String,
    branchTree: Option[BranchTree],
    strategy: String,
    pool: ConstantPool,
    feedback: SessionFeedback[A]
)
