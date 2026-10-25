package com.example.localtwouserapp

import org.junit.Assert.*
import org.junit.Test

class ConnectionStatusTest {
    @Test fun firebaseDisconnectionDoesNotImplyNoInternet() {
        assertTrue(ConnectionStatus.disconnected(true).startsWith("Firebase unreachable"))
        assertTrue(ConnectionStatus.disconnected(false).startsWith("No internet connection"))
    }
}
