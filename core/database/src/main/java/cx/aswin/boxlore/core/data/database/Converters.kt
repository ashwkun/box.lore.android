package cx.aswin.boxlore.core.data.database

import androidx.room.TypeConverter
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.model.Transcript
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromEpisode(episode: Episode?): String? {
        return episode?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toEpisode(episodeString: String?): Episode? {
        return episodeString?.let {
            try {
                gson.fromJson(it, Episode::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }

    @TypeConverter
    fun fromPersons(persons: List<Person>?): String? = persons?.let(gson::toJson)

    @TypeConverter
    fun toPersons(value: String?): List<Person>? = value?.let {
        runCatching {
            gson.fromJson<List<Person>>(
                it,
                object : TypeToken<List<Person>>() {}.type,
            )
        }.getOrNull()
    }

    @TypeConverter
    fun fromTranscripts(transcripts: List<Transcript>?): String? = transcripts?.let(gson::toJson)

    @TypeConverter
    fun toTranscripts(value: String?): List<Transcript>? = value?.let {
        runCatching {
            gson.fromJson<List<Transcript>>(
                it,
                object : TypeToken<List<Transcript>>() {}.type,
            )
        }.getOrNull()
    }
}
