package com.ridvanozdemir.socialdiet.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
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

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = CredentialManager.create(context).getCredential(
            context = context,
            request = request
        )
        val credential = result.credential
        require(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            "Geçerli bir Google hesabı kimliği alınamadı."
        }

        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
}
