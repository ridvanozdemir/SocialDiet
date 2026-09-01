package com.ridvanozdemir.socialdiet.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
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

        // This is an explicit "Continue with Google" button, so use the
        // dedicated button flow rather than the Credential Manager bottom-sheet
        // account discovery flow. The button flow can also surface accounts that
        // need re-authentication or let the user add a Google account.
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        val result = try {
            CredentialManager.create(activity).getCredential(
                context = activity,
                request = request
            )
        } catch (error: GetCredentialCancellationException) {
            throw IllegalStateException("Google ile giriş iptal edildi.", error)
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
