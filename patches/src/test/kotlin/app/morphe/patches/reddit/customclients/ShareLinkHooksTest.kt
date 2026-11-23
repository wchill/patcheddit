package app.morphe.patches.reddit.customclients

import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class ShareLinkHooksTest {
    @Test
    fun keepsNewHostAndPathPairsSeparateFromStockViewFilters() {
        val android = "http://schemas.android.com/apk/res/android"
        val manifest = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse("""
            <manifest xmlns:android="$android"><application>
                <activity android:name=".Other"/>
                <activity android:name=".MainActivity">
                    <intent-filter><data android:scheme="http" android:host="www.reddit.com"/></intent-filter>
                </activity>
            </application></manifest>
        """.trimIndent().byteInputStream())
        addShareViewFilters(manifest, setOf("com.example.MainActivity", ".MainActivity"),
            ShareViewFilter("www.reddit.com", "path", "/media"),
            ShareViewFilter("matrix.redditspace.com", "pathPrefix", "/_matrix/media/"))

        val activities = manifest.getElementsByTagName("activity")
        val other = activities.item(0) as Element
        val filters = (activities.item(1) as Element).getElementsByTagName("intent-filter")
        assertEquals(0, other.getElementsByTagName("intent-filter").length)
        assertEquals(3, filters.length)
        val data = (0 until filters.length).map {
            val entries = (filters.item(it) as Element).getElementsByTagName("data")
            assertEquals(1, entries.length)
            entries.item(0) as Element
        }
        assertEquals(listOf("http", "https", "https"), data.map { it.getAttributeNS(android, "scheme") })
        assertEquals(listOf("www.reddit.com", "www.reddit.com", "matrix.redditspace.com"),
            data.map { it.getAttributeNS(android, "host") })
        assertEquals("/media", data[1].getAttributeNS(android, "path"))
        assertEquals("/_matrix/media/", data[2].getAttributeNS(android, "pathPrefix"))
    }
}
