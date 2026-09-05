package com.example

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.initialize

class NavigatorApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    if (Firebase.initialize(this) != null) installAppCheckProvider()
  }
}
