package com.ridvanozdemir.socialdiet.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.MutableContextWrapper
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.SecureRandom

object GoogleCredentialHelper {
    suspend fun requestIdToken(context: Context): String {
        val clientIdResource = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        require(clientIdResource != 0) {
            "Google ile giriş henüz Firebase Console'da yapılandırılmadı. Google sağlayıcısını etkinleştirip SHA-1 ekledikten sonra güncel google-services.json dosyasını projeye ekleyin."
        }

        val serverClientId = context.getString(clientIdResource)
        require(serverClientId.isNotBlank()) {
            "Google OAuth istemci kimliği bulunamadı. Firebase yapılandırmasını güncelleyin."
        }

        val activity = context.findActivity()
            ?: error("Google ile giriş ekranı açılamadı. Uygulamayı kapatıp yeniden açın.")

        val credentialManager = CredentialManager.create(activity)
        val mutableContext = MutableContextWrapper(activity)

        // The user explicitly tapped the "Google ile devam et" button, so use
        // Credential Manager's dedicated Sign in with Google button flow.
        val googleOption = GetSignInWithGoogleOption.Builder(serverClientId)
            .setNonce(generateSecureRandomNonce())
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()

        val result = try {
            credentialManager.getCredential(
                context = mutableContext,
                request = request
            )
        } catch (error: GetCredentialCancellationException) {
            throw IllegalStateException("Google hesabı seçimi iptal edildi.", error)
        } catch (error: GetCredentialException) {
            val detail = error.message?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName
            throw IllegalStateException("Google ile giriş açılamadı: $detail", error)
        }

        val credential = result.credential
        require(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            "Geçerli bir Google hesabı kimliği alınamadı."
        }

        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }

    private fun generateSecureRandomNonce(byteLength: Int = 32): String {
        val randomBytes = ByteArray(byteLength)
        SecureRandom().nextBytes(randomBytes)
        return Base64.encodeToString(
            randomBytes,
            Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
        )
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
