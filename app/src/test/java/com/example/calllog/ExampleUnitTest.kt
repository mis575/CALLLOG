package com.example.calllog

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonStr = """
        {
            "_id": "6a7457bdb0b67f04634fc9b9",
            "id": "38637",
            "address": "",
            "companyName": "",
            "contactName": "",
            "date": 1786009510352,
            "description": "",
            "duration": 0,
            "isSaved": false,
            "name": "Muski",
            "number": "8882446799",
            "status": "Select Status",
            "syncedBy": "Rajkumari",
            "type": 2
        }
        """.trimIndent()
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val record = json.decodeFromString<com.example.calllog.data.CallRecord>(jsonStr)
        System.out.println("PARSED: " + record)
        assertFalse(record.isSaved)
    }
}