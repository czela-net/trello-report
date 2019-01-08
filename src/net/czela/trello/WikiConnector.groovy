package net.czela.trello

import net.sourceforge.jwbf.core.contentRep.Article
import net.sourceforge.jwbf.mediawiki.bots.MediaWikiBot

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
        Article articleSeznam = wikiBot.getArticle("Zápisy_KI");
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
        if (current.hashCode() != reportText.hashCode()) {
            articleReport.setText(reportText)
            articleReport.save();
            println("  Report ulozen")
        } else {
            println("  Report se nezmenil")
        }
    }
}

