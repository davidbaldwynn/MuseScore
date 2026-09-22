package com.scoreleaf.app

/**
 * Test-only integration endpoints.
 *
 * These endpoints are isolated to the authorized musescore.test environment.
 * Production musescore.com hosts are rejected by the sandbox policy.
 */
object ScoreleafConstants {
    const val MUSESCORE_SANDBOX_BASE_URL = "https://musescore.test/"
    const val MUSESCORE_SANDBOX_PDF_EXPORT_TEMPLATE =
        "https://musescore.test/api/test/scores/{scoreId}/export.pdf"
    const val MUSESCORE_SANDBOX_PAGE_TEMPLATE =
        "https://musescore.test/api/jmuse?id={scoreId}&index={pageIndex}&type=img"
}
