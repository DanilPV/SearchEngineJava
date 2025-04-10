package searchengine.function;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.RecursiveAction;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.SpringContext.SpringContext;
import searchengine.config.AppConfig;
import searchengine.enums.StatusIndexing;
import searchengine.model.*;
import searchengine.serviceRepositoryes.LemmaService;
import searchengine.serviceRepositoryes.PageService;
import searchengine.serviceRepositoryes.SiteService;


@RequiredArgsConstructor
public class WebCrawler extends RecursiveAction {

    @Setter
    private static volatile Set<String> visitedUrls = new HashSet<>();

    private final String url;
    private final int depth;
    @Getter
    private final Site site;


    @Override
    protected void compute() {

        StatusIndexing isIndexing = StartIndexingFunction.isIndexing;

        if (isIndexing != StatusIndexing.INDEXING) {
            return;
        }

        if (depth <= 0 || visitedUrls.contains(url)) {
            return;
        }

        SiteService mySiteService = SpringContext.getBean(SiteService.class);

        site.setStatusTime(LocalDateTime.now());
        mySiteService.saveSite(site);

        visitedUrls.add(url);

        try {

            AppConfig appConfig = SpringContext.getBean(AppConfig.class);
            Document doc = Jsoup.connect(url)
                    .userAgent(appConfig.getUserAgent())
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Connection", "keep-alive")
                    .referrer(appConfig.getReferrer() )
                    .ignoreContentType(true)
                    .timeout(30000) // Таймаут 30 секунд
                    .get();


            PageService myPageService = SpringContext.getBean(PageService.class);
            Page page = new Page();
            page.setCode(doc.connection().response().statusCode());
            page.setContent(doc.html());
            page.setSite(site);
            page.setPath(url);
            myPageService.savePage(page);


            Elements links = doc.select("a[href]");

            Set<WebCrawler> subtasks = new HashSet<>();

            for (Element link : links) {

                String nextUrl = link.absUrl("href");

                if (!nextUrl.startsWith("http") & !nextUrl.startsWith("/")) {
                    continue;
                }

                if (!visitedUrls.contains(nextUrl)) {

                    if (nextUrl.startsWith("/")) {
                        nextUrl = site.getUrl() + nextUrl.substring(0);

                    }


                    int startIndex = nextUrl.indexOf("/") + 2;
                    int stopIndex = nextUrl.substring(startIndex).indexOf("/");

                    if (stopIndex == -1) {
                        stopIndex = nextUrl.length() - startIndex;
                    }

                    String mainFromLink = nextUrl.substring(startIndex, startIndex + stopIndex);
                    if (site.getMainURL().equals(mainFromLink.replace("www.", ""))) {


                        WebCrawler subtask = new WebCrawler(nextUrl, depth - 1, site);
                        subtasks.add(subtask);

                    }


                }


            }
            invokeAll(subtasks);


            if (site.isScanLemmas()) {
                startLemmas(doc.body().toString(), page);
            }

        } catch (IOException | InterruptedException e) {

            PageService myPageService = SpringContext.getBean(PageService.class);
            Page page = new Page();
            if (e.getMessage().equals("Read timed out")) {
                page.setCode(404);
            }
            if (e.getMessage().equals("500 Internal Server Error")) {
                page.setCode(500);
            }
            page.setContent("");
            page.setSite(site);
            page.setPath(url);
            myPageService.savePage(page);

            System.err.println("Error visiting " + url + ": " + e.getMessage());

            site.setLastError(e.getMessage());
            site.setStatusTime(LocalDateTime.now());
            mySiteService.saveSite(site);

        }


    }


    public void startLemmas(String text, Page page) throws InterruptedException {

        String finalText = text;
        Thread thread = new Thread(() -> {
            try {

                LemmaService myLemmaService = SpringContext.getBean(LemmaService.class);

                TreeMap<String, Integer> lemaList = myLemmaService.extractLemmasFromString(finalText);

                myLemmaService.addAllLemma(lemaList, page);

            } catch (IOException e) {
                System.out.println(e.getMessage());
                throw new RuntimeException(e);
            }
        });
        thread.start();
        thread.join();

    }

}
