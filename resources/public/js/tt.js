current_url = window.location.href;
current_title = encodeURIComponent(document.title);
tweet = "<a title=\"Share on twitter\" target=\"_blank\" href=\"https://twitter.com/share?via=bzg2&text=" + current_title + " http://www.panoramap.org\"\">Tweet :)</a>";
document.write(tweet);
