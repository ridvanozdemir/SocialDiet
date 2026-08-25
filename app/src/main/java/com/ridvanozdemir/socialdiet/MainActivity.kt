package com.ridvanozdemir.socialdiet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ridvanozdemir.socialdiet.ui.SocialDietApp
import com.ridvanozdemir.socialdiet.ui.theme.SocialDietTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SocialDietTheme {
                SocialDietApp()
            }
        }
    }
}
