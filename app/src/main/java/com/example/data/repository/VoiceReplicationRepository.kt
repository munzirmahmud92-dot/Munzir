package com.example.data.repository

import android.util.Log
import com.example.data.local.ReplicationStatus
import com.example.data.local.SecureVoiceIdStore
import com.example.data.local.VoiceProfileDao
import com.example.data.local.VoiceProfileEntity
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Url
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "VoiceReplicationRepo"

@JsonClass(generateAdapter = true)
data class VoiceReplicationApiResponse(
    val voiceId: String? = null,
    val status: String? = null,
    val message: String? = null
)

interface VoiceReplicationApiService {
    @Multipart
    @POST
    suspend fun enrollVoiceSample(
        @Url endpointUrl: String,
        @Part audioSample: MultipartBody.Part,
        @Part("voiceName") voiceName: okhttp3.RequestBody,
        @Part("languageCode") languageCode: okhttp3.RequestBody,
        @Part("consentConfirmed") consentConfirmed: okhttp3.RequestBody,
        @Part("ownershipType") ownershipType: okhttp3.RequestBody
    ): Response<VoiceReplicationApiResponse>
}

sealed interface ReplicationResult {
    data class Replicated(val voiceId: String, val message: String) : ReplicationResult
    data class Unavailable(val reason: String) : ReplicationResult
}

class VoiceReplicationRepository(
    private val voiceProfileDao: VoiceProfileDao,
    private val secureVoiceIdStore: SecureVoiceIdStore
) {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private val apiService: VoiceReplicationApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://localhost/") // Full URL supplied dynamically via @Url
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(VoiceReplicationApiService::class.java)
    }

    suspend fun replicateVoiceProfile(
        profile: VoiceProfileEntity,
        configuredEndpointUrl: String,
        isOnline: Boolean
    ): ReplicationResult = withContext(Dispatchers.IO) {
        if (!profile.consentConfirmed) {
            return@withContext ReplicationResult.Unavailable(
                "Explicit speaker consent confirmation is required before submitting any voice sample."
            )
        }

        val samplePath = profile.sampleFilePath
        val sampleFile = samplePath?.let { File(it) }
        if (sampleFile == null || !sampleFile.exists() || sampleFile.length() == 0L) {
            return@withContext ReplicationResult.Unavailable(
                "No valid local voice sample WAV file found for this profile."
            )
        }

        if (configuredEndpointUrl.isBlank()) {
            val honestMsg = "Personal neural voice replication is unavailable: the default Firebase AI Logic Gemini TTS API only provides prebuilt studio voices and does not support cloning custom speaker audio. Your sample and consent record are stored locally; configure an officially supported Voice Replication API endpoint in Settings if your organization hosts one."
            voiceProfileDao.updateVoice(
                profile.copy(
                    replicationStatus = ReplicationStatus.UNAVAILABLE.name,
                    replicationMessage = honestMsg
                )
            )
            return@withContext ReplicationResult.Unavailable(honestMsg)
        }

        if (!configuredEndpointUrl.startsWith("https://")) {
            val msg = "Replication endpoint must use HTTPS for secure voice sample transmission."
            voiceProfileDao.updateVoice(
                profile.copy(
                    replicationStatus = ReplicationStatus.UNAVAILABLE.name,
                    replicationMessage = msg
                )
            )
            return@withContext ReplicationResult.Unavailable(msg)
        }

        if (!isOnline) {
            return@withContext ReplicationResult.Unavailable(
                "Cannot contact Voice Replication API while offline or in Airplane Mode."
            )
        }

        try {
            val audioBody = sampleFile.asRequestBody("audio/wav".toMediaType())
            val audioPart = MultipartBody.Part.createFormData("sample", sampleFile.name, audioBody)
            val textMedia = "text/plain".toMediaType()

            val response = apiService.enrollVoiceSample(
                endpointUrl = configuredEndpointUrl,
                audioSample = audioPart,
                voiceName = profile.name.toRequestBody(textMedia),
                languageCode = profile.languageCode.toRequestBody(textMedia),
                consentConfirmed = profile.consentConfirmed.toString().toRequestBody(textMedia),
                ownershipType = profile.consentOwnerType.toRequestBody(textMedia)
            )

            if (response.isSuccessful) {
                val body = response.body()
                val returnedVoiceId = body?.voiceId?.takeIf { it.isNotBlank() }
                if (returnedVoiceId != null) {
                    secureVoiceIdStore.saveVoiceId(profile.id, returnedVoiceId)
                    val msg = body.message ?: "Voice profile replicated and encrypted voice ID saved."
                    voiceProfileDao.updateVoice(
                        profile.copy(
                            replicationStatus = ReplicationStatus.REPLICATED.name,
                            replicationMessage = msg,
                            hasEncryptedVoiceId = true
                        )
                    )
                    ReplicationResult.Replicated(returnedVoiceId, msg)
                } else {
                    val msg = "Replication endpoint responded without a valid voiceId."
                    voiceProfileDao.updateVoice(
                        profile.copy(
                            replicationStatus = ReplicationStatus.UNAVAILABLE.name,
                            replicationMessage = msg
                        )
                    )
                    ReplicationResult.Unavailable(msg)
                }
            } else {
                val errBody = response.errorBody()?.string()?.take(200).orEmpty()
                val msg = "Replication API rejected request (HTTP ${response.code()}): ${errBody.ifBlank { response.message() }}"
                voiceProfileDao.updateVoice(
                    profile.copy(
                        replicationStatus = ReplicationStatus.UNAVAILABLE.name,
                        replicationMessage = msg
                    )
                )
                ReplicationResult.Unavailable(msg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Voice replication request failed", e)
            val msg = "Replication API unreachable: ${e.localizedMessage ?: e.javaClass.simpleName}"
            voiceProfileDao.updateVoice(
                profile.copy(
                    replicationStatus = ReplicationStatus.UNAVAILABLE.name,
                    replicationMessage = msg
                )
            )
            ReplicationResult.Unavailable(msg)
        }
    }
}
