package android.util

import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser

/**
 * JVM unit-test stub for [android.util.Xml] backed by the real kxml2 pull parser, so the custom
 * [cx.aswin.boxlore.core.rss.RssFeedClient] parser can be exercised hermetically off-device.
 */
object Xml {
    @JvmStatic
    fun newPullParser(): XmlPullParser = KXmlParser()
}
