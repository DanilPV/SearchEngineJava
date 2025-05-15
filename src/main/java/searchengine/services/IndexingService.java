package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import searchengine.config.SiteConfig;
import searchengine.enums.Status;
import searchengine.exception.RestException;
import searchengine.config.SitesListConfig;
import searchengine.dto.startIndexing.StartIndexingResponce;
import searchengine.dto.stopIndexing.StopIndexingResponce;
import searchengine.enums.StatusIndexing;
import searchengine.util.AddLemmaAndIndex;
import searchengine.util.WebCrawler;
import searchengine.model.Site;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IndexingService {

    public static volatile StatusIndexing isIndexing = StatusIndexing.STOP;

    private final SitesListConfig sites;

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final AddLemmaAndIndex lemmaService;

    private final ApplicationContext context;


    public StartIndexingResponce preIndexing(String url) {

        SitesListConfig siteIndexing = new SitesListConfig();

        if (isIndexing == StatusIndexing.INDEXING) {
            throw new RestException(false, "Индексация уже запущена.", HttpStatus.OK);
        }
        if (isIndexing == StatusIndexing.INTERRUPTION) {
            throw new RestException(false, "Индексация в процессе остановки.", HttpStatus.OK);
        }

        String startPage = null;
        if (url == null) {

            siteRepository.deleteAllInBatch();
            siteIndexing = sites;
            WebCrawler.clearVisitedUrls();

        } else {

            url = URLDecoder.decode(url.replace("url=", ""), StandardCharsets.UTF_8);
            String domain = extractDomainName(url);

            if (domain == null) {
                throw new RestException(false, "Не корректный URL.", HttpStatus.OK);
            }

            List<SiteConfig> newSite = sites.getSites().stream().filter(
                    site -> site.getUrl().equals(domain)
            ).collect(Collectors.toList());

            if (newSite.isEmpty()) {
                throw new RestException(false, "Данная страница находится за пределами сайтов,\\n\" +\n" +
                        "                    \"    указанных в конфигурационном файле.", HttpStatus.OK);
            }


            if (pageRepository.existsByPath(url)) {

                pageRepository.delete(pageRepository.findByPath(url));
                WebCrawler.deleteUrlVisitedUrls(url);

            }

            startPage = url;
            siteIndexing.setSites(newSite);

        }

        formingIndexing(siteIndexing, startPage);

        if (isIndexing == StatusIndexing.INDEXING) {
            return new StartIndexingResponce(true);
        } else {
            throw new RestException(false, "Не удалось запустить индексацию.", HttpStatus.OK);
        }

    }


    public void formingIndexing(SitesListConfig sites, String startPage) {


        List<Site> sitesStart = new ArrayList<>();

        for (SiteConfig siteIndeging : sites.getSites()) {


            Site site ;
            site = siteRepository.findByUrl(siteIndeging.getUrl())
                        .orElseGet(() -> {
                            Site newSite = new Site();
                            newSite.setName(siteIndeging.getName());
                            newSite.setUrl(extractDomainName(siteIndeging.getUrl()));
                            return newSite;
                        });

            site.setStatus(Status.INDEXING);
            site.setStatusTime(LocalDateTime.now());

            siteRepository.save(site);
            sitesStart.add(site);

        }

        startIndexingProcess(sitesStart, startPage);
    }


    public void startIndexingProcess(List<Site> siteList, String startPage) {
        System.out.println("Старт индексации");
        long startTime = System.currentTimeMillis();

        CountDownLatch latch = new CountDownLatch(siteList.size());
        List<Thread> tasks = new ArrayList<>();

        for (Site site : siteList) {

            int finalDepth;
            String finalStartURL;
            if (startPage == null) {
                finalStartURL = site.getUrl();
                finalDepth = 2;
            } else {
                finalStartURL = startPage;
                finalDepth = 1;
            }


            Thread task = new Thread(() -> {
                WebCrawler crawler = context.getBean(WebCrawler.class);
                crawler.setSite(site);
                crawler.setDepth(finalDepth);
                crawler.setUrl(finalStartURL);
                crawler.invoke();


                lemmaService.saveAllLemmaToBD(crawler.getSite());
                System.out.println("Сканирование сайта " + site.getName() + " завершено");

                if (isIndexing == StatusIndexing.INDEXING && site.getLastError() == null) {
                    site.setStatus(Status.INDEXED);
                } else {

                    site.setStatus(Status.FAILED);
                    if (isIndexing == StatusIndexing.INTERRUPTION) {
                        site.setLastError("Индексация остановлена пользователем");
                    }

                }

                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);

                if (!siteRepository.existsByStatus(Status.INDEXING)) {

                    long duration = (System.currentTimeMillis() - startTime) / 1000;
                    System.out.println("Конец индексации за " + duration + " секунд");
                    isIndexing = StatusIndexing.STOP;

                }
                latch.countDown();
            });

            tasks.add(task);
        }
        isIndexing = StatusIndexing.INDEXING;
        tasks.forEach(Thread::start);

    }


    public StopIndexingResponce stopIndexing() {

        if (isIndexing == StatusIndexing.STOP) {
            throw new RestException(false, "Индексация не запущена.", HttpStatus.OK);
        }

        if (isIndexing == StatusIndexing.INTERRUPTION) {
            throw new RestException(false, "Индексация останавливается.", HttpStatus.OK);
        }

        isIndexing = StatusIndexing.INTERRUPTION;
        return new StopIndexingResponce(true);
    }


    public static String extractDomainName(String url) {
        try {
            URI uri = new URI(url);
            String domain = uri.getHost();
            if (domain == null) {
                return null;
            }
            int endIndex = url.indexOf(domain);

            String prefix ="";
            if(!domain.startsWith("www.")) {
                prefix ="www." ;
            }
            domain = url.substring(0, endIndex) + prefix + domain;

            return domain;
        } catch (URISyntaxException e) {
            return null;
        }

    }
}
