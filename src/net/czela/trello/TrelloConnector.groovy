package net.czela.trello

import groovy.json.JsonSlurper

class TrelloConnector {
    String myKey = 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx'
    String myToken = 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx'

    TrelloConnector(String myKey, String myToken) {
        this.myKey = myKey
        this.myToken = myToken
    }

    String urlPrefix = "https://api.trello.com/1/"
    String urlSuffix = "key=${myKey}&token=${myToken}"


    def trelloGet(String query) {
        String qm = query.contains("?") ? "&" : "?"
        def link = urlPrefix + query + qm + urlSuffix
        URL apiUrl = new URL(link)
        return new JsonSlurper().parse(apiUrl)
    }

    static def getStream(String url, File outFile) {
        if (outFile.exists() && outFile.size() > 100) {
            println "  file exists. skip download."
            return
        }

        println "  download file from $url"
        if (outFile.exists()) outFile.delete()
        InputStream is = (new URL(url)).openStream()
        outFile << is
        is.close()
    }

    def trelloPut(String query, String body = null) {
        String qm = query.contains("?") ? "&" : "?"
        def link = urlPrefix + query + qm + urlSuffix
        URL apiUrl = new URL(link)
        HttpURLConnection http = apiUrl.openConnection() as HttpURLConnection
        http.setRequestMethod('PUT')
        http.setRequestProperty("Content-Type", "application/json")
        http.setRequestProperty("Accept", "application/json")
        http.setDoInput(true)
        if (body) {
            http.setDoOutput(true)
            def os = http.outputStream
            os.write(body.getBytes())
            os.flush()
            os.close()
        } else {
            http.setDoOutput(false)
        }
        int rc = http.getResponseCode()
        return rc == 200
    }

    def trelloPost(String query, String body = null) {
        String qm = query.contains("?") ? "&" : "?"
        def link = urlPrefix + query + qm + urlSuffix
        URL apiUrl = new URL(link)
        HttpURLConnection http = apiUrl.openConnection() as HttpURLConnection
        http.setRequestMethod('POST')
        http.setRequestProperty("Content-Type", "application/json")
        http.setRequestProperty("Accept", "application/json")
        http.setDoInput(true)
        if (body) {
            http.setDoOutput(true)
            def os = http.outputStream
            os.write(body.getBytes())
            os.flush()
            os.close()
        } else {
            http.setDoOutput(false)
        }
        int rc = http.getResponseCode()
        return rc == 200
    }


    private def customFieldsMap = [:]

    def getCustomFieldId(def cardId, String label) {
        def key = labelTransform(label)
        def id = customFieldsMap.get(key)
        if (id != null) {
            return id
        } else {
            def jsonArray = trelloGet("cards/${cardId}/customFieldItems")
            jsonArray.each { json ->
                def itemId = json.idCustomField
                def cfJson = trelloGet("customFields/${id}")
                def itemKey = labelTransform(cfJson.name)
                customFieldsMap.put(itemKey, itemId)
            }
        }

        id = customFieldsMap.get(key)
        assert id != null, "Can not find customField named $label!"
        return id
    }

    static String labelTransform(String label) {
        return label.toUpperCase().replaceAll(/[^A-Z0-9_]+/,'_').toString()
    }

    void addComment(String cardId, String comment) {
        assert comment != null && cardId != null
        comment = java.net.URLEncoder.encode(comment, "UTF-8")
        trelloPost("cards/${cardId}/actions/comments?text=${comment}")
    }

    void moveCardToList(String cardId, String listId) {
        assert listId != null && cardId != null
        trelloPut("cards/${cardId}?idList=${listId}&pos=bottom")
    }
}
