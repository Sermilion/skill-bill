package skillbill.text

/**
 * The NUL byte git writes between records when it is invoked with `-z`.
 *
 * This is git's wire format, not our own key encoding, so it keeps a name separate from
 * [RECORD_FIELD_SEPARATOR]: every reader of this constant is a raw git output parser, and grepping
 * the name finds them all. The parallel-review evidence path still parses git output outside the
 * workflow git adapter; this delimiter marks those sites until they move behind the adapter.
 */
const val GIT_NUL_RECORD_DELIMITER: String = "\u0000"
