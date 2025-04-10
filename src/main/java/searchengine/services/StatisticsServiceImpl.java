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
import searchengine.servicesInterface.StatisticsService;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {


    private final SitesListConfig sites;
    @Autowired
    private final PageRepository pageRepository;
    @Autowired
    private LemmaRepository lemmaRepository;
    @Autowired
    private SiteRepository siteRepository;
    @Override
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
            item.setUrl(site.getUrl());

            int pages = pageRepository.findAllPageBySite(
                    siteRepository.findByUrl(
                            site.getUrl()
                    ).get()
            ).size();

            int lemmas = lemmaRepository.findAllLemmaBySite(
                    siteRepository.findByUrl(
                            site.getUrl()
                    )
            ).size();

            item.setPages(pages);
            item.setLemmas(lemmas);

            Site siteQuery = siteRepository.findByUrl(site.getUrl()).get();

            item.setStatus(siteQuery.getStatus().toString());
            item.setError(siteQuery.getLastError() ==  null ? "" : siteQuery.getLastError());

            ZoneId zoneId = ZoneId.systemDefault();
            long time = siteQuery.getStatusTime().atZone(zoneId).toEpochSecond() * 1000;

            item.setStatusTime( time);

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
