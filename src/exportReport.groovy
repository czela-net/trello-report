import net.czela.trello.TrelloToWiki
import groovy.cli.commons.CliBuilder

def cli = new CliBuilder(usage: "${this.class.simpleName}.groovy -h for this help")
// Create the list of options.
cli.with {
    a longOpt: 'archive', 'Archivace hotovych a neschvalenych ukolu'
    b longOpt: 'board-name', args: 1, argName: 'boardName', 'jmeno nastenky v Trello'
    g longOpt: 'group-name', args: 1, argName: 'reportGroup', 'typ reportu ve wiki, KI je default'
    h longOpt: 'help', 'Show help'
    k longOpt: 'trello-key', args: 1, argName: 'key', 'Trello API key'
    o longOpt: 'output', args: 1, argName: 'outpuFile', 'Soubor s vystupnim reportem ve formatu mediawiki'
    p longOpt: 'wiki-password', args: 1, argName: 'wikiPassword', 'Wiki Password'
    t longOpt: 'trello-token', args: 1, argName: 'token', 'Trello API token'
    u longOpt: 'wiki-user', args: 1, argName: 'wikiUser', 'Wiki User'
    y longOpt: 'year', args: 1, argName: 'year', 'Rok se pouzije pro cestu v URL'
    d longOpt: 'date', args: 1, argName: 'date', 'Datum reportu proste tam je'
    n longOpt: 'report-number', args: 1, argName: 'report-number', 'Poradove cislo reportu se pouzije v URL'
}

def options = cli.parse(args)

if ((!options.k && !options.u) || options.h) {
    cli.usage()
}

def p = new TrelloToWiki(options)
p.process()

