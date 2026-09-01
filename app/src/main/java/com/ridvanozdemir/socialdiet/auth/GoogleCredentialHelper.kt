package com.ridvanozdemir.socialdiet.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.MutableContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

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

        suspend fun requestGoogleCredential(authorizedAccountsOnly: Boolean): GetCredentialResponse {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(authorizedAccountsOnly)
                .setServerClientId(serverClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            return credentialManager.getCredential(
                context = mutableContext,
                request = request
            )
        }

        val result = try {
            try {
                // Prefer an account that has already authorized SocialDiet.
                requestGoogleCredential(authorizedAccountsOnly = true)
            } catch (_: NoCredentialException) {
                // First-time sign-in: show all Google accounts on the device.
                requestGoogleCredential(authorizedAccountsOnly = false)
            }
        } catch (error: GetCredentialCancellationException) {
            throw IllegalStateException("Google hesabı seçimi iptal edildi.", error)
        } catch (error: NoCredentialException) {
            throw IllegalStateException(
                "Kullanılabilir Google hesabı bulunamadı. Telefonda bir Google hesabının açık olduğundan ve Google Play Hizmetleri'nin güncel olduğundan emin olun.",
                error
            )
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

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
