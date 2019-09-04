package net.czela.trello

import net.sourceforge.jwbf.core.actions.util.ProcessException
import net.sourceforge.jwbf.core.contentRep.Article
import net.sourceforge.jwbf.mediawiki.actions.editing.FileUpload
import net.sourceforge.jwbf.mediawiki.actions.queries.ImageInfo
import net.sourceforge.jwbf.mediawiki.bots.MediaWikiBot
import net.sourceforge.jwbf.mediawiki.contentRep.SimpleFile

@Grab (group='net.sourceforge', module= 'jwbf', version='3.1.1')
@Grab (group='org.slf4j', module='slf4j-log4j12', version='1.7.2')
@Grab (group='log4j', module='log4j', version='1.2.17')

class WikiConnector {
    private MediaWikiBot wikiBot

    WikiConnector(def host, def user, def password) {
        wikiBot = new MediaWikiBot(host);
        wikiBot.login(user as String, password as String);
    }

    def storeReport(String group, String year, String recno, String date, String reportText) {
        assert group != null
        assert year != null
        assert recno != null
        assert date != null
        assert reportText != null
        String listName = (group == 'Rada')?'Rady':group
        Article articleSeznam = wikiBot.getArticle("Zápisy_${listName}");
        String textSeznam = articleSeznam.getText();

        String path = "${group}_${recno}/${year}"
        println "  Writing $path to wiki"
        if (textSeznam.contains(path)) {
            println("  Report uz je v seznamu")
        } else {
            String textSeznam2 = textSeznam.replaceFirst("=Zápisy ${year}=".toString(), "=Zápisy ${year}=\n* [[${path}|${group} ${recno}/${year} - $date]]")
            if (textSeznam.hashCode() != textSeznam2.hashCode()) {
                articleSeznam.setText(textSeznam2);
                articleSeznam.save();
                println("  Seznam ulozen")
            } else {
                println("  Seznam se nezmenil")
            }
        }

        Article articleReport = wikiBot.getArticle(path);
        String current = articleReport.getText()
        if (current == null || current.length() == 0 || current.startsWith("Draft:")) {
            if (current.hashCode() != reportText.hashCode()) {
                articleReport.setText(reportText)
                articleReport.save();
                println("  Report ulozen")
            } else {
                println("  Report se nezmenil")
            }
        } else {
            println("  Nelze prepsat existujici report (pouze Draft lze zmenit!)")
        }
    }

    def copyImage(File file, String label) {
        ImageInfo ii = new ImageInfo(wikiBot, label)
        try {
            if (ii?.getUrlAsString()?.length() > 0) {
                println "  image $label exists. skip."
                return
            }
        } catch (ProcessException e) {
            // ignore
        }
        println "  upload image $label to wiki"
        def sf = new SimpleFile(label, file)
        def fu = new FileUpload(sf, wikiBot)
        wikiBot.getPerformedAction(fu);
    }
}

