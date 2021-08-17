import net.czela.trello.Helper
import net.czela.trello.NetadminConnector
import net.czela.trello.TrelloConnector
import net.czela.trello.TrelloToWiki
import net.czela.trello.model.Card

import groovy.cli.commons.CliBuilder

def cli = new CliBuilder(usage: "${this.class.simpleName}.groovy -h for this help")
// Create the list of options.
cli.with {
    h longOpt: 'help', 'Show help'
    m longOpt: 'move', 'move task to bezi'
    c longOpt: 'config', args: 1, argName: 'config', 'properties suffix'
}

def options = cli.parse(args)

if (options.h) {
    cli.usage()
    System.exit(0)
}

String propertyFile = "netadmin.properties"
if (options.c) {
    propertyFile = "netadmin.properties.${options.c}"
}
Helper.openProps(propertyFile)

def sp = new SyncProjects(Helper.get("trello.api.access.key"), Helper.get("trello.api.access.token"))

if (options.m) {
    sp.moveCards = true
}

sp.process()


/**
 * Synchronizace projektu z trello do netadmina
 *
 */
class SyncProjects {
    boolean dry = false
    boolean moveCards = false
    String myBoardName
    String approvedListName
    String myBoardId
    String activeListName

    Map<String, String> user2idMap = [:]
    Map<String, String> taskType2idMap = [:]
    List<String> preloadCustomFields = []

    TrelloConnector tc
    NetadminConnector nc

    SyncProjects(String key, String token) {
        this.tc = new TrelloConnector(key, token)
        this.nc = new NetadminConnector();
        this.myBoardName = Helper.get('app.netadmin.sync.akce.board.name')
        this.approvedListName = Helper.get('app.netadmin.sync.akce.approvedList.name')
        this.activeListName = Helper.get('app.netadmin.sync.akce.activeList.name')

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

    def label2key(String k) {
        return k.toLowerCase().replaceAll(/[^a-z0-9]+/, '')
    }

    def process() {
        preloadCustomFields.each { id ->
            def cfd = getCustomFieldDefinition(id)
            cfMap.put(id, cfd)
        }

        myBoardId = getBoardByName(myBoardName)
        def activeListId = getListByName(myBoardId, activeListName)

        List<Card> approvedCards = getCardsOnLists(myBoardId, approvedListName)

        for (Card c : approvedCards) {
            if (c.budget > 0 && c.approved) {
                assert c.userId != null, "Spatny sef akce ${c.userName}"
                assert c.typeId != null, "Spatny typ akce ${c.typeName}"

                //println(c)
                /* 2. porovnani hodnot netadmin, trello */
                if (c.akceId != null) {
                    def c2 = convertAkceJson2Card(nc.getAkceById(c.akceId))
                    if (!equalCards(c, c2)) {
                        //println("je potrbeba updatovat: $c2")
                        def msg = showDiffs(c2, c)
                        println(msg)
                        if (!dry) {
                            nc.updateAkce(c)
                            tc.addComment(c.id, msg)
                            if (moveCards)
                                tc.moveCardToList(c.id, activeListId)
                        }
                    } else {
                        println("${c.name} is Ok - move to bezi")
                        if (!dry) {
                            if (moveCards)
                                tc.moveCardToList(c.id, activeListId)
                        }
                    }
                } else {
                    println("Nova akce \"${c.name}\" je potreba ji zapsat!")
                    if (!dry) {
                        Long akceId = nc.createAkce(c)
                        assert akceId != null && akceId > 0L
                        setAkceId(c.id, akceId)
                        tc.addComment(c.id, "Akce je zapsana do [netaminu](https://www.czela.net/netadmin/sef/show_akce.php?id=${akceId})!")
                        if (moveCards)
                            tc.moveCardToList(c.id, activeListId)
                    }
                }
            }
        }
    }

    Card convertJson2Card(def json) {
        def o = new Card()
        o.id = json.id
        o.name = json.name
        o.shortLink = "https://trello.com/c/${json.shortLink}"
        o.desc = json.desc
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

    String showDiffs(Card a, Card b) {
        def arr = []
        if (a.name != b.name) arr.add("akce se přejmenovala na \"${b.name}\"")
        if (a.budget != b.budget) {
            if (a.budget < b.budget) {
                def diff = b.budget - a.budget
                if (a.budget > 0) {
                    def diffpr = (int) (1 + 100 * diff / a.budget)
                    arr.add("akce má navýšený rozpočet z ${a.budget}Kč na ${b.budget}Kč, to jest o ${diff}Kč (${diffpr}%)")
                } else {
                    arr.add("akce má navýšený rozpočet z ${a.budget}Kč na ${b.budget}Kč")
                }
            } else {
                arr.add("akce má snížený rozpočet z ${a.budget}Kč na ${b.budget}Kč")
            }
        }
        if (a.userId != b.userId) arr.add("akci kočíruje nový šef ${b.userName}")
        if (a.typeId != b.typeId) arr.add("oprava činnosti na \"${b.typeName}\"")
        if (arr.size() > 0) {
            return "Změna akce \"[${a.name}](https://www.czela.net/netadmin/sef/show_akce.php?id=${a.akceId})\": " + arr.join(", ")
        } else {
            return "Akce je beze změn"
        }
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
        //println "CF: $cfdId"
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


    class CustoFieldDef {
        String name, type, id
        Map<String, String> options = [:]

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
