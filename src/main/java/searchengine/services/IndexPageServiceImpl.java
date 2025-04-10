package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import searchengine.classesError.RestException;
import searchengine.config.SiteConfig;
import searchengine.config.SitesListConfig;
import searchengine.dto.indexPage.IndexPageResponce;
import searchengine.function.StartIndexingFunction;
import searchengine.enums.StatusIndexing;
import searchengine.model.Page;
import searchengine.repository.PageRepository;
import searchengine.servicesInterface.IndexPageService;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class IndexPageServiceImpl implements IndexPageService {

    private final SitesListConfig sites;

    @Autowired
    private StartIndexingFunction startIndexingFunction;
    @Autowired
    private final PageRepository pageRepository;

    @Override
    public IndexPageResponce indexPage(String url) {

        url = URLDecoder.decode(url.replace("url=", ""), StandardCharsets.UTF_8);
        IndexPageResponce result = new IndexPageResponce(false);

        StatusIndexing indexation = StartIndexingFunction.isIndexing;
        if (indexation == StatusIndexing.INDEXING) {
            throw new RestException(false, "Индексация уже запущена.", HttpStatus.OK);
        }
        if (indexation == StatusIndexing.INTERRUPTION) {
            throw new RestException(false, "Индексация в процессе остановки.", HttpStatus.OK);
        }

        if (pageRepository.existsByPath(url)) {
            // Страница уже проиндексированная
            Page page = pageRepository.findByPath(url);
            pageRepository.delete(page);
        }


        SiteConfig site = new SiteConfig();
        site.setUrl(url);
        site.setName(url);
        site.setScanLemmas(true);

        AtomicBoolean parent = new AtomicBoolean(false);
        List<SiteConfig> pageSite = new ArrayList<>();

        for (SiteConfig value : sites.getSites()) {
            if (url.startsWith(value.getUrl())) {
                pageSite.add(value);
                parent.set(true);
            }
        }

        SitesListConfig sitesList = new SitesListConfig();
        sitesList.setSites(pageSite);


        if (parent.get()) {

            startIndexingFunction.setSites(sitesList);
            startIndexingFunction.setStartPage(url);
            if (startIndexingFunction.startIndexing() == StatusIndexing.INDEXING) {
                result.setResult(true);
            } else {
                throw new RestException(false, "Не удалось запустить индексацию.", HttpStatus.OK);
            }

        } else {
            throw new RestException(false, "Данная страница находится за пределами сайтов,\\n\" +\n" +
                    "                    \"    указанных в конфигурационном файле.", HttpStatus.OK);
        }

        return result;
    }


}
