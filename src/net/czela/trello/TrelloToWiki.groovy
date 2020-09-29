package net.czela.trello

import java.text.SimpleDateFormat

/*
 * TODO
 *      - exporter si nepamatuje ze už něco zaarchivoval, pokud tedy spuutime generator podruhé (nez navýšení čísla zápisu) část ukolu se ztratí (hotové ukoly)
 *      - u exporterů není vyřešeno automatické číslováni verzi zápisu
 *      - není dodělaná integrace s novou evidencí
 */

class TrelloToWiki {

    private TrelloConnector tc
    private MediaWikiFormater wiki
    private File file
    private boolean archivingEnabled = false
    private HashSet<String> archivedIds = new HashSet<>()
    String listIdApproved = null
    String listIdInProgress = null
    private WikiConnector wikic

    private String boardName
    private String wikiGroup
    private String year
    private String date
    private String reportNo


    TrelloToWiki(def options) {
        this.tc = new TrelloConnector(options.k, options.t)
        this.wiki = new MediaWikiFormater()
        this.file = new File((options.o) ? options.o : "report.txt")
        this.archivingEnabled = options.a
        this.wikiGroup = options.g?options.g:'KI'
        this.boardName = options.b?options.b:'Projekty KI'
        this.year = options.y?options.y:'2099'
        this.date = options.d?options.d:'1.1.2099'
        this.reportNo = options.n?options.n:'99'

        if (options.u) {
            this.wikic = new WikiConnector("https://www.czela.net/wiki/", options.u, options.p)
        }

    }

    private static List<String> ignoredActions = ['updateCard']

    private static SimpleDateFormat atomFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
    private static Date twoMonthAgo = new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 60)

    private static Map<String, String> customfields = [:] // mapa kde si postupne stahuju nazvy custom fieldu
    private static Map<String, String> customfieldOptions = [:] // mapa kde si postupne stahuju nazvy custom fieldu

    private static Map<String, Long> sefove = [
            'Chmelej'   : 1224,
            'Daman'     : 10695,
            'Naj.Satah' : 1138,
            'Renda'     : 10140,
            'Matěj'     : 10791,
            'Majkl'     : 1111,
            'Asistenka' : 12918,
            'Vašek'     : 10088,
            'Efffe'     : 1023,
            'Asistentka': 13838,
            'Mikael'    : 1070,
            'Forest'    : 12394,
    ]

    private static Map<String, Long> activityTypeMap = [
            "KI - projekty": 5L,
            "KI - optika a LAN": 6L,
            "KI - bezdrátové body": 7L,
            "KI - upgrade sítě": 8L,
            "KI - výzkum": 9L,
            "fix - podpora": 15L,
            "fix - nájem a energie": 16L,
            "fix - licence ČTU": 18L,
            "fix - konektivita": 19L,
            "fix - asistentka": 20L,
            "fix - slevy na PHP": 17L,
            "PHP - čerpání": 4L,
            "PHP- čerpání": 4L,
            "akce a párty": 10L,
            "slevy": 11L,
            "chod sdružení": 12L,
            "účetnictví, pojištění": 13L,
            "externí služby": 14L,
            "podpora": 15L,
            "nájem a energie": 16L,
            "slevy na PHP": 17L,
            "licence ČTÚ": 18L,
            "konektivita": 19L,
            "asistentka": 20L,
    ]



    def process() {
        println(" processing ...")
        def boardsJson = tc.trelloGet("members/me/boards")
        boardsJson.each { board ->
            if (board.name == boardName) {
                processLists(board)
                processCards(board)
            } else {
                println(" board ${board.name} is skipped.")
            }
        }

        String reportText = wiki.printReport(this.archivingEnabled)
        if (file.exists()) file.delete()
        file << reportText

        if (wikic) {
            //wikic.storeReport("KI", "2019", "12", "8.9.2019", reportText);
            wikic.storeReport(this.wikiGroup,  this.year, this.reportNo, this.date, reportText)
        }
    }

    private void processCards(board) {
        def cardsJson = tc.trelloGet("boards/${board.id}/cards")
        cardsJson.each { card ->
            String comments = processComments(card)
            NetadminAkce akce =  processCustomFields(card)
            String customs = akce.wikiText
            String attachments = processAttachments(card)
            if (card.name.startsWith("Zápis jednání")) {
                String checkLists = processCheckLists(card, true)
                customs = customs.replaceFirst("Šéf Akce","Vede")
                wiki.addHead(card.desc+"\n"+customs+checkLists+"\n\n")
            } else {
                String checkLists = processCheckLists(card, false)
                wiki.addSubsection(card.idList, card.name, card.desc +"\n" + customs +"\n"+ checkLists+"\n"+attachments+"\n"+comments)
            }

            if (archivedIds.contains(card.idList)) {
                if (archivingEnabled) {
                    println("Archivuji ${card.name}")
                    tc.trelloPut("cards/${card.id}?closed=true")
                }
            }

            if (listIdApproved == card.idList) {
                if (akce.isValid()) {
                    if (archivingEnabled) {
                    // zapis do netadmina
                    // uloz akce id
                    // pridej komentar zapsano
                    // presun kartu do bezi
                    }
                } else {
                    warn(akce.getValidationStatus())
                }
            }
        }
    }

    String processCheckLists(def card, boolean isHead) {
        def checkLists = tc.trelloGet("cards/${card.id}/checklists")
        StringBuilder sb = new StringBuilder()
        checkLists.each { checkList ->
            String listName = checkList.name
            def list = []
            checkList.checkItems.each { checkItem ->
                boolean checked = (checkItem.state == 'complete')
                if (!checked) {
                    String itemLabel = checkItem.name
                    list.add(itemLabel)
                }
            }
            if (list.size() > 0) {
                if (isHead) {
                    String listItems = list.sort().join(", ")
                    sb.append("\n* $listName: $listItems\n")
                } else {
                    String listItems = list.sort().collect({ "** $it" }).join("\n")
                    sb.append("\n* $listName:\n$listItems")
                }
            }
        }
        return sb.toString()
    }

    private void processLists(def board) {
        def listsJson = tc.trelloGet("boards/${board.id}/lists")
        listsJson.each { list ->
            wiki.addSection(list.id, list.name)

            if (list.name == "Hotovo" || list.name == "Zamítnuto") {
                archivedIds.add(list.id)
            }

            if (list.name == "Schváleno") {
                listIdApproved = list.id as String
            }

            if (list.name == "Běží") {
                listIdInProgress = list.id as String
            }
        }
    }

    /**
     * Trello řadí poznamky tak, že poslední vložená je na první místě, ale v zápisu chceme opačné pořadí. Navíc nás zajímají jen poznámky staré max 8 týdnů
     *
     * @param card
     * @return
     */
    private String processComments(def card) {
        StringBuilder reverseComments = new StringBuilder()
        def actionsJson = tc.trelloGet("cards/${card.id}/actions")
        actionsJson.each { action ->
            def atype = action.type
            if (atype == 'commentCard') {
                Date d = atomFmt.parse(action.date)
                if (twoMonthAgo.before(d)) {
                    reverseComments.append(wiki.fmtNote(d, action.data.text).reverse())
                }
            } else if (!ignoredActions.contains(atype)) {
                warn(" - ${action.id} [uknown action type]: ${atype}")
            }
        }
        if (reverseComments.length() > 1) {
            reverseComments.append("* '''Poznámky''':\n".reverse())
        }
        reverseComments.toString().reverse()
    }

    private NetadminAkce processCustomFields(def card) {
        def akce = new NetadminAkce()
        akce.name = card.name
        def customs = "\n"
        def cfs = tc.trelloGet("cards/${card.id}/customFieldItems")
        cfs.each { cf ->
            String cfId = cf.idCustomField
            String cfLabel = customfields.get(cfId)
            String cfValue = cf.value?.text
            if (cfLabel == null) {
                def cfDef = tc.trelloGet("customFields/${cfId}")
                customfields.put(cfId, cfDef.name)
                cfLabel = customfields.get(cfId)
            }

            if (!cfValue) {
                String optionId = cf.idValue
                if (optionId) {
                    def optionValue = customfieldOptions.get(optionId)
                    if (!optionValue) {
                        def option = tc.trelloGet("customField/${cfId}/options/${optionId}")
                        optionValue = option.value.text
                        customfieldOptions.put(optionId, optionValue)
                    }
                    cfValue = optionValue
                }
            }

            if (cfValue) {
                if (cfLabel == 'Šéf Akce') {
                    akce.assignedTo = sefove.get(cfValue)
                    if (akce.assignedTo == null) {
                        warn("${card.name} - neznamy sef akce! ${cfValue}")
                        sefove.put(cfValue, -1)
                    } else {
                        customs += wiki.fmtCustomKV(cfLabel, cfValue)
                    }
                } else if (cfLabel == 'Rozpočet') {
                    if (cfValue ==~ /^[0-9]+$/) {
                        akce.budget = new BigDecimal(cfValue)
                        customs += wiki.fmtCustomKV(cfLabel, "$cfValue Kč")
                    } else {
                        warn("${card.name} - pole Rozpočet obsahuje nesmysl: '$cfValue'")
                    }
                } else if (cfLabel == 'Hlasování') {
                    def m = cfValue =~ /^([0-9]+):([0-9]+):([0-9]+)$/
                    if (m.matches()) {
                        double pro = Double.parseDouble(m[0][1])
                        double proti = Double.parseDouble(m[0][2])
                        double zdrzel = Double.parseDouble(m[0][3])
                        akce.aproved = (pro / (pro + proti + zdrzel) >= 0.5)
                        def vysledek = akce.aproved ? "SCHVÁLENO" : "ZAMÍTNUTO"
                        customs += wiki.fmtCustomKV(cfLabel, "$cfValue \n** $vysledek")
                    } else {
                        warn("${card.name} - pole Hlasování obsahuje nesmysl")
                    }
                } else if (cfLabel == 'Typ akce') {
                    customs += wiki.fmtCustomKV(cfLabel, cfValue)
                    akce.activityType = activityTypeMap.get(cfValue)
                } else if (cfLabel == 'id akce') {
                    akce.id = Long.parseLong(cfValue)
                } else {
                    warn("${card.name} - unknown custom field $cfLabel")
                    customs += wiki.fmtCustomKV(cfLabel, cfValue)
                }
            }
        }
        akce.wikiText = customs
        return akce
    }

    private String processAttachments(def card) {
        def attachments = "\n\n* '''Přílohy:'''\n"
        def cfs = tc.trelloGet("cards/${card.id}/attachments")
        boolean showAttachments = false
        def maxSize = 1.5 * 1024 * 1024 // s obrazkem 1.8MB mi wiki vracela chybu :-(
        cfs.each { cf ->
            int size = cf.bytes
            if (cf.isUpload) {
                String id = cf.id
                String name = cf.name
                String url = cf.url

                if (name.endsWith("jpg")) {
                    if (size > maxSize) {
                        String bestUrl = null
                        int bestSize = 0
                        cf.previews.each { preview ->
                            int psize = preview.bytes
                            if (psize > 0 && psize < maxSize && psize > bestSize) {
                                bestSize = psize
                                bestUrl = preview.url
                            }
                        }
                        if (bestSize > 0) {
                            size = bestSize
                            url = bestUrl // nahraju zmenseninu
                        }
                    }
                }

                String mimeType = cf.mimeType
                File mediaFile = new File(new File('cache'), id+'-'+name.replaceAll(/[^A-Za-z0-9._-]/,'_'))
                tc.getStream(url, mediaFile)

                if (size <= maxSize && (name.endsWith('png') || name.endsWith('gif') || name.endsWith('jpg') || name.endsWith('jpeg')
                        || name.endsWith('pdf') || name.endsWith('ods'))) {
                    wikic.copyImage(mediaFile, name)

                    attachments += "** [[File:$name]]\n"
                    showAttachments = true
                } else {
                    warn("prilohu ${name} nelze nahrat do wiki. wiki podporuje jen obrazky a pdf do 1.5 MB ")
                }
            }
        }
        return showAttachments?attachments:""
    }

    def warn(def s) {
        println("WARN: $s")
    }
}
