package ai.davoted.craving.ui

/**
 * A conservative, source-linked snapshot of public career metadata.
 * IMDb is a secondary credits database, not a primary rights or authenticity authority.
 * These records are intentionally not injected as verified archive facts.
 */
data class NatashaPublicArchiveFact(
    val id: String,
    val heading: String,
    val detail: String,
    val sourceLabel: String,
    val sourceUrl: String,
    val provenanceNote: String
)

val NatashaPublicArchiveFacts = listOf(
    NatashaPublicArchiveFact(
        id = "imdb-credit-categories",
        heading = "Public credit categories",
        detail = "IMDb's public profile classifies Natasha Nice's credits under Actress, Writer, Director, Self, and Archive Footage.",
        sourceLabel = "IMDb — Natasha Nice profile",
        sourceUrl = "https://www.imdb.com/name/nm2510606/",
        provenanceNote = "Secondary database snapshot. Categories describe IMDb's listing, not an independently audited complete career record."
    ),
    NatashaPublicArchiveFact(
        id = "imdb-recent-credits",
        heading = "Recent listed credits",
        detail = "The public credits listing includes Brazzers Exxtra (2023–2026), Cheating with My Wife Volume 3 (2025), and Permission (2025).",
        sourceLabel = "IMDb — full credits",
        sourceUrl = "https://www.imdb.com/name/nm2510606/fullcredits/",
        provenanceNote = "Secondary database snapshot. Titles and year ranges are transcribed from the cited credits page."
    ),
    NatashaPublicArchiveFact(
        id = "imdb-2024-credit",
        heading = "2024 listed credit",
        detail = "Natasha Nice: Busty Bombshell is listed among the profile's 2024 video credits.",
        sourceLabel = "IMDb — full credits",
        sourceUrl = "https://www.imdb.com/name/nm2510606/fullcredits/",
        provenanceNote = "Secondary database snapshot. This is a catalog entry, not a media asset or license to reproduce it."
    )
)
