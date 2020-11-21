package net.czela.trello

class NetadminGQLConnector {
    String url = "http://docker.czela.net:9090/netadmin-api/graphql"
    String username = "api"
    String password = "qfDMb1toV9aNlxljvy2qekV0ylU5cO"

    def gqlPost() {
        URL apiUrl = new URL(url)
        HttpURLConnection http = apiUrl.openConnection()
        http.setDoOutput(true)
        http.setDoInput(true)
        http.setRequestMethod('POST')
        http.setRequestProperty("Content-Type", "application/json");
        http.setRequestProperty("Accept", "application/json");
        String secret = username + ":" + password
        http.setRequestProperty("Authorization", "Basic " + new String(Base64.getEncoder().encode(secret.getBytes("UTF-8"))));
        http.getOutputStream().write("{ \"query\" : \"query { actions(where:{id_gt : 2171}) {id, sekceid, nazev, stav, datum_schvaleni, datum_ukonceni, userid, cena, schvaleno}}\" }".toString().bytes)
        int rc = http.getResponseCode()

        http.getInputStream().readLines().each { println (it) }

        return rc == 200
    }

    public static void main(String[] args) {
        def c = new NetadminGQLConnector();
        c.gqlPost()

    }
    /*
    ### pridani
    POST http://{{host}}/netadmin-api/graphql
    Authorization: Basic {{username}} {{password}}
    Content-Type: application/json

    { "query" : "mutation { addAction(sekceid: 2, nazev: \"nazev 3\", stav: 5, obsah: \"obsah 1\", userid: 1138, cena: \"10\", schvaleno: \"schvaleno 1\", ukonceno: \"ukonceno 1\", smlouvanutna: 0) { id, sekceid, nazev, stav, userid }}" }


    ### zmena
    POST http://{{host}}/netadmin-api/graphql
    Authorization: Basic {{username}} {{password}}
    Content-Type: application/json

    { "query" : "mutation { updateAction(id: 2171, sekceid: 2, nazev: \"nazev 4\", stav: 5, obsah: \"obsah 1\", userid: 1138, cena: \"10\", schvaleno: \"schvaleno 1\", ukonceno: \"ukonceno 1\", smlouvanutna: 0) { id, sekceid, nazev, stav, userid }}" }


    ### dotazani na 1 konkretni akci podle ID
    POST http://{{host}}/netadmin-api/graphql
    Authorization: Basic {{username}} {{password}}
    Content-Type: application/json

    { "query" : "query { actions(id: 2171) {id, sekceid, nazev, stav, obsah, datum_schvaleni, datum_ukonceni, userid, cena, schvaleno, ukonceno, userid2, smlouvanutna }}" }


     */

}
