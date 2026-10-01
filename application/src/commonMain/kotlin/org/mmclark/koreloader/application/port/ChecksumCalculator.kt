package org.mmclark.koreloader.application.port

import org.mmclark.koreloader.domain.catalog.HashAlgorithm

/** Port: computes digests of a local file. */
fun interface ChecksumCalculator {
    /** Returns lower-case hex digests for each requested algorithm, reading the file once. */
    suspend fun compute(file: LocalFile, algorithms: Set<HashAlgorithm>): Map<HashAlgorithm, String>
}
