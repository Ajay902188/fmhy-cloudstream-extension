// Use an integer for version numbers
version = 1

cloudstream {
    description = "Browse and stream from the FMHY video directory"
    authors = listOf("Ajay902188")

    /**
     * Status int as one of the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta-only
     */
    status = 1 // Ok

    language = "en"

    tvTypes = listOf("Movie", "TvSeries", "Anime", "Others")
}
