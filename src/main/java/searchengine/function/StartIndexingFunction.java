package searchengine.function;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.SpringContext.SpringContext;
import searchengine.config.SiteConfig;
import searchengine.config.SitesListConfig;
import searchengine.enums.STATUS;
import searchengine.enums.StatusIndexing;
import searchengine.model.*;
import searchengine.serviceRepositoryes.LemmaService;
import searchengine.serviceRepositoryes.SiteService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Component
@Getter
@Setter
@RequiredArgsConstructor
public class StartIndexingFunction {

    public static volatile StatusIndexing isIndexing = StatusIndexing.STOP;

    private SitesListConfig sites;
    private String startPage;
    private int depth = 1;

    @Autowired
    private SiteService siteService;

    public StatusIndexing startIndexing() {

        WebCrawler.setVisitedUrls(new HashSet<>());
        isIndexing = StatusIndexing.INDEXING;
        
        try {

            List<Site> sitesStart = new ArrayList<>();
            List<SiteConfig> sitesList = sites.getSites();
            
            for (SiteConfig siteIndeging : sitesList) {

                Site site;
                if (siteService.existsByName(siteIndeging.getName())) {

                    site = siteService.findByName(siteIndeging.getName()).orElse(null);
                    assert site != null;
                    site.setScanLemmas(true);
                } else {

                    site = new Site();
                    site.setName(siteIndeging.getName());
                    site.setUrl(siteIndeging.getUrl());
                    site.setScanLemmas(true);

                }
                site.setStatus(STATUS.INDEXING);
                site.setStatusTime(LocalDateTime.now());

                String mainURL;
                mainURL = site.getUrl().substring(site.getUrl().lastIndexOf("/") + 1).replace("www.", "");
                site.setMainURL(mainURL);

                siteService.saveSite(site);
                sitesStart.add(site);

            }

            start(sitesStart, 1);

        } catch (Exception e) {
            
            isIndexing = StatusIndexing.STOP;
            System.out.println("Ошибка при старте индексации:" + e.getMessage());
            

        }


        return isIndexing;
    }


    public StatusIndexing stopIndexing() {

        isIndexing = StatusIndexing.INTERRUPTION;
        System.out.println("Процесс остановки индексации");
        return isIndexing;

    }

    public void start(List<Site> siteList, int depth) {
        System.out.println("Старт индексации");
        long startTime = System.currentTimeMillis();

        StartIndexingFunction.isIndexing = StatusIndexing.INDEXING;
        LemmaService myLemmaService = SpringContext.getBean(LemmaService.class);
        String startURL;

        CountDownLatch latch = new CountDownLatch(siteList.size());
        List<Thread> tasks = new ArrayList<>();

        for (Site site : siteList) {
            if (startPage == null) {
                startURL = site.getUrl();
                depth = 2;
            } else {
                startURL = startPage;
            }
            String finalStartURL = startURL;
            int finalDepth = depth;
            Thread task = new Thread(() -> {
                WebCrawler crawler = new WebCrawler(finalStartURL, finalDepth, site);
                crawler.invoke();


                myLemmaService.saveAllLemmaSite(crawler.getSite());
                System.out.println("Сканирование сайта " + site.getMainURL() + " завершено");


                if (StartIndexingFunction.isIndexing == StatusIndexing.INDEXING && site.getLastError() == null) {

                    site.setStatus(STATUS.INDEXED);


                } else {

                    site.setStatus(STATUS.FAILED);
                    if (StartIndexingFunction.isIndexing == StatusIndexing.INTERRUPTION) {

                        site.setLastError(StatusIndexing.INTERRUPTION.toString());

                    }

                }

                site.setStatusTime(LocalDateTime.now());

                siteService.saveSite(site);
                if (!siteService.existsByStatus(STATUS.INDEXING)) {

                    long endTime = System.currentTimeMillis();
                    long duration = endTime - startTime;
                    System.out.println("Конец индексации за " + duration / 1000 + " секунд");
                    StartIndexingFunction.isIndexing = StatusIndexing.STOP;

                }
                latch.countDown();
            });

            tasks.add(task);
        }
        tasks.forEach(Thread::start);

    }


}
