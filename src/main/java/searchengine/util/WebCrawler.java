package searchengine.util;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.RecursiveAction;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import searchengine.config.AppConfig;
import searchengine.enums.StatusIndexing;
import searchengine.model.*;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.IndexingService;


@Component
@Scope("prototype")
@RequiredArgsConstructor
@Getter
@Setter
public class WebCrawler extends RecursiveAction {


    private static volatile Set<String> visitedUrls = new HashSet<>();
    private final ApplicationContext context;
    private String url;
    private int depth;


    private Site site;

    private final SiteRepository siteRepository;
    private final AddLemmaAndIndex addLemmaAndIndex;
    private final PageRepository pageRepository;
    private final AppConfig appConfig;

    public static synchronized void clearVisitedUrls() {
        visitedUrls.clear();
    }

    public static synchronized void deleteUrlVisitedUrls(String url) {
        visitedUrls.remove(url);
    }

    @Override
    protected void compute() {

        StatusIndexing isIndexing = IndexingService.isIndexing;

        if (isIndexing != StatusIndexing.INDEXING) {
            return;
        }

        if (depth <= 0 || visitedUrls.contains(url)) {
            return;
        }


        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);

        visitedUrls.add(url);

        try {


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


            Page page = new Page();
            page.setCode(doc.connection().response().statusCode());
            page.setContent(doc.html().replace("\u0000", ""));
            page.setSite(site);
            page.setPath(url);
            pageRepository.save(page);


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



                    String mainFromLink = IndexingService.extractDomainName(nextUrl);
                    if (site.getUrl().equals(mainFromLink)) {


                        WebCrawler subtask = context.getBean(WebCrawler.class);
                        subtask.setSite(site);
                        subtask.setUrl(nextUrl);
                        subtask.setDepth(depth - 1);
                        subtasks.add(subtask);

                    }


                }


            }
            invokeAll(subtasks);
            startLemmas(page);


        } catch (IOException | InterruptedException e) {


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
            pageRepository.save(page);

            System.err.println("Error visiting " + url + ": " + e.getMessage());

            site.setLastError(e.getMessage());
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);

        }


    }


    public void startLemmas(Page page) throws InterruptedException {

        Thread thread = new Thread(() -> {
            try {

                TreeMap<String, Integer> lemaList = addLemmaAndIndex.extractLemmasFromString(page.getContent());
                addLemmaAndIndex.addAllLemma(lemaList, page);

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        thread.start();
        thread.join();

    }

}
