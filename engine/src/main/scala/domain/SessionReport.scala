package domain

import java.nio.file.Path

/** What one fuzz session produced. `pool` is the literals the strategy was actually given access to (empty for non-pool strategies). */
final case class SessionReport[A](
    methodName: String,
    sourceFile: Path,
    branchTree: Option[BranchTree],
    strategy: String,
    pool: ConstantPool,
    feedback: SessionFeedback[A]
)
