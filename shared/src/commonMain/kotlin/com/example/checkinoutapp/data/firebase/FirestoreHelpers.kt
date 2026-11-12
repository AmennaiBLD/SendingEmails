package com.example.checkinoutapp.data.firebase

import dev.gitlive.firebase.firestore.Timestamp

/** Firestore stores instants as Timestamps; the app's models carry epoch milliseconds. */
internal fun Timestamp.toMillis(): Long = seconds * 1000 + nanoseconds / 1_000_000

internal fun millisTimestamp(millis: Long): Timestamp = Timestamp(millis / 1000, ((millis % 1000) * 1_000_000).toInt())
