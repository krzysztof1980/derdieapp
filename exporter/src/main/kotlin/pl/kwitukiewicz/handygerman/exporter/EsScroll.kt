package pl.kwitukiewicz.handygerman.exporter

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.BasicCredentialsProvider
import org.elasticsearch.client.Request
import org.elasticsearch.client.RestClient
import org.slf4j.LoggerFactory

/** Index populated by the wiktio companion plan; `_id`s are `%012d-%02d` page-order ids. */
const val ES_INDEX = "dewiktionary_entries"

private val log = LoggerFactory.getLogger("pl.kwitukiewicz.handygerman.exporter.EsScroll")

/**
 * Streams every document of [ES_INDEX] through [onDoc] via a scroll search
 * (initial `POST /_search?scroll=5m` size 2000, then `_search/scroll` until a page
 * comes back empty). Read-only against Elasticsearch: the final `DELETE /_search/scroll`
 * releases the search context, never a document.
 *
 * Elasticsearch 8.15 forbids fielddata access on `_id`, so there is no server-side sort:
 * delivery order is arbitrary and [reduce] sorts client-side — fixed-width zero-padded
 * ids make lexicographic order equal numeric order.
 */
fun scrollAll(host: String, user: String, onDoc: (id: String, sourceJson: String) -> Unit) {
    val credentials = BasicCredentialsProvider().apply {
        setCredentials(
            AuthScope.ANY,
            UsernamePasswordCredentials(user.substringBefore(':'), user.substringAfter(':', "")),
        )
    }
    RestClient.builder(HttpHost(host, 9200, "http"))
        .setHttpClientConfigCallback { it.setDefaultCredentialsProvider(credentials) }
        .setRequestConfigCallback { it.setSocketTimeout(120_000) }
        .build()
        .use { client ->
            val mapper = ObjectMapper()
            var scrollId: String? = null
            try {
                var hits = 1
                var docs = 0
                while (hits > 0) {
                    val page = if (scrollId == null) {
                        client.performRequest(
                            Request("POST", "/$ES_INDEX/_search").apply {
                                addParameter("scroll", "5m")
                                setJsonEntity("""{"size":2000,"query":{"match_all":{}}}""")
                            },
                        )
                    } else {
                        client.performRequest(
                            Request("POST", "/_search/scroll").apply {
                                setJsonEntity("""{"scroll":"5m","scroll_id":${mapper.writeValueAsString(scrollId)}}""")
                            },
                        )
                    }
                    page.entity.content!!.use { stream ->
                        val root = mapper.readTree(stream)
                        scrollId = root.path("_scroll_id").asText(scrollId)
                        val pageHits = root.path("hits").path("hits")
                        hits = pageHits.size()
                        docs += hits
                        pageHits.forEach { hit -> onDoc(hit.path("_id").asText(), hit.path("_source").toString()) }
                    }
                    log.info("scrolled {} docs", docs)
                }
            } finally {
                if (scrollId != null) {
                    runCatching {
                        client.performRequest(
                            Request("DELETE", "/_search/scroll").apply {
                                setJsonEntity("""{"scroll_id":[${mapper.writeValueAsString(scrollId)}]}""")
                            },
                        )
                    }
                }
            }
        }
}
