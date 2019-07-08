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
    private HashSet<String> archivedIds = new HashSet<>();
    private WikiConnector wikic

    TrelloToWiki(def options) {
        this.tc = new TrelloConnector(options.k, options.t);
        this.wiki = new MediaWikiFormater()
        this.file = new File((options.o) ? options.o : "report.txt")
        this.archivingEnabled = options.a

        if (options.u) {
            this.wikic = new WikiConnector("https://www.czela.net/wiki/", options.u, options.p)
        }

    }

    private static List<String> ignoredActions = ['updateCard']

    private static SimpleDateFormat atomFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private static Date twoMonthAgo = new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 60)

    private static Map<String, String> customfields = [:] // mapa kde si postupne stahuju nazvy custom fieldu
    private static Map<String, String> customfieldOptions = [:] // mapa kde si postupne stahuju nazvy custom fieldu

    private static Map<String, Long> sefove = [
            'Chmelej'  : 1224,
            'Daman'    : 10695,
            'Naj.Satah': 1138,
            'Renda'    : 10140,
            'Matěj'    : 10791,
            'Majkl'    : 1111,
            'Asistenka': 12918,
            'Vašek'    : 10088,
    ]

    def process() {
        println(" processing ...")
        def boardsJson = tc.trelloGet("members/me/boards");
        boardsJson.each { board ->
            if (board.name == 'Projekty KI') {
                processLists(board)
                processCards(board)
            }
        }

        String reportText = wiki.printReport();
        if (file.exists()) file.delete()
        file << reportText

        if (wikic)
            wikic.storeReport("KI", "2019", "9", "7.7.2019", reportText);
    }

    private void processCards(board) {
        def cardsJson = tc.trelloGet("boards/${board.id}/cards");
        cardsJson.each { card ->
            String comments = processComments(card)
            String customs = processCustomFields(card)
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
                    sb.append("\n* $listName: $listItems\n");
                } else {
                    String listItems = list.sort().collect({ "** $it" }).join("\n")
                    sb.append("\n* $listName:\n$listItems")
                }
            }
        }
        return sb.toString();
    }

    private void processLists(def board) {
        def listsJson = tc.trelloGet("boards/${board.id}/lists");
        listsJson.each { list ->
            wiki.addSection(list.id, list.name)

            if (list.name == "Hotovo" || list.name == "Zamítnuto") {
                archivedIds.add(list.id);
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

    private String processCustomFields(def card) {
        def customs = "\n";
        def cfs = tc.trelloGet("cards/${card.id}/customFieldItems");
        cfs.each { cf ->
            String idcf = cf.idCustomField
            String cfLabel = customfields.get(idcf)
            String cfValue = cf.value?.text
            if (cfLabel == null) {
                def cfDef = tc.trelloGet("customFields/${idcf}");
                customfields.put(idcf, cfDef.name)
                cfLabel = customfields.get(idcf)
            }

            if (!cfValue) {
                String optionId = cf.idValue
                if (optionId) {
                    def optionValue = customfieldOptions.get(optionId);
                    if (!optionValue) {
                        def option = tc.trelloGet("customField/${idcf}/options/${optionId}");
                        optionValue = option.value.text
                        customfieldOptions.put(optionId, optionValue)
                    }
                    cfValue = optionValue;
                }
            }

            if (cfValue) {
                if (cfLabel == 'Šéf Akce') {
                    Long sefId = sefove.get(cfValue)
                    if (sefId == null) {
                        warn("${card.name} - neznamy sef akce! ${cfValue}")
                        sefove.put(cfValue, -1);
                    } else {
                        customs += wiki.fmtCustomKV(cfLabel, cfValue)
                    }
                } else if (cfLabel == 'Rozpočet') {
                    if (cfValue ==~ /^[0-9]+$/) {
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
                        def vysledek = (pro / (pro + proti + zdrzel) >= 0.5) ? "SCHVÁLENO" : "ZAMÍTNUTO"
                        customs += wiki.fmtCustomKV(cfLabel, "$cfValue \n** $vysledek")
                    } else {
                        warn("${card.name} - pole Hlasování obsahuje nesmysl")
                    }
                } else if (cfLabel == 'id akce') {
                    warn("${card.name} - unknown custom field $cfLabel")
                    customs += wiki.fmtCustomKV(cfLabel, cfValue)
                }
            }
        }
        customs
    }

    private String processAttachments(def card) {
        def attachments = "\n\n* '''Přílohy:'''\n";
        def cfs = tc.trelloGet("cards/${card.id}/attachments");
        boolean showAttachments = false;
        cfs.each { cf ->
            String id = cf.id
            int size = cf.bytes
            String name = cf.name
            String url = cf.url

            if (name.endsWith("jpg")) {
                def maxSize = 1.5 * 1024 * 1024 // s obrazkem 1.8MB mi wiki vracela chybu :-(
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
                        url = bestUrl; // nahraju zmenseninu
                    }
                }
            }

            String mimeType = cf.mimeType
            println "Attachment: $id , $name , $url, $mimeType"
            File mediaFile = new File(id+'-'+name.replaceAll(/[^A-Za-z0-9._-]/,'_'));
            tc.getStream(url, mediaFile);
            println "  upload to wiki"
            wikic.copyImage(mediaFile, name);

            attachments += "** [[File:$name]]\n"
            showAttachments = true;

        }
        return showAttachments?attachments:""
    }

    def warn(def s) {
        println("WARN: $s")
    }
}
