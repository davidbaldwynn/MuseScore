package com.scoreleaf.app

/**
 * Test-only integration endpoints.
 *
 * Replace both placeholder values with the HTTPS origin and documented export
 * route of an authorized MuseScore sandbox. Production musescore.com hosts are
 * rejected by [com.scoreleaf.app.model.MuseScoreSandboxPolicy].
 */
object ScoreleafConstants {
    const val MUSESCORE_SANDBOX_BASE_URL = "https://sandbox.musescore.test/"
    const val MUSESCORE_SANDBOX_PDF_EXPORT_TEMPLATE =
        "https://sandbox.musescore.test/api/test/scores/{scoreId}/export.pdf"
}
