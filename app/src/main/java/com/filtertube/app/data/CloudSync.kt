package com.filtertube.app.data

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Firebase-backed account backup.
 *
 * The files downloaded to MediaStore stay on the phone. Firestore stores their
 * video metadata, so a restored account can show the item and download it again.
 */
object CloudSync {
    private const val TAG = "CloudSync"
    private const val WORK_NAME_PREFIX = "filtertube-cloud-upload"
    internal const val WORK_UID = "firebase_uid"
    internal const val WORK_INCLUDE_PROFILE = "include_profile"
    private const val BATCH_SIZE = 450
    private const val MAX_SEARCH_QUERY_CHARS = 200
    private const val MAX_LOCAL_SUBSCRIPTIONS = 500
    private const val MAX_LOCAL_SUBSCRIPTION_CHARS = 100
    private const val MAX_YOUTUBE_SUBSCRIPTIONS = 250
    private const val MAX_SUBSCRIPTION_TITLE_CHARS = 150
    private const val MAX_SUBSCRIPTION_THUMBNAIL_CHARS = 384
    private const val MAX_PLAYLIST_VIDEOS = 250

    private fun uid(): String? =
        FirebaseAuth.getInstance().currentUser?.takeIf { it.isEmailVerified }?.uid

    private fun ensureSyncSession(expectedUid: String, generation: Long) {
        val current = FirebaseAuth.getInstance().currentUser
        if (
            current?.uid != expectedUid ||
            !current.isEmailVerified ||
            AccountDataGuard.generation() != generation
        ) {
            throw AccountChangedDuringSyncException()
        }
    }

    suspend fun signInOrRegister(email: String, password: String, settings: SettingsStore): Boolean =
        FirebaseAccount.signInOrRegister(email, password, settings).ok

    fun signOut(context: Context, settings: SettingsStore) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
            ?: settings.cloudUid.takeIf { it.isNotBlank() }
        if (userId != null) cancelUploads(context, userId)
        FirebaseAccount.signOut(context, settings)
    }

    /** Queues one network-aware upload; rapid local changes are coalesced. */
    fun enqueueUpload(context: Context, includeProfile: Boolean = false) {
        val userId = uid() ?: return
        val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setInputData(workDataOf(
                WORK_UID to userId,
                WORK_INCLUDE_PROFILE to includeProfile,
            ))
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            workName(userId, includeProfile),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancelUploads(context: Context, userId: String) {
        if (userId.isBlank()) return
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelUniqueWork(workName(userId, includeProfile = false))
        workManager.cancelUniqueWork(workName(userId, includeProfile = true))
    }

    private fun workName(userId: String, includeProfile: Boolean) =
        "$WORK_NAME_PREFIX-${if (includeProfile) "profile" else "library"}-$userId"

    /**
     * Pulls first and uploads only after a complete successful merge. This is
     * the only bidirectional entry point used by account/login UI.
     */
    suspend fun synchronize(context: Context, settings: SettingsStore): Boolean {
        val userId = uid() ?: return false
        if (!pullCloudData(context, settings, userId)) return false
        val uploaded = syncUserProfile(context, settings, userId)
        if (!uploaded && uid() == userId) enqueueUpload(context, includeProfile = true)
        return uploaded
    }

    /** Uploads the real local profile and library, never the old placeholder lists. */
    suspend fun syncUserProfile(
        context: Context,
        settings: SettingsStore,
        expectedUid: String? = null,
        includeProfile: Boolean = true,
    ): Boolean {
        val userId = uid() ?: return false
        if (expectedUid != null && expectedUid != userId) return false
        val authUser = FirebaseAuth.getInstance().currentUser
            ?.takeIf { it.uid == userId && it.isEmailVerified }
            ?: return false
        val verifiedEmail = authUser.email?.trim() ?: return false
        settings.bindAccountDataOwner(userId, verifiedEmail)
        val generation = AccountDataGuard.generation()
        return runCatching {
            val store = LibraryStore(context)
            val local = AccountDataGuard.withLock {
                ensureSyncSession(userId, generation)
                settings.userEmail = verifiedEmail
                LocalSyncSnapshot(
                    profileName = settings.userName.trim().take(80),
                    profileGender = settings.userGender,
                    filterLevel = settings.filterLevel.coerceIn(1, 3),
                    onboardingDone = settings.onboardingDone,
                    searchHistory = settings.getSearchHistory(),
                    localSubscriptions = store.localSubscriptions().toList(),
                    youtubeSubscriptions = store.subscriptions(),
                    likes = store.likes(),
                    downloads = store.downloads(),
                    localHistory = store.localHistory(),
                    youtubeLikes = store.youtubeLikes(),
                    youtubeHistory = store.history(),
                    recommendations = store.recommendations(),
                    newVideos = store.newVideos(),
                    playlists = store.playlists(),
                )
            }
            ensureSyncSession(userId, generation)
            val db = FirebaseFirestore.getInstance()
            val user = db.collection("users").document(userId)
            val now = System.currentTimeMillis()

            if (includeProfile) {
                val profileGender = local.profileGender
                    .takeIf { it == "male" || it == "female" }
                    ?: return@runCatching false
                if (local.profileName.isEmpty()) return@runCatching false
                saveDocumentIfChanged(
                    user.collection("profile").document("main"),
                    mapOf(
                        "name" to local.profileName,
                        "email" to verifiedEmail,
                        "gender" to profileGender,
                        "filterLevel" to local.filterLevel,
                        "onboardingDone" to local.onboardingDone,
                    ),
                    expectedUid = userId,
                    generation = generation,
                )
            }

            val stateReference = user.collection("library").document("state")
            ensureSyncSession(userId, generation)
            val existingState = stateReference.get().await()
            ensureSyncSession(userId, generation)
            val searches = mergeStrings(
                local.searchHistory,
                stringList(existingState.get("searchHistory")),
            )
            val localSubscriptions = mergeAllStrings(
                local.localSubscriptions,
                stringList(existingState.get("localSubscriptions")),
            )
            val remoteYoutubeSubscriptions = (existingState.get("youtubeSubscriptions") as? List<*>)
                ?.mapNotNull { subChannelFromMap(it as? Map<*, *>) }
                .orEmpty()
            val youtubeSubscriptions = mergeSubChannels(
                local.youtubeSubscriptions,
                remoteYoutubeSubscriptions,
            ).take(MAX_YOUTUBE_SUBSCRIPTIONS)

            saveDocumentIfChanged(
                stateReference,
                mapOf(
                    "schemaVersion" to 2,
                    "searchHistory" to searches,
                    "localSubscriptions" to localSubscriptions,
                    "youtubeSubscriptions" to youtubeSubscriptions.map(::subChannelMap),
                ),
                existingState,
                userId,
                generation,
            )

            syncVideos(db, user, "likes", local.likes, now, userId, generation)
            syncVideos(db, user, "downloads", local.downloads, now, userId, generation)
            syncVideos(db, user, "localHistory", local.localHistory, now, userId, generation)
            syncVideos(db, user, "youtubeLikes", local.youtubeLikes, now, userId, generation)
            syncVideos(db, user, "youtubeHistory", local.youtubeHistory, now, userId, generation)
            syncVideos(db, user, "recommendations", local.recommendations, now, userId, generation)
            syncVideos(db, user, "newVideos", local.newVideos, now, userId, generation)
            syncPlaylists(db, user, local.playlists, now, userId, generation)
            true
        }.getOrElse {
            if (it is CancellationException) throw it
            if (it is AccountChangedDuringSyncException) return@getOrElse false
            Log.e(TAG, "sync failed", it)
            false
        }
    }

    /**
     * Merges a cloud backup into this device. The caller should upload after a
     * successful pull so items that existed only locally are also preserved.
     */
    suspend fun pullCloudData(
        context: Context,
        settings: SettingsStore,
        expectedUid: String? = null,
    ): Boolean {
        val userId = uid() ?: return false
        if (expectedUid != null && expectedUid != userId) return false
        val verifiedEmail = FirebaseAuth.getInstance().currentUser
            ?.takeIf { it.uid == userId && it.isEmailVerified }
            ?.email
            ?.trim()
            ?: return false
        settings.bindAccountDataOwner(userId, verifiedEmail)
        val generation = AccountDataGuard.generation()
        return runCatching {
            ensureSyncSession(userId, generation)
            val db = FirebaseFirestore.getInstance()
            val user = db.collection("users").document(userId)
            val store = LibraryStore(context)
            val profile = user.collection("profile").document("main").get().await()
            ensureSyncSession(userId, generation)
            val state = user.collection("library").document("state").get().await()
            ensureSyncSession(userId, generation)

            // Complete every remote read before mutating local storage. A
            // partial network failure must leave the device untouched.
            val cloudLikes = loadVideos(user, "likes", userId, generation)
            val cloudDownloads = loadVideos(user, "downloads", userId, generation)
            val cloudLocalHistory = loadVideos(user, "localHistory", userId, generation)
            val cloudYoutubeLikes = loadVideos(user, "youtubeLikes", userId, generation)
            val cloudYoutubeHistory = loadVideos(user, "youtubeHistory", userId, generation)
            val cloudRecommendations = loadVideos(user, "recommendations", userId, generation)
            val cloudNewVideos = loadVideos(user, "newVideos", userId, generation)
            val cloudPlaylists = loadPlaylists(user, userId, generation)

            AccountDataGuard.withLock {
                ensureSyncSession(userId, generation)
                profile.getString("name")?.let { settings.userName = it }
                settings.userEmail = verifiedEmail
                profile.getString("gender")?.let { settings.userGender = it }
                profile.getLong("filterLevel")?.toInt()?.let { settings.filterLevel = it }
                if (profile.getBoolean("onboardingDone") == true) {
                    settings.onboardingDone = true
                }

                if (state.exists()) {
                    val searches = stringList(state.get("searchHistory"))
                    settings.replaceSearchHistory(
                        mergeStrings(settings.getSearchHistory(), searches),
                    )
                    val subscriptions = stringList(state.get("localSubscriptions"))
                    store.replaceLocalSubscriptions(
                        store.localSubscriptions() + subscriptions,
                    )
                    val youtubeSubscriptions =
                        (state.get("youtubeSubscriptions") as? List<*>)
                            ?.mapNotNull { subChannelFromMap(it as? Map<*, *>) }
                            .orEmpty()
                    store.replaceSubscriptions(
                        mergeSubChannels(
                            store.subscriptions(),
                            youtubeSubscriptions,
                        ),
                    )
                }

                store.replaceLikes(mergeVideos(store.likes(), cloudLikes))
                store.replaceDownloads(mergeVideos(store.downloads(), cloudDownloads))
                store.replaceLocalHistory(
                    mergeVideos(store.localHistory(), cloudLocalHistory),
                )
                store.replaceYoutubeLikes(
                    mergeVideos(store.youtubeLikes(), cloudYoutubeLikes),
                )
                store.replaceHistory(
                    mergeVideos(store.history(), cloudYoutubeHistory),
                )
                store.replaceRecommendations(
                    mergeVideos(store.recommendations(), cloudRecommendations),
                )
                store.replaceNewVideos(
                    mergeVideos(store.newVideos(), cloudNewVideos),
                )
                store.replacePlaylists(
                    mergePlaylists(store.playlists(), cloudPlaylists),
                )
                ensureSyncSession(userId, generation)
                true
            }
        }.getOrElse {
            if (it is CancellationException) throw it
            if (it is AccountChangedDuringSyncException) return@getOrElse false
            Log.e(TAG, "pull failed", it)
            false
        }
    }

    private fun itemsCollection(user: DocumentReference) =
        user.collection("library").document("items").collection("entries")

    private fun playlistsCollection(user: DocumentReference) =
        user.collection("library").document("playlists").collection("entries")

    private suspend fun syncVideos(
        db: FirebaseFirestore,
        user: DocumentReference,
        kind: String,
        videos: List<Video>,
        now: Long,
        expectedUid: String,
        generation: Long,
    ) {
        val collection = itemsCollection(user)
        val local = videos
            .map { it.copy(id = it.id.trim().take(64)) }
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
        ensureSyncSession(expectedUid, generation)
        val remote = collection.whereEqualTo("kind", kind).get().await()
        ensureSyncSession(expectedUid, generation)
        val remoteByDocumentId = remote.documents.associateBy { it.id }
        val mutations = local.mapNotNull { video ->
            val documentId = itemDocumentId(kind, video.id)
            val videoData = videoMap(video)
            val existingVideo = remoteByDocumentId[documentId]?.get("video") as? Map<*, *>
            if (valuesEquivalent(existingVideo, videoData)) null else Mutation(
                collection.document(documentId),
                mapOf(
                    "kind" to kind,
                    "video" to videoData,
                    "updatedAtMillis" to now,
                ),
            )
        }
        // Absence on one device is not proof of deletion. Never delete remote
        // items during a normal upload; explicit tombstones can be added later.
        commitMutations(db, mutations, expectedUid, generation)
    }

    private suspend fun syncPlaylists(
        db: FirebaseFirestore,
        user: DocumentReference,
        playlists: List<Playlist>,
        now: Long,
        expectedUid: String,
        generation: Long,
    ) {
        val collection = playlistsCollection(user)
        val local = playlists
            .map { it.copy(name = it.name.trim().take(120)) }
            .filter { it.name.isNotBlank() }
            .distinctBy { it.name }
        ensureSyncSession(expectedUid, generation)
        val remote = collection.get().await()
        ensureSyncSession(expectedUid, generation)
        val remoteByDocumentId = remote.documents.associateBy { it.id }
        val mutations = local.mapNotNull { playlist ->
            val documentId = playlistDocumentId(playlist.name)
            val remoteVideos = (remoteByDocumentId[documentId]?.get("videos") as? List<*>)
                ?.mapNotNull { videoFromMap(it as? Map<*, *>) }
                .orEmpty()
            val videos = mergeVideos(playlist.videos, remoteVideos)
                .take(MAX_PLAYLIST_VIDEOS)
                .map(::playlistVideoMap)
            val existingVideos = remoteByDocumentId[documentId]?.get("videos")
            if (valuesEquivalent(existingVideos, videos)) null else Mutation(
                collection.document(documentId),
                mapOf(
                    "name" to playlist.name,
                    "videos" to videos,
                    "updatedAtMillis" to now,
                ),
            )
        }
        commitMutations(db, mutations, expectedUid, generation)
    }

    private suspend fun commitMutations(
        db: FirebaseFirestore,
        mutations: List<Mutation>,
        expectedUid: String,
        generation: Long,
    ) {
        mutations.chunked(BATCH_SIZE).forEach { chunk ->
            ensureSyncSession(expectedUid, generation)
            val batch = db.batch()
            chunk.forEach { mutation ->
                batch.set(mutation.reference, mutation.data)
            }
            batch.commit().await()
            ensureSyncSession(expectedUid, generation)
        }
    }

    private suspend fun saveDocumentIfChanged(
        reference: DocumentReference,
        data: Map<String, Any>,
        knownSnapshot: DocumentSnapshot? = null,
        expectedUid: String,
        generation: Long,
    ) {
        ensureSyncSession(expectedUid, generation)
        val existing = knownSnapshot ?: reference.get().await()
        ensureSyncSession(expectedUid, generation)
        val expectedKeys = data.keys + "updatedAtMillis"
        val existingData = existing.data
        if (
            existingData != null &&
            existingData.keys == expectedKeys &&
            existing.get("updatedAtMillis") is Number &&
            data.all { (key, value) -> valuesEquivalent(existing.get(key), value) }
        ) {
            return
        }
        ensureSyncSession(expectedUid, generation)
        reference.set(data + ("updatedAtMillis" to System.currentTimeMillis())).await()
        ensureSyncSession(expectedUid, generation)
    }

    private suspend fun loadVideos(
        user: DocumentReference,
        kind: String,
        expectedUid: String,
        generation: Long,
    ): List<Video> {
        ensureSyncSession(expectedUid, generation)
        val documents =
            itemsCollection(user).whereEqualTo("kind", kind).get().await().documents
        ensureSyncSession(expectedUid, generation)
        return documents.mapNotNull { document ->
            videoFromMap(document.get("video") as? Map<*, *>)
        }
    }

    private suspend fun loadPlaylists(
        user: DocumentReference,
        expectedUid: String,
        generation: Long,
    ): List<Playlist> {
        ensureSyncSession(expectedUid, generation)
        val documents = playlistsCollection(user).get().await().documents
        ensureSyncSession(expectedUid, generation)
        return documents.mapNotNull { document ->
            val name = document.getString("name")?.trim().orEmpty()
            if (name.isBlank()) return@mapNotNull null
            val videos = (document.get("videos") as? List<*>)
                ?.mapNotNull { videoFromMap(it as? Map<*, *>) }
                .orEmpty()
            Playlist(name, videos)
        }
    }

    private fun videoMap(video: Video): Map<String, Any> = mapOf(
        "id" to video.id.take(64),
        "title" to video.title.take(500),
        "channelName" to video.channelName.take(200),
        "channelId" to video.channelId.take(100),
        "thumbnailUrl" to video.thumbnailUrl.take(2_048),
        "publishedAt" to video.publishedAt,
    )

    /**
     * Playlist videos share one Firestore document, so their fields use a
     * tighter cap than standalone library entries to stay below 1 MiB.
     */
    private fun playlistVideoMap(video: Video): Map<String, Any> = mapOf(
        "id" to video.id.take(64),
        "title" to video.title.take(200),
        "channelName" to video.channelName.take(100),
        "channelId" to video.channelId.take(100),
        "thumbnailUrl" to video.thumbnailUrl.take(384),
        "publishedAt" to video.publishedAt,
    )

    private fun videoFromMap(data: Map<*, *>?): Video? {
        val id = data?.get("id") as? String ?: return null
        if (id.isBlank()) return null
        return Video(
            id = id,
            title = data["title"] as? String ?: "",
            channelName = data["channelName"] as? String ?: "",
            channelId = data["channelId"] as? String ?: "",
            thumbnailUrl = data["thumbnailUrl"] as? String ?: "",
            publishedAt = (data["publishedAt"] as? Number)?.toLong() ?: 0L,
        )
    }

    private fun subChannelMap(channel: SubChannel): Map<String, Any> = mapOf(
        "channelId" to channel.channelId.trim().take(MAX_LOCAL_SUBSCRIPTION_CHARS),
        "title" to channel.title.trim().take(MAX_SUBSCRIPTION_TITLE_CHARS),
        "thumbnailUrl" to channel.thumbnailUrl.trim().take(MAX_SUBSCRIPTION_THUMBNAIL_CHARS),
    )

    private fun subChannelFromMap(data: Map<*, *>?): SubChannel? {
        val channelId = (data?.get("channelId") as? String)
            ?.trim()
            ?.take(MAX_LOCAL_SUBSCRIPTION_CHARS)
            ?: return null
        if (channelId.isBlank()) return null
        return SubChannel(
            channelId = channelId,
            title = (data["title"] as? String)
                ?.trim()
                ?.take(MAX_SUBSCRIPTION_TITLE_CHARS)
                .orEmpty(),
            thumbnailUrl = (data["thumbnailUrl"] as? String)
                ?.trim()
                ?.take(MAX_SUBSCRIPTION_THUMBNAIL_CHARS)
                .orEmpty(),
        )
    }

    private fun itemDocumentId(kind: String, id: String) = "$kind-${documentPart(id)}"
    private fun playlistDocumentId(name: String) = "playlist-${documentPart(name)}"

    private fun documentPart(value: String): String = Base64.encodeToString(
        value.toByteArray(Charsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )

    private fun stringList(value: Any?): List<String> =
        (value as? List<*>)?.mapNotNull { it as? String }?.filter { it.isNotBlank() }.orEmpty()

    private fun mergeStrings(local: List<String>, remote: List<String>): List<String> {
        val merged = LinkedHashSet<String>()
        local.map { it.trim().take(MAX_SEARCH_QUERY_CHARS) }
            .filter { it.isNotBlank() }
            .forEach { merged += it }
        remote.map { it.trim().take(MAX_SEARCH_QUERY_CHARS) }
            .filter { it.isNotBlank() }
            .forEach { merged += it }
        return merged.take(20)
    }

    private fun mergeAllStrings(local: Collection<String>, remote: Collection<String>): List<String> {
        val merged = LinkedHashSet<String>()
        local.map { it.trim().take(MAX_LOCAL_SUBSCRIPTION_CHARS) }
            .filter { it.isNotBlank() }
            .forEach { merged += it }
        remote.map { it.trim().take(MAX_LOCAL_SUBSCRIPTION_CHARS) }
            .filter { it.isNotBlank() }
            .forEach { merged += it }
        return merged.take(MAX_LOCAL_SUBSCRIPTIONS)
    }

    private fun mergeVideos(local: List<Video>, remote: List<Video>): List<Video> {
        val merged = LinkedHashMap<String, Video>()
        (local + remote).filter { it.id.isNotBlank() }.forEach { video ->
            merged.putIfAbsent(video.id, video)
        }
        return merged.values.toList()
    }

    private fun mergeSubChannels(local: List<SubChannel>, remote: List<SubChannel>): List<SubChannel> {
        val merged = LinkedHashMap<String, SubChannel>()
        (local + remote).forEach { channel ->
            val normalized = SubChannel(
                channelId = channel.channelId.trim().take(MAX_LOCAL_SUBSCRIPTION_CHARS),
                title = channel.title.trim().take(MAX_SUBSCRIPTION_TITLE_CHARS),
                thumbnailUrl =
                    channel.thumbnailUrl.trim().take(MAX_SUBSCRIPTION_THUMBNAIL_CHARS),
            )
            if (normalized.channelId.isNotBlank()) {
                merged.putIfAbsent(normalized.channelId, normalized)
            }
        }
        return merged.values.toList()
    }

    private fun mergePlaylists(local: List<Playlist>, remote: List<Playlist>): List<Playlist> {
        val merged = LinkedHashMap<String, Playlist>()
        (local + remote).filter { it.name.isNotBlank() }.forEach { playlist ->
            val existing = merged[playlist.name]
            merged[playlist.name] = if (existing == null) playlist else Playlist(
                playlist.name,
                mergeVideos(existing.videos, playlist.videos),
            )
        }
        return merged.values.toList()
    }

    private fun valuesEquivalent(left: Any?, right: Any?): Boolean = when {
        left is Number && right is Number -> left.toDouble() == right.toDouble()
        left is Map<*, *> && right is Map<*, *> ->
            left.size == right.size && right.all { (key, value) ->
                left.containsKey(key) && valuesEquivalent(left[key], value)
            }
        left is List<*> && right is List<*> ->
            left.size == right.size && left.indices.all { index ->
                valuesEquivalent(left[index], right[index])
            }
        else -> left == right
    }

    private data class LocalSyncSnapshot(
        val profileName: String,
        val profileGender: String,
        val filterLevel: Int,
        val onboardingDone: Boolean,
        val searchHistory: List<String>,
        val localSubscriptions: List<String>,
        val youtubeSubscriptions: List<SubChannel>,
        val likes: List<Video>,
        val downloads: List<Video>,
        val localHistory: List<Video>,
        val youtubeLikes: List<Video>,
        val youtubeHistory: List<Video>,
        val recommendations: List<Video>,
        val newVideos: List<Video>,
        val playlists: List<Playlist>,
    )

    private class AccountChangedDuringSyncException : IllegalStateException()

    private data class Mutation(val reference: DocumentReference, val data: Map<String, Any>)
}

class CloudSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val expectedUid = inputData.getString(CloudSync.WORK_UID).orEmpty()
        val includeProfile = inputData.getBoolean(CloudSync.WORK_INCLUDE_PROFILE, false)
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (expectedUid.isBlank() || currentUid != expectedUid) return Result.success()
        val synced = CloudSync.syncUserProfile(
            applicationContext,
            SettingsStore(applicationContext),
            expectedUid,
            includeProfile,
        )
        return if (synced) {
            Result.success()
        } else if (FirebaseAuth.getInstance().currentUser?.uid != expectedUid) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
