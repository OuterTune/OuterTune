package com.dd3boh.outertune.playback

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.PodcastEntity
import com.dd3boh.outertune.db.entities.PodcastEpisodeEntity
import com.dd3boh.outertune.models.PodcastEpisodeMetadata
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit tests para PodcastDownloadUtil
 */
@ExperimentalCoroutinesApi
class PodcastDownloadUtilTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: MusicDatabase
    private lateinit var downloadUtil: DownloadUtil
    private lateinit var podcastDownloadUtil: PodcastDownloadUtil

    @Before
    fun setup() {
        // Crear base de datos en memoria para tests
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MusicDatabase::class.java
        ).allowMainThreadQueries()
            .build()

        // Mock del DownloadUtil
        downloadUtil = mockk(relaxed = true)

        // Inicializar PodcastDownloadUtil
        podcastDownloadUtil = PodcastDownloadUtil(
            context = ApplicationProvider.getApplicationContext(),
            database = database,
            downloadUtil = downloadUtil,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testInsertPodcast() = runTest {
        val podcast = PodcastEntity(
            id = "pod_001",
            title = "Test Podcast",
            feedUrl = "https://example.com/feed.xml",
            author = "Test Author"
        )

        database.podcastDao().insertPodcast(podcast)

        val retrieved = database.podcastDao().getPodcast(podcast.id).first()
        assert(retrieved != null)
        assert(retrieved?.title == "Test Podcast")
    }

    @Test
    fun testInsertEpisode() = runTest {
        val episode = PodcastEpisodeEntity(
            id = "ep_001",
            podcastId = "pod_001",
            title = "Test Episode",
            audioUrl = "https://example.com/episode.mp3",
            duration = 3600
        )

        database.podcastDao().insertEpisode(episode)

        val retrieved = database.podcastDao().getEpisode(episode.id).first()
        assert(retrieved != null)
        assert(retrieved?.title == "Test Episode")
    }

    @Test
    fun testDownloadEpisode() = runTest {
        val episode = PodcastEpisodeMetadata(
            id = "ep_001",
            podcastId = "pod_001",
            title = "Test Episode",
            audioUrl = "https://example.com/episode.mp3",
            duration = 3600
        )

        podcastDownloadUtil.downloadEpisode(episode)

        // Verify that downloadUtil.download() was called
        verify { downloadUtil.download(any()) }
    }

    @Test
    fun testDownloadMultipleEpisodes() = runTest {
        val episodes = listOf(
            PodcastEpisodeMetadata(
                id = "ep_001",
                podcastId = "pod_001",
                title = "Episode 1",
                audioUrl = "https://example.com/ep1.mp3",
                duration = 1800
            ),
            PodcastEpisodeMetadata(
                id = "ep_002",
                podcastId = "pod_001",
                title = "Episode 2",
                audioUrl = "https://example.com/ep2.mp3",
                duration = 1800
            )
        )

        podcastDownloadUtil.downloadEpisodes(episodes)

        verify { downloadUtil.download(any()) }
    }

    @Test
    fun testMarkEpisodeAsDownloaded() = runTest {
        val episode = PodcastEpisodeEntity(
            id = "ep_001",
            podcastId = "pod_001",
            title = "Test Episode",
            audioUrl = "https://example.com/episode.mp3",
            duration = 3600,
            isLocal = false
        )

        database.podcastDao().insertEpisode(episode)

        // Marcar como descargado
        database.podcastDao().updateEpisodeDownloadStatus(
            episodeId = episode.id,
            isLocal = true,
            localPath = "/path/to/episode.mp3",
            dateDownload = LocalDateTime.now()
        )

        val updated = database.podcastDao().getEpisode(episode.id).first()
        assert(updated?.isLocal == true)
        assert(updated?.localPath != null)
    }

    @Test
    fun testUpdateListeningProgress() = runTest {
        val episode = PodcastEpisodeEntity(
            id = "ep_001",
            podcastId = "pod_001",
            title = "Test Episode",
            audioUrl = "https://example.com/episode.mp3",
            duration = 3600,
            listeningProgress = 0
        )

        database.podcastDao().insertEpisode(episode)

        // Actualizar progreso
        database.podcastDao().updateListeningProgress(episode.id, 1800)

        val updated = database.podcastDao().getEpisode(episode.id).first()
        assert(updated?.listeningProgress == 1800)
    }

    @Test
    fun testMarkEpisodeAsListened() = runTest {
        val episode = PodcastEpisodeEntity(
            id = "ep_001",
            podcastId = "pod_001",
            title = "Test Episode",
            audioUrl = "https://example.com/episode.mp3",
            duration = 3600,
            listened = false
        )

        database.podcastDao().insertEpisode(episode)

        // Marcar como escuchado
        val now = LocalDateTime.now()
        database.podcastDao().markAsListened(episode.id, now)

        val updated = database.podcastDao().getEpisode(episode.id).first()
        assert(updated?.listened == true)
    }

    @Test
    fun testGetUnlistenedEpisodes() = runTest {
        val podcast = PodcastEntity(
            id = "pod_001",
            title = "Test Podcast",
            feedUrl = "https://example.com/feed.xml"
        )

        database.podcastDao().insertPodcast(podcast)

        // Insertar episodios
        database.podcastDao().insertEpisodes(
            listOf(
                PodcastEpisodeEntity(
                    id = "ep_001",
                    podcastId = "pod_001",
                    title = "Episode 1",
                    audioUrl = "https://example.com/ep1.mp3",
                    duration = 1800,
                    listened = false
                ),
                PodcastEpisodeEntity(
                    id = "ep_002",
                    podcastId = "pod_001",
                    title = "Episode 2",
                    audioUrl = "https://example.com/ep2.mp3",
                    duration = 1800,
                    listened = true
                )
            )
        )

        // Obtener episodios no escuchados
        val unlistened = database.podcastDao().getUnlistenedEpisodes("pod_001").first()
        assert(unlistened.size == 1)
        assert(unlistened[0].id == "ep_001")
    }

    @Test
    fun testGetDownloadedEpisodes() = runTest {
        val podcast = PodcastEntity(
            id = "pod_001",
            title = "Test Podcast",
            feedUrl = "https://example.com/feed.xml"
        )

        database.podcastDao().insertPodcast(podcast)

        // Insertar episodios
        database.podcastDao().insertEpisodes(
            listOf(
                PodcastEpisodeEntity(
                    id = "ep_001",
                    podcastId = "pod_001",
                    title = "Episode 1",
                    audioUrl = "https://example.com/ep1.mp3",
                    duration = 1800,
                    isLocal = true,
                    localPath = "/path/to/ep1.mp3"
                ),
                PodcastEpisodeEntity(
                    id = "ep_002",
                    podcastId = "pod_001",
                    title = "Episode 2",
                    audioUrl = "https://example.com/ep2.mp3",
                    duration = 1800,
                    isLocal = false
                )
            )
        )

        // Obtener episodios descargados
        val downloaded = database.podcastDao().getDownloadedEpisodes("pod_001").first()
        assert(downloaded.size == 1)
        assert(downloaded[0].id == "ep_001")
    }

    @Test
    fun testDeleteEpisode() = runTest {
        val episode = PodcastEpisodeEntity(
            id = "ep_001",
            podcastId = "pod_001",
            title = "Test Episode",
            audioUrl = "https://example.com/episode.mp3",
            duration = 3600
        )

        database.podcastDao().insertEpisode(episode)

        // Verify it was inserted
        var retrieved = database.podcastDao().getEpisode(episode.id).first()
        assert(retrieved != null)

        // Eliminar
        database.podcastDao().deleteEpisode(episode)

        // Verify it was deleted
        retrieved = database.podcastDao().getEpisode(episode.id).first()
        assert(retrieved == null)
    }

    @Test
    fun testSearchPodcasts() = runTest {
        database.podcastDao().insertPodcasts(
            listOf(
                PodcastEntity(
                    id = "pod_001",
                    title = "Tech Podcast",
                    feedUrl = "https://example.com/feed1.xml",
                    author = "John Doe"
                ),
                PodcastEntity(
                    id = "pod_002",
                    title = "Music Podcast",
                    feedUrl = "https://example.com/feed2.xml",
                    author = "Jane Smith"
                )
            )
        )

        // Search by title
        val results = database.podcastDao().searchPodcasts("Tech").first()
        assert(results.size == 1)
        assert(results[0].title == "Tech Podcast")
    }

    @Test
    fun testGetListenedEpisodeCount() = runTest {
        val podcast = PodcastEntity(
            id = "pod_001",
            title = "Test Podcast",
            feedUrl = "https://example.com/feed.xml"
        )

        database.podcastDao().insertPodcast(podcast)

        database.podcastDao().insertEpisodes(
            listOf(
                PodcastEpisodeEntity(
                    id = "ep_001",
                    podcastId = "pod_001",
                    title = "Episode 1",
                    audioUrl = "https://example.com/ep1.mp3",
                    duration = 1800,
                    listened = true
                ),
                PodcastEpisodeEntity(
                    id = "ep_002",
                    podcastId = "pod_001",
                    title = "Episode 2",
                    audioUrl = "https://example.com/ep2.mp3",
                    duration = 1800,
                    listened = true
                ),
                PodcastEpisodeEntity(
                    id = "ep_003",
                    podcastId = "pod_001",
                    title = "Episode 3",
                    audioUrl = "https://example.com/ep3.mp3",
                    duration = 1800,
                    listened = false
                )
            )
        )

        val count = database.podcastDao().getListenedEpisodeCount("pod_001").first()
        assert(count == 2)
    }
}
