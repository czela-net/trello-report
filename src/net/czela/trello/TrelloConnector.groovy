package net.czela.trello

import groovy.json.JsonSlurper

public class TrelloConnector {
    String myKey = 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx';
    String myToken = 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx';

    TrelloConnector(String myKey, String myToken) {
        this.myKey = myKey
        this.myToken = myToken
    }

    String urlPrefix = "https://api.trello.com/1/";
    String urlSuffix = "key=${myKey}&token=${myToken}";


    def trelloGet(String query) {
        String qm = query.contains("?") ? "&" : "?"
        def link = urlPrefix + query + qm + urlSuffix;
        URL apiUrl = new URL(link);
        return new JsonSlurper().parse(apiUrl);
    }

    def getStream(String url, File outFile) {
        if (outFile.exists()) outFile.delete()
        InputStream is = (new URL(url)).openStream()
        outFile << is
        is.close();
    }

    def trelloPut(String query) {
        String qm = query.contains("?") ? "&" : "?"
        def link = urlPrefix + query + qm + urlSuffix;
        URL apiUrl = new URL(link);
        HttpURLConnection http = apiUrl.openConnection()
        http.setDoOutput(false)
        http.setDoInput(true)
        http.setRequestMethod('PUT')
        http.setRequestProperty("Content-Type", "application/json");
        http.setRequestProperty("Accept", "application/json");
        int rc = http.getResponseCode()
        return rc == 200
    }
}
