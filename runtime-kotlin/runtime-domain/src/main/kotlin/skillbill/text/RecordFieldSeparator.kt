package skillbill.text

/**
 * The single separator the runtime uses to join record fields into one opaque key or digest preimage.
 *
 * NUL cannot occur inside a repository path, a revision id, or a workflow/phase id, so joining on it
 * keeps composite keys unambiguous. One owner keeps every producer and consumer on the same byte.
 */
const val RECORD_FIELD_SEPARATOR: String = "\u0000"
