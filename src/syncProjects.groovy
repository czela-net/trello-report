import net.czela.trello.Helper
import net.czela.trello.NetadminConnector
import net.czela.trello.TrelloConnector

Helper.openProps()

def sp = new SyncProjects(Helper.get("trello.api.access.key"), Helper.get("trello.api.access.token"))
sp.process()

/**
 * Synchronizace projektu z trello do netadmina
 *
 */
class SyncProjects {
    String myBoardName
    String approvedListName
    String myBoardId

    Map<String,String> user2idMap = [:]
    Map<String,String> taskType2idMap = [:]
    List<String> preloadCustomFields = []

    TrelloConnector tc
    NetadminConnector nc

    SyncProjects(String key, String token) {
        this.tc = new TrelloConnector(key, token)
        this.nc = new NetadminConnector();
        this.myBoardName = Helper.get('app.netadmin.sync.akce.board.name')
        this.approvedListName = Helper.get('app.netadmin.sync.akce.approvedList.name')

        preloadCustomFields = Helper.getList('app.netadmin.list.preload.trello.customField.id')
        user2idMap = Helper.getMap('app.netadmin.map.users.alias')
        taskType2idMap = Helper.getMap('app.netadmin.map.typakce.alias')
    }

    String getBoardByName(String name) {
        def boardsJson = tc.trelloGet("members/me/boards")
        for (board in boardsJson) {
            if (board.name == name) {
                return board.id
            }
        }
        assert false, "Board name: $name does not exists!"
    }

    String getListByName(String boardId, String listName) {
        def listsJson = tc.trelloGet("boards/${boardId}/lists")
        for (list in listsJson) {
            if (list.name == listName) {
                return list.id
            }
        }
        assert false, "List name: $listName does not exists on Board $boardId!"
    }

    List<Card> getCardsOnLists(String boardId, String listName) {
        String listId = getListByName(boardId, listName)
        def result = []
        def cardsJson = tc.trelloGet("boards/${boardId}/cards/open")
        cardsJson.each { card ->
            if (card.idList == listId) {
                def c = convertJson2Card(card)
                result.add(c) // list obsahuje cely objekt Card!!
            }
        }
        return result
    }

    def akceMap = [
            'Prijeti clenu 2020/12'                                       : 2457L,
            'Vánoční dárky 2020'                                          : 2456L,
            'Sanitační zařízení k pípě'                                   : 2455L,
            '10Gbit switche TP-link'                                      : 2454L,
            'Monitoring sítě'                                             : 2453L,
            'Termokamera na akvárko'                                      : 2452L,
            'Nová svářečka'                                               : 2451L,
            'Zabezpečení sítě - audit, školení, dokumentace'              : 2450L,
            'Optika: stadion - zdymadla'                                  : 2449L,
            'nová profi žába'                                             : 2447L,
            'U tosu bytovka'                                              : 2446L,
            'Prijeti clenu 2020/11'                                       : 2445L,
            'Asistentka Q3.2020'                                          : 2444L,
            'PHP Chlumecký'                                               : 2443L,
            'Prijeti clenu 2020/10'                                       : 2442L,
            'Výpomoc v czele'                                             : 2441L,
            'MPO Kabely'                                                  : 2440L,
            'racochejl'                                                   : 2439L,
            'Zaluzi! Kanalizace!'                                         : 2438L,
            'vánoční párty 2020'                                          : 2437L,
            'Sleva z rolí Q4.2020'                                        : 2436L,
            'Školení Pivernetes 2020'                                     : 2435L,
            'Odpadkové koše'                                              : 2434L,
            'Elektronický podpis pro czela.net'                           : 2433L,
            'zápy 2020'                                                   : 2432L,
            'czela.kejta 2020'                                            : 2431L,
            'utp kabely 2020'                                             : 2430L,
            'náklady projekce'                                            : 2429L,
            'oslava roční uzávěrky účto 2019'                             : 2428L,
            'Prijeti clenu 2020/09'                                       : 2427L,
            'Roční odměna podpoře za rok 2019'                            : 2426L,
            'Hlavni BGP a problém ze SFP'                                 : 2425L,
            'Prijeti clenu 2020/08'                                       : 2424L,
            'Předřadné vlákno k OTDR'                                     : 2423L,
            'práce truschán 2020 zápy'                                    : 2422L,
            'Anténa k zaloznimu spoji lentilky -mochov'                   : 2421L,
            'PHP Dominik Q2.2020 asistentka'                              : 2420L,
            'Sleva z rolí Q3.2020'                                        : 2419L,
            'Federer PHP 2019'                                            : 2418L,
            'Opticky kabel na sklad'                                      : 2417L,
            'OLT Mochov'                                                  : 2416L,
            'Mochov U Hřbitova'                                           : 2415L,
            'Nové pojítko do Mochova'                                     : 2414L,
            'Monitorovací čidla do serveroven'                            : 2413L,
            'Prijeti clenu 2020/07'                                       : 2412L,
            'czela.tenis a role správce tenisového kurtu'                 : 2411L,
            'Doplňková kovová skříň přímo do klubovny.'                   : 2410L,
            'PHP Chmela - telefon'                                        : 2409L,
            'Plny moci - projektovani'                                    : 2408L,
            'zápůjčky 2020'                                               : 2407L,
            'czela.gril + NFX | 12.6. + 13.6.2020'                        : 2406L,
            'CZELA.GRIL'                                                  : 2405L,
            'koupení 40KM bidi SFP'                                       : 2404L,
            'Ray3 24Ghz do Mochova'                                       : 2403L,
            'Rotování routerů'                                            : 2402L,
            'Flexa 260mm'                                                 : 2401L,
            'Router na Poštu Nehvizdy'                                    : 2400L,
            'nový kabel z kotelny Na Strani do CMC'                       : 2399L,
            'Rotování pojítek vol.2'                                      : 2398L,
            'Mini akce Testovaci Router TPlink'                           : 2397L,
            'Prijeti clenu 2020/06'                                       : 2396L,
            'Nákup ozonových generátorů do škol'                          : 2395L,
            'WiFiny 2020'                                                 : 2394L,
            'Podpora sítě 2020'                                           : 2393L,
            'Bourací kladivo ke kompresoru'                               : 2392L,
            'Diskuze 80GHz spoje'                                         : 2391L,
            'PHP Dominik Q1.2020 asistentka'                              : 2389L,
            'Sleva z rolí Q2.2020'                                        : 2388L,
            'Prijeti clenu 2020/05'                                       : 2387L,
            'čerpání PHP 2020'                                            : 2386L,
            'Prijeti clenu 2020/04'                                       : 2385L,
            'Kamera do auta'                                              : 2384L,
            'Matej PHP'                                                   : 2383L,
            'Žranice 2020'                                                : 2382L,
            'Nová skříň na šanony'                                        : 2381L,
            'PHP Vašek Telefon'                                           : 2380L,
            'PPC -6 sousedu v rade u Duhace'                              : 2379L,
            'Vyzkum Resetitko'                                            : 2378L,
            'Měniče 2020'                                                 : 2377L,
            'Mochov pokládka k nádraží'                                   : 2376L,
            'prihlasky_odhlasky ARCHIV'                                   : 2375L,
            'Prijeti clenu 2020/03'                                       : 2374L,
            'Prijeti clenu 2020/02'                                       : 2373L,
            'Správce auta 1 čtvrtletí 2020'                               : 2372L,
            'PHP - Kjegel NTB'                                            : 2371L,
            'Záchod CMC'                                                  : 2370L,
            'APi sledovanitv <=>czela'                                    : 2369L,
            'NFX - Plzen 2020'                                            : 2368L,
            'Samson a jeho odpracování dluhu.'                            : 2367L,
            'SFP 2020'                                                    : 2366L,
            'switche 2020'                                                : 2365L,
            'Rychlý prachy podpora 2020'                                  : 2364L,
            'Měniče 2020'                                                 : 2363L,
            'POE pro kameru 1581 nebo resetátor'                          : 2362L,
            'Okruh UPC 2020'                                              : 2361L,
            'Zahraniční konektivita Freetel 2020'                         : 2360L,
            'Zápy - Licence čtú'                                          : 2359L,
            'doklady po druhé import z Flexibee'                          : 2358L,
            'Vánoční párty 2019'                                          : 2357L,
            'Sponzorský dar / reciprocita - BLUESBADGER 2019'             : 2356L,
            'Sleva dan1 a mnemec'                                         : 2355L,
            'PHP matej'                                                   : 2354L,
            'administrativa cyrínová'                                     : 2353L,
            'Nákup krabic do skladu'                                      : 2352L,
            'školení "sledovanitv" | 27.2.2020'                           : 2351L,
            'Nůžkový stan 3x9'                                            : 2350L,
            'Změny členů 1/2020'                                          : 2349L,
            'PHP chmela - Programování netadmin'                          : 2348L,
            'Nájem a energie | 2020'                                      : 2347L,
            'integromat | 2020'                                           : 2346L,
            'Koncesionářské poplatky | 2020'                              : 2345L,
            'Paušály na telefony | 2020'                                  : 2344L,
            'SledovaníTV | 2020'                                          : 2343L,
            'Klubovna provoz(chod) | 2020'                                : 2342L,
            'Auto | 2020'                                                 : 2341L,
            'Roční licence Flexibee | 2020'                               : 2340L,
            'Pojištění spolku | 2020'                                     : 2339L,
            'Účetnictví | 2020'                                           : 2338L,
            'Občerstvení | 2020'                                          : 2337L,
            'Akce "přeplatky členům" | 2020'                              : 2336L,
            'akce a termín VH 2020'                                       : 2335L,
            '10Gbit sitovky do serveru'                                   : 2334L,
            'OTDR - Skrze SFP'                                            : 2333L,
            'Nová jednotka pro GPON'                                      : 2332L,
            'Kolonka'                                                     : 2331L,
            'Switch na páteř'                                             : 2330L,
            'Satah server'                                                : 2329L,
            'Měření sítě pro IPTV'                                        : 2328L,
            'Dominik PHP | supl za asistenktu | 2019'                     : 2327L,
            'PHP effe a matěj | resty účto a evidence 2019'               : 2326L,
            'PHP pro invalidy | 2020'                                     : 2325L,
            'Slevy - role | 1.Q 2020'                                     : 2324L,
            'Slevy prospěšným subjektům | 2020'                           : 2322L,
            'Slevy ze smluv | 2020'                                       : 2321L,
            'Prijeti clenu 2020/01'                                       : 2320L,
            'testovaci'                                                   : 2319L,
            'Skoleni pivernetys'                                          : 2317L,
            'Prijeti clenu 2019/12'                                       : 2316L,
            'ztracené účtenky'                                            : 2315L,
            'nkup modulu do flexibee'                                     : 2314L,
            'switche HP do skladu'                                        : 2313L,
            'Podpora 2019'                                                : 2312L,
            'Propagační material'                                         : 2311L,
            'PHP 2019'                                                    : 2310L,
            'Auto 2019'                                                   : 2309L,
            'Hlasování o akci Okno herna'                                 : 2308L,
            'Zápy 2019'                                                   : 2307L,
            'Vzdělávací kurzy 2019/2020'                                  : 2305L,
            'Klubovna 2019'                                               : 2304L,
            'Zasíťování lokality Zápy - nad hřištěm'                      : 2303L,
            'Nákup RB C1072 a 1036'                                       : 2302L,
            'Maliny v oleji'                                              : 2301L,
            'Sundáni stožáru na 1471 + výkopy Záluží'                     : 2300L,
            'Konference Futuretec'                                        : 2299L,
            'MS Rumunská'                                                 : 2298L,
            'výkaz práce Trusan vodojem'                                  : 2297L,
            'Switche - HP'                                                : 2296L,
            'alternativní router na testy'                                : 2295L,
            'nová amálka'                                                 : 2294L,
            'Workshop WIFI'                                               : 2293L,
            'wifiny 2019'                                                 : 2292L,
            'Zápy - řešení spoje'                                         : 2291L,
            'Rumunská 1446 - 1448'                                        : 2290L,
            'virtuály psanda 2019'                                        : 2289L,
            'Merici/zalozni jednotka Ubnt AirMax AC'                      : 2288L,
            'Roční odměna podpoře 2018'                                   : 2287L,
            'Upgrade 10Gbit Čína'                                         : 2286L,
            'Mochov Další část'                                           : 2285L,
            'Rychlý prachy podpora 2019'                                  : 2284L,
            'Jablonka - Licence RF'                                       : 2283L,
            'Zápy - Licence RF'                                           : 2282L,
            'Duplicita!!! IoT LoRaWAN brány'                              : 2280L,
            'Školení Kubernetes'                                          : 2277L,
            'Prijeti clenu 2019/11'                                       : 2276L,
            'U Kovárny'                                                   : 2273L,
            'mikrotik lora'                                               : 2272L,
            'Výkaz práce - toušen'                                        : 2271L,
            'Školení Kubernetes'                                          : 2270L,
            'Sleva z rolí Q4.2019'                                        : 2269L,
            'neprirazene odhlasky'                                        : 2268L,
            'Prijeti clenu 2019/10'                                       : 2267L,
            'Zasitovani Slunecnich zahrad'                                : 2266L,
            'Nehvizdy'                                                    : 2263L,
            'pozarni zabezpeceni nasich bodu'                             : 2262L,
            'Nehwe, peklo, nehwest'                                       : 2261L,
            'GPon město'                                                  : 2260L,
            'KKTS Plzeň'                                                  : 2259L,
            'NFX Vsetín'                                                  : 2258L,
            'odorik'                                                      : 2257L,
            'Prijeti clenu 2019/09'                                       : 2256L,
            'Daman: Domácí routery na zapůjčení'                          : 2255L,
            'Gymnázium čelákovice'                                        : 2254L,
            'czela kejta 2019'                                            : 2253L,
            'Detektor 33khz a sondy'                                      : 2250L,
            'Menič s UPS'                                                 : 2249L,
            'Effe : LiteBeam, settopbox a set'                            : 2248L,
            'Mikael LAN'                                                  : 2247L,
            'Samson komponenty'                                           : 2246L,
            'Effe : SSD disk, podložka a set'                             : 2245L,
            'Prijeti clenu 2019/08'                                       : 2244L,
            'Zapůjčení Makita příslušenství'                              : 2243L,
            'Slevy z rolí Q3.2019'                                        : 2242L,
            'Sleva Lukáš Brož : červen 2019'                              : 2241L,
            'Kužel : SSD disky na testování'                              : 2240L,
            'Prijeti clenu 2019/07'                                       : 2239L,
            'Monitor Samson'                                              : 2238L,
            'Prijeti clenu 2019/06'                                       : 2237L,
            'Vratné kelímky na akce'                                      : 2236L,
            'Sluchátka na testování'                                      : 2235L,
            'czela.hokej 2019'                                            : 2234L,
            'Bazén 2019/2020'                                             : 2233L,
            'Čarodejnice 2019'                                            : 2232L,
            'Dovybavení kuchyňky kuchyňskou linkou a digestoří'           : 2231L,
            'Přeložka chrániček v lokalitě Na Nábřeží'                    : 2230L,
            'Prijeti clenu 2019/05'                                       : 2229L,
            'Ostrá prosba radě'                                           : 2228L,
            'Čarodějnice Šanik'                                           : 2227L,
            'Nabídka záložního okruhu O2'                                 : 2226L,
            'czela.plavání 2018/2019 : pronájem bazénu'                   : 2225L,
            'Mochov pokladka - Dr. Nejedleho, Tylova, Na Zatisi'          : 2224L,
            'Kabel pro zafukování k členům'                               : 2223L,
            'Materiál do skladu (původně Mochov Další část)'              : 2222L,
            'Duplicita!!! IoT LoRaWAN brány'                              : 2221L,
            'Optika do Portu'                                             : 2220L,
            'Slevy z rolí 2. čtvrtletí 2019'                              : 2219L,
            'Nákup úhlové brusky a zapůjčení na testování : Petr Truschán': 2218L,
            'Sondy do sítě'                                               : 2217L,
            'Fyzické servery v CMC - revize a upgrade'                    : 2216L,
            'Upgrade 10Gbit Stankovského'                                 : 2215L,
            'Elektrorevize QByt'                                          : 2214L,
            'Jiřina obvolávání'                                           : 2213L,
            'Sud pro Matěje'                                              : 2212L,
            'AP Záluží bytovka'                                           : 2211L,
            'Zápůjčka Samsung HW-N450/EN na testování'                    : 2210L,
            'Dveře herna'                                                 : 2209L,
            'Příslušenství pípa'                                          : 2208L,
            'Prijeti clenu 2019/04'                                       : 2207L,
            'Občerstvení na VH 3/2019'                                    : 2206L,
            'router na testování a držák na komín'                        : 2205L,
            'Vyzkoušení nového routeru'                                   : 2204L,
            'Sleva Koděra 2019'                                           : 2203L,
            'Oprava slev KK Q4.2018'                                      : 2202L,
            'czela.fest 2019'                                             : 2201L,
            'Slevy zapomenutých členů'                                    : 2200L,
            'AP Hook'                                                     : 2199L,
            'Žranice 2019'                                                : 2198L,
            'Telefon na testování : vaclavd'                              : 2197L,
            'Prijeti clenu 2019/03'                                       : 2196L,
            'Konference Valeč 2019'                                       : 2195L,
            'Prijeti clenu 2019/02'                                       : 2194L,
            'kompresor - servis'                                          : 2193L,
            'Konektivita Freetel 2019'                                    : 2192L,
            'Okruh UPC 2019'                                              : 2191L,
            'Slevy z rolí 1. čtvrtletí 2019'                              : 2190L,
            'Slevy prospěšným subjektům 2019'                             : 2189L,
            'Slevy ze smluv 2019'                                         : 2188L,
            'Občerstvení 2019'                                            : 2187L,
            'Administrativní práce 2019'                                  : 2186L,
            'Oběh a evidence dokumentů, faktur, účtenek, smluv'           : 2185L,
            'Účetnictví 2018'                                             : 2184L,
            'Účetnictví 2019'                                             : 2183L,
            'Roční licence Flexibee 2019'                                 : 2182L,
            'Pojištění spolku 2019'                                       : 2181L,
            'Sledování.TV 2019'                                           : 2180L,
            'Paušály na telefony 2019'                                    : 2179L,
            'Koncesionářské poplatky 2019'                                : 2178L,
            'Nájem a energie 2019'                                        : 2177L,
            'zafukování na švihově'                                       : 2176L,
            'Klešťový ampérmetr'                                          : 2175L,
            'IoT LoRaWAN brány'                                           : 2135L,
    ]

    def label2key(String k) {
        return k.toLowerCase().replaceAll(/[^a-z0-9]+/, '')
    }

    def process() {

       // transfromace klicu aby se to lepe hledalo
       def newMap = [:]
       akceMap.each { k,v ->
           def kk = label2key(k)
           newMap.put(kk,v)
       }
       akceMap = newMap

        preloadCustomFields.each { id ->
            def cfd = getCustomFieldDefinition(id)
            cfMap.put(id, cfd)
        }

        /*
        // transfromace klicu aby se to lepe hledalo
        def newMap = [:]
        akceMap.each { k,v ->
            def kk = label2key(k)
            newMap.put(kk,v)
        }
        akceMap = newMap
         */

        myBoardId = getBoardByName(myBoardName)

        List<Card> approvedCards = getCardsOnLists(myBoardId, approvedListName)

        for (Card c : approvedCards) {
            if (c.budget > 0 && c.approved) {
                println(c)
                /* 2. porovnani hodnot netadmin, trello
                if (c.akceId != null) {
                    def c2 = convertAkceJson2Card(nc.getAkceById(c.akceId))
                    if (! equalCards(c, c2)) {
                        println("je potrbeba updatovat: $c2")
                    }
                }*/
                /* 1. synchronizace dat (jeste jsem tu mel mapu akci) */
                if (c.akceId == null) {
                    def akceId = akceMap.get(label2key(c.name))
                if (akceId != null) {
                    println("Save akceId Name:${c.name}, id=${akceId}")
                    setAkceId(c.id, akceId)
                } else {
                    println("Unknown akceId Name:${c.name}")
                }
                }
                /* */
                /*
                if (c.akceId == null) {
                    c.akceId = nc.createAkce(c)
                    tc.saveAkceId(c)
                    tc.setComment(c, "Akce zapsana")
                } else {
                    Card c2 = convertAkceJson2Card(nc.getAkceById(c.akceId))
                    if (!equalCards(c2, c)) {
                        nc.saveAkce(c)
                        tc.setComment(c, "Zmena akce zapsana")
                    }
                }
                 */
            }
        }
    }

    Card convertJson2Card(def json) {
        def o = new Card()
        o.id = json.id
        o.name = json.name
        getCustomFieldValues(o)
        if (o.userName != null && o.userId == null) {
            o.userId = user2idMap.get(o.userName) as Long
        }
        if (o.typeName != null && o.typeId == null) {
            o.typeId = taskType2idMap.get(o.typeName) as Long
        }

        return o
    }

    Card convertAkceJson2Card(def json) {
        def o = new Card()
        o.akceId = json.id as Long
        o.name = json.nazev
        o.userId = json.userid
        o.budget = json.cena as Long
        o.typeId = json.sekceid as Long
        o.statusId = json.stav as Long
        return o
    }

    boolean equalCards(Card a, Card b) {
        return a.name == b.name && a.budget == b.budget && a.userId == b.userId && a.akceId == b.akceId && a.typeId == b.typeId
    }

    def setAkceId(def cardId, long akceId) {
        String cfLabel = label2key('id akce')
        for (CustoFieldDef cfd in cfMap.values()) {
            if (label2key(cfd.name) == cfLabel) {
                def json = """{"value":{"${cfd.type}":"$akceId"}}"""
                def link = "cards/${cardId}/customField/${cfd.id}/item"
                tc.trelloPut(link, json)
                return true
            }
        }
        return false
    }

    Map<String, CustoFieldDef> cfMap = [:]

    Card getCustomFieldValues(Card card) {
        def cfJson = tc.trelloGet("cards/${card.id}/customFieldItems")
        cfJson.each { cf ->
            String cfLabelId = cf.idCustomField
            CustoFieldDef cfDef = cfMap.get(cfLabelId)
            if (cfDef == null) {
                cfDef = getCustomFieldDefinition(cfLabelId)
                cfMap.put(cfLabelId, cfDef)
            }

            switch (cfDef.name) {
                case 'hlasovani':
                case 'Hlasování':
                    card.approved = parsePoll(cf.value.text)
                    break
                case 'id akce':
                    card.akceId = cfDef.getLongValue(cf)
                    break
                case 'rozpočet':
                case 'Rozpočet':
                    card.budget = cfDef.getLongValue(cf)
                    break
                case 'typ akce':
                case 'Typ akce':
                    card.typeName = cfDef.getValue(cf)
                    break
                case 'šef akce':
                case 'Šéf Akce':
                    card.userName = cfDef.getValue(cf)
                    break
                default: warn("Uknown customField with name: $cfDef.name")
            }
        }
        return card
    }

    private CustoFieldDef getCustomFieldDefinition(String cfdId) {
        println "CF: $cfdId"
        def cfDefJson = tc.trelloGet("customFields/${cfdId}")
        CustoFieldDef cfDef = new CustoFieldDef()
        cfDef.name = cfDefJson.name
        cfDef.type = cfDefJson.type
        cfDef.id = cfdId
        if (cfDef.type == 'list') {
            cfDefJson.options.each { op ->
                cfDef.options.put(op.id, op.value.text)
            }
        }
        return cfDef
    }

    static boolean parsePoll(String poll) {
        if (poll != null) {
            def m = poll =~ /(\d+):(\d+):(\d+)/
            if (m.find()) {
                int pro = m[0][1] as int
                int proti = m[0][2] as int
                int zdrzel = m[0][3] as int
                return pro * 2 > pro + proti + zdrzel
            }
        }
        warn("Can not parse poll: $poll")
        return false
    }

    static void warn(def msg) {
        System.err.println("WARN: $msg")
    }

    class Card {
        String id, name, userName, typeName
        Long budget = 0L
        boolean approved = false
        Long userId, akceId, typeId, statusId

        @Override
        public String toString() {
            return "Card{" +
                    "id='" + id + '\'' +
                    ", akceId=" + akceId +
                    ", name='" + name + '\'' +
                    ", budget=" + budget +
                    ", approved=" + approved +
                    ", statusId=" + statusId +
                    ", userName='" + userName + '\'' +
                    ", userId=" + userId +
                    ", typeName='" + typeName + '\'' +
                    ", typeId=" + typeId +
                    '}';
        }
    }

    class CustoFieldDef {
        String name, type, id
        Map<String,String> options = [:]

        private String listValue(def cf) {
            def v = options.get(cf.idValue)
            assert v != null
            return v
        }

        String getValue(def cf) {
            String r = null
            switch (type) {
                case 'number':
                    r = cf.value.number
                    break
                case 'text':
                    r = cf.value.text
                    break
                case 'list':
                    r = listValue(cf)
                    break
                    break
                    warn("Can not parse CustomeField type $type")
            }
            return r
        }
        Long getLongValue(def cf) {
            getValue(cf) as Long
        }
    }
}