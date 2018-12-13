import net.czela.trello.TrelloToWiki

def cli = new CliBuilder(usage: "${this.class.simpleName}.groovy -h for this help")
// Create the list of options.
cli.with {
    h longOpt: 'help', 'Show help'
    a longOpt: 'archive', 'Archivace hotovych a neschvalenych ukolu'
    k longOpt: 'key', args: 1, argName: 'key', 'Trello API key'
    t longOpt: 'token', args: 1, argName: 'token', 'Trello API token'
    o longOpt: 'output', args: 1, argName: 'outpuFile', 'Soubor s vystupnim reportem ve formatu mediawiki'
}

def options = cli.parse(args)
cli.usage()

def p = new TrelloToWiki(options)
p.process()
