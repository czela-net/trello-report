groovy -cp src src/exportReport.groovy \
 -g Rada -b 'RADA - czela.net' \
 -u czela_wiki_user  \
 -p czela_wiki_password \
 -k trello_api_key \
 -t trello_api_token \
 -y 2019 -d '29.9.2019' -n 7 \
 -o /tmp/sample-zapis.txt "$@"
