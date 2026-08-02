package com.filtertube.app.data

import org.junit.Assert.assertNotNull
import org.junit.Test

class FirestoreRuntimeDependencyTest {
    @Test
    fun protobufByteStringIsAvailableToFirestoreAtRuntime() {
        assertNotNull(Class.forName("com.google.protobuf.ByteString"))
    }
}
