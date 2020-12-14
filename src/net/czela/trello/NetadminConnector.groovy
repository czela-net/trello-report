package net.czela.trello

import groovy.json.JsonSlurper
import net.czela.trello.model.Card

/**
 * GraphQL connector to netadmin
 */
class NetadminConnector {
    String url
    String username
    String password

    NetadminConnector() {
        url = Helper.get("netadmin.api.url")
        username = Helper.get("netadmin.api.username")
        password = Helper.get("netadmin.api.password")
    }

    /**
     * @param query GraphQL request
     * @return JsonSlurper with response
     */
    def doQuery(String query) {
        URL apiUrl = new URL(url)
        HttpURLConnection http = apiUrl.openConnection()
        http.setDoOutput(true)
        http.setDoInput(true)
        http.setRequestMethod('POST')
        http.setRequestProperty("Content-Type", "application/json");
        http.setRequestProperty("Accept", "application/json");
        String secret = username + ":" + password
        http.setRequestProperty("Authorization", "Basic " + new String(Base64.getEncoder().encode(secret.getBytes("UTF-8"))));
        http.getOutputStream().write(query.bytes)
        int rc = http.getResponseCode()

        assert rc == 200

        return new JsonSlurper().parse(http.getInputStream())
    }

    def getAkceById(long id) {
        def query = """{"query":"query { actions(id: ${id}) {id, sekceid, nazev, stav, datum_schvaleni, datum_ukonceni, userid, cena, schvaleno }}"}"""
        return doQuery(query).data.actions[0]
    }

    def updateAkce(Map akce) {
        if (akce != null && akce.id > 0) {
            def orig = getAkceById(akce.id)
            assert orig != null, "Can not update akce.id: {akce.id} not exists!"
            Map map = [:]
            map.putAll(orig)
            map.putAll(akce)
            def params = map.collect({ entry ->
                def vv = (entry.value instanceof Number) ? "${entry.value}" : "\\\"${entry.value}\\\""
                "${entry.key}: ${vv}"
            }).join(", ")
            doQuery("""{"query":"mutation { updateAction(${params}) {id}}" }""")
        }
    }

    def createAkce(Card akce) {
        Map<String, Object> map = [
                'nazev': akce.name,
                'sekceid': akce.typeId,
                'userid': akce.userId,
                'cena': akce.budget as String,
        ]
        return createAkce(map)
    }

    Long createAkce(Map akce) {
        if (akce != null && akce.nazev != null && akce.sekceid != null && akce.userid != null && (akce.cena as Long) > 0) {
            def map = [
                    'stav': 0,
                    'obsah': "-",
                    'cena': "0",
                    'schvaleno': "-",
                    'ukonceno': "-",
                    'smlouvanutna': 0
            ]
            map.putAll(akce)
            def params = map.collect({ entry ->
                def vv = (entry.value instanceof Number) ? "${entry.value}" : "\\\"${entry.value}\\\""
                "${entry.key}: ${vv}"
            }).join(", ")
            def jsonAkce = doQuery("""{ "query" : "mutation { addAction( ${params} ) { id }}" }""")
            return jsonAkce.data.addAction.id as Long
        } else {
            println("WARN akce je spatne, neco chybi! $akce")
        }
    }

    static void main(String[] args) {
        def c = new NetadminConnector();

        c.createAkce([
                //'id': 174,
                'sekceid': 101,
                'nazev': "Test GraphQL v4",
                'userid': 1111,
        ])

        //def akceJson = c.getAkceById(172)

        //println(akceJson.data.actions[0])
    }
}
