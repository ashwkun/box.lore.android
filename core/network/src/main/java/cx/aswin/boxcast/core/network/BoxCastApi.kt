package cx.aswin.boxcast.core.network

import cx.aswin.boxcast.core.network.model.EpisodesResponse
import cx.aswin.boxcast.core.network.model.EpisodesPaginatedResponse
import cx.aswin.boxcast.core.network.model.PodcastResponse
import cx.aswin.boxcast.core.network.model.SearchResponse
import cx.aswin.boxcast.core.network.model.TrendingResponse
import cx.aswin.boxcast.core.network.model.SyncRequest
import cx.aswin.boxcast.core.network.model.SyncResponse
import cx.aswin.boxcast.core.network.model.SingleEpisodeResponse
import cx.aswin.boxcast.core.network.model.FeedbackRequest
import cx.aswin.boxcast.core.network.model.FeedbackResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * BoxCast API - Cloudflare Worker proxy for Podcast Index
 */
interface BoxCastApi {
    
    @GET("trending")
    fun getTrending(
        @Header("X-App-Key") publicKey: String,
        @Query("country") country: String? = "us",
        @Query("limit") limit: Int? = 50,
        @Query("cat") category: String? = null // New: Genre Filter
    ): retrofit2.Call<TrendingResponse>

    @GET("trending")
    @retrofit2.http.Streaming
    fun getTrendingStream(
        @Header("X-App-Key") publicKey: String,
        @Query("country") country: String? = "us",
        @Query("limit") limit: Int? = 50,
        @Query("cat") category: String? = null // New: Genre Filter
    ): retrofit2.Call<okhttp3.ResponseBody>

    @GET("search")
    fun search(
        @Header("X-App-Key") publicKey: String,
        @Query("q") query: String
    ): retrofit2.Call<SearchResponse>

    @GET("episodes")
    fun getEpisodes(
        @Header("X-App-Key") publicKey: String,
        @Query("id") feedId: String
    ): retrofit2.Call<EpisodesResponse>
    
    @GET("episodes")
    fun getEpisodesPaginated(
        @Header("X-App-Key") publicKey: String,
        @Query("id") feedId: String,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("sort") sort: String = "newest"
    ): retrofit2.Call<EpisodesPaginatedResponse>

    @GET("episodes/search")
    fun searchEpisodes(
        @Header("X-App-Key") publicKey: String,
        @Query("id") feedId: String,
        @Query("q") query: String
    ): retrofit2.Call<EpisodesResponse>

    @GET("episode")
    fun getEpisode(
        @Header("X-App-Key") publicKey: String,
        @Query("id") id: String
    ): retrofit2.Call<SingleEpisodeResponse>
    
    @GET("podcast")
    fun getPodcast(
        @Header("X-App-Key") publicKey: String,
        @Query("id") feedId: String
    ): retrofit2.Call<PodcastResponse>
    
    @POST("sync")
    fun syncSubscriptions(
        @Header("X-App-Key") publicKey: String,
        @Body request: SyncRequest
    ): retrofit2.Call<SyncResponse>

    @POST("feedback")
    fun submitFeedback(
        @Header("X-App-Key") publicKey: String,
        @Body request: FeedbackRequest
    ): retrofit2.Call<FeedbackResponse>

    @GET("curated/vibe")
    fun getCuratedVibe(
        @Header("X-App-Key") publicKey: String,
        @Query("id") vibeId: String
    ): retrofit2.Call<TrendingResponse> // Reusing TrendingResponse structure (feeds list)

    @GET("podcast/meta")
    fun getPodcastMeta(
        @Header("X-App-Key") publicKey: String,
        @Query("id") feedId: String
    ): retrofit2.Call<cx.aswin.boxcast.core.network.model.PodcastMetaResponse>

    // --- RADIO ROUTES ---

    @GET("radio/locate")
    fun getRadioLocate(
        @Header("X-App-Key") publicKey: String
    ): retrofit2.Call<cx.aswin.boxcast.core.network.model.RadioLocateResponse>

    @GET("radio/popular")
    fun getPopularStations(
        @Header("X-App-Key") publicKey: String,
        @Query("countrycode") countryCode: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): retrofit2.Call<cx.aswin.boxcast.core.network.model.RadioStationResponse>

    @GET("radio/trending")
    fun getTrendingStations(
        @Header("X-App-Key") publicKey: String,
        @Query("countrycode") countryCode: String? = null,
        @Query("limit") limit: Int = 10
    ): retrofit2.Call<cx.aswin.boxcast.core.network.model.RadioStationResponse>
    @GET("radio/genre")
    fun getStationsByGenre(
        @Header("X-App-Key") publicKey: String,
        @Query("tag") tag: String,
        @Query("limit") limit: Int = 50
    ): retrofit2.Call<cx.aswin.boxcast.core.network.model.RadioStationResponse>

    @GET("radio/search")
    fun searchStations(
        @Header("X-App-Key") publicKey: String,
        @Query("q") query: String?,
        @Query("tag") tag: String?,
        @Query("country") country: String?,
        @Query("language") language: String?,
        @Query("limit") limit: Int = 50
    ): retrofit2.Call<cx.aswin.boxcast.core.network.model.RadioStationResponse>

}
