Crawler is kind of a port of this repository:  
https://github.com/dipu-bd/lightnovel-crawler

Things of note:

- This project is in super early stages of "Proof of Concept"
- Using jsoup instead of BeautifulSoup to parse HTML
- I can't find a Java equivalent of the python library cloudscraper, so for now HTML is pulled from
a WebView, which passes the Cloudflare checks. Until I write a Java version of that library.
- I'm only porting over the sources that I use, as I need them.
