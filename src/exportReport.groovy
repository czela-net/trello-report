import net.czela.trello.TrelloToWiki

def cli = new CliBuilder(usage: "${this.class.simpleName}.groovy -h for this help")
// Create the list of options.
cli.with {
    h longOpt: 'help', 'Show help'
    a longOpt: 'archive', 'Archivace hotovych a neschvalenych ukolu'
    k longOpt: 'trello-key', args: 1, argName: 'key', 'Trello API key'
    t longOpt: 'trello-token', args: 1, argName: 'token', 'Trello API token'
    o longOpt: 'output', args: 1, argName: 'outpuFile', 'Soubor s vystupnim reportem ve formatu mediawiki'
    u longOpt: 'wiki-user', args: 1, argName: 'wikiUser', 'Wiki User'
    p longOpt: 'wiki-password', args: 1, argName: 'wikiPassword', 'Wiki Password'
}

def options = cli.parse(args)

if ((!options.k && !options.u) || options.h) {
    cli.usage()
}

def p = new TrelloToWiki(options)
p.process()

/*
def tc = new TrelloConnector(options.k, options.t);
tc.trelloPut("cards/5bf294848bd9010fe533bc01?closed=true")
*/