package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import searchengine.classesError.RestException;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.serviceRepositoryes.IndexService;
import searchengine.serviceRepositoryes.LemmaService;
import searchengine.serviceRepositoryes.PageService;
import searchengine.serviceRepositoryes.SiteService;
import searchengine.servicesInterface.SearchService;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceIml implements SearchService {

    @Autowired
    private SiteService siteService;
    @Autowired
    private LemmaService lemmaService;
    @Autowired
    private PageService pageService;
    @Autowired
    private IndexService indexService;


    @Override
    public SearchResponse search(String query, String siteQuery, int offset, int limit) {
        // TODO: implement search

        if (query == null || query.isEmpty()) {
            throw new RestException(false, "Задан пустой поисковый запрос.", HttpStatus.OK);
        }

        Site site = null;
        if (siteQuery != null) {
            site = siteService.getSiteByUrl(siteQuery);
            if (site == null || !siteService.isSiteIndexed(site)) {
                throw new RestException(false, "Сайт " + siteQuery + " не проиндексирован", HttpStatus.OK);
            }
        } else {
            if (!siteService.isAnySiteIndexed()) {
                throw new RestException(false, "Нет проиндексированных сайтов", HttpStatus.OK);

            }
        }


        try {

            TreeMap<String, Integer> queryLemmas = lemmaService.extractLemmasFromString(query);


            List<Lemma> filteredLemmas = filterCommonLemmas(queryLemmas, site);

            if (filteredLemmas.isEmpty()) {
                throw new RestException(false, "Ничего не найдено", HttpStatus.OK);
            }


            filteredLemmas.sort(Comparator.comparingInt(Lemma::getFrequency));


            List<Page> foundPages = findPagesContainingAllLemmas(filteredLemmas);

            if (foundPages.isEmpty()) {
                throw new RestException(false, "Нет страниц содержащих все слова", HttpStatus.OK);
            }


            Map<Page, Double> pageRelevanceMap = calculateRelevance(foundPages, filteredLemmas);

            // Сортируем страницы по релевантности
            List<Page> sortedPages = pageRelevanceMap.entrySet().stream()
                    .sorted(Map.Entry.<Page, Double>comparingByValue().reversed())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());


            List<SearchData> data = prepareSearchResults(
                    sortedPages, pageRelevanceMap, query, offset, limit);

            SearchResponse response = new SearchResponse();
            response.setCount(data.size());
            response.setData(data);
            response.setResult(true);

            System.out.println(response);

            return response;

        } catch (Exception e) {
            System.out.println(e.fillInStackTrace().toString());
            throw new RestException(false, "Ошибка при выполнении поиска: " + e.getMessage(), HttpStatus.OK);

        }


    }


    private List<Lemma> filterCommonLemmas(TreeMap<String, Integer> lemmas, Site site) {

        List<Lemma> result = new ArrayList<>();
        int totalPages = site != null ?
                pageService.getPageCountBySite(site) : pageService.getTotalPageCount();
        double exclusionThreshold = 0.8; // Исключаем леммы, встречающиеся на 80%+ страниц


        List<Site> sites = new ArrayList<>();

        if (site != null) {
            sites.add(site);
        } else {
            sites = siteService.getAllIndexedSites();

        }
        sites.forEach(siteEach -> {
            for (String lemma : lemmas.keySet()) {
                System.out.println("Search lemma " + lemma);
                Lemma lemmaEntity = lemmaService.getLemma(lemma, siteEach);
                if (lemmaEntity != null) {
                    double frequencyRatio = (double) lemmaEntity.getFrequency() / totalPages;
                    if (frequencyRatio < exclusionThreshold) {
                        result.add(lemmaEntity);
                    }
                }
            }

        });

        return result;
    }

    private List<Page> findPagesContainingAllLemmas(List<Lemma> lemmas) {
        if (lemmas.isEmpty()) return Collections.emptyList();


        Lemma firstLemma = lemmas.get(0);
        Set<Page> resultPages = new HashSet<>(pageService.findPagesByLemma(firstLemma));

        // Постепенно сужаем выборку по остальным леммам
        for (int i = 1; i < lemmas.size() && !resultPages.isEmpty(); i++) {
            Lemma currentLemma = lemmas.get(i);
            Set<Page> currentLemmaPages = new HashSet<>(pageService.findPagesByLemma(currentLemma));
            resultPages.retainAll(currentLemmaPages);
        }

        return new ArrayList<>(resultPages);
    }


    private Map<Page, Double> calculateRelevance(List<Page> pages, List<Lemma> lemmas) {

        Map<Page, Double> absoluteRelevance = new HashMap<>();
        double maxRelevance = 0.0;

        // Рассчитываем абсолютную релевантность для каждой страницы
        for (Page page : pages) {
            double relevance = 0.0;
            for (Lemma lemma : lemmas) {
                Index index = indexService.getIndex(page, lemma);
                if (index != null) {
                    relevance += index.getRank();
                }
            }
            absoluteRelevance.put(page, relevance);
            if (relevance > maxRelevance) {
                maxRelevance = relevance;
            }
        }

        // Преобразуем в относительную релевантность
        if (maxRelevance > 0) {
            for (Map.Entry<Page, Double> entry : absoluteRelevance.entrySet()) {
                entry.setValue(entry.getValue() / maxRelevance);
            }
        }

        return absoluteRelevance;
    }

    private List<SearchData> prepareSearchResults(List<Page> pages,
                                                  Map<Page, Double> relevanceMap,
                                                  String query,
                                                  int offset,
                                                  int limit) {
        List<SearchData> results = new ArrayList<>();

        int end = Math.min(offset + limit, pages.size());
        for (int i = offset; i < end; i++) {
            Page page = pages.get(i);
            SearchData result = new SearchData();
            result.setSite(page.getSite().getUrl());
            result.setSiteName(page.getSite().getName());
            result.setUri(page.getPath());
            result.setTitle(pageService.getPageTitle(page));
            result.setSnippet(generateSnippet(page, query));
            result.setRelevance(relevanceMap.get(page));
            results.add(result);
        }

        return results;
    }


    private String generateSnippet(Page page, String query) {

        String content = page.getContent();
        String[] queryWords = query.toLowerCase().split("\\s+");


        List<Integer> positions = new ArrayList<>();
        for (String word : queryWords) {
            int pos = content.toLowerCase().indexOf(word);
            while (pos >= 0) {
                positions.add(pos);
                pos = content.toLowerCase().indexOf(word, pos + 1);
            }
        }

        if (positions.isEmpty()) {
            return "";
        }

        // Выбираем область вокруг первого вхождения
        int snippetStart = Math.max(0, positions.get(0) - 50);
        int snippetEnd = Math.min(content.length(), positions.get(0) + 150);
        String snippet = content.substring(snippetStart, snippetEnd);

        // Выделяем слова запроса жирным
        for (String word : queryWords) {
            snippet = snippet.replaceAll("(?i)(" + Pattern.quote(word) + ")", "<b>$1</b>");
        }

        // Обрезаем до целых слов
        snippet = snippet.substring(snippet.indexOf(' ') + 1);
        snippet = snippet.substring(0, snippet.lastIndexOf(' '));

        return snippet + "...";
    }
}
