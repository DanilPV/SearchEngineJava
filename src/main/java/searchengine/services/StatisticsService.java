package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.SiteConfig;
import searchengine.config.SitesListConfig;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsService {


    private final SitesListConfig sites;

    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SiteRepository siteRepository;
    private final IndexingService indexingService;

    public StatisticsResponse getStatistics() {


        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<SiteConfig> sitesList = sites.getSites();

        for (int i = 0; i < sitesList.size(); i++) {

            SiteConfig site = sitesList.get(i);
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(IndexingService.extractDomainName(site.getUrl()));

            int pages = 0;
            if (pageRepository.existsBySiteUrl(item.getUrl())) {
                pages = pageRepository.findAllPageBySite(
                        siteRepository.findByUrl(
                                item.getUrl()
                        ).get()
                ).size();
            }

            int lemmas = 0;
            if (lemmaRepository.existsBySiteUrl(item.getUrl())) {
                lemmas = lemmaRepository.findAllLemmaBySite(
                        siteRepository.findByUrl(
                                item.getUrl()
                        )
                ).size();
            }


            item.setPages(pages);
            item.setLemmas(lemmas);
            ZoneId zoneId = ZoneId.systemDefault();
            long time;

            if (siteRepository.findByUrl(item.getUrl()).isPresent()) {

                Site siteQuery = siteRepository.findByUrl(item.getUrl()).get();
                item.setStatus(siteQuery.getStatus().toString());
                item.setError(siteQuery.getLastError());


                time = siteQuery.getStatusTime().atZone(zoneId).toEpochSecond() * 1000;
                item.setStatusTime(time);

            } else {
                item.setStatus("NOT_INDEXED");
                item.setError("NOT_INDEXED");
                time = LocalDateTime.now().atZone(zoneId).toEpochSecond() * 1000;
            }

            item.setStatusTime(time);
            total.setPages(total.getPages() + pages);
            total.setLemmas(total.getLemmas() + lemmas);
            detailed.add(item);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }


}
