package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import searchengine.enums.STATUS;
import searchengine.exception.RestException;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.function.AddLemmaAndIndex;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService{

    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private AddLemmaAndIndex lemmaService;
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private IndexRepository indexRepository;
    @Autowired
    private LemmaRepository lemmaRepository;




    public SearchResponse search(String query, String siteQuery, int offset, int limit) {
        // TODO: implement search

        if (query == null || query.isEmpty()) {
            throw new RestException(false, "Задан пустой поисковый запрос.", HttpStatus.OK);
        }

        Site site = null;
        if (siteQuery != null) {

            if (siteRepository.existsByUrl(siteQuery)) {
                site = siteRepository.findByUrl(siteQuery).get();
            }

            if (site == null || site.getStatus() != STATUS.INDEXED) {
                throw new RestException(false, "Сайт " + siteQuery + " не проиндексирован", HttpStatus.OK);
            }

        } else {
            if (!siteRepository.existsByStatus(STATUS.INDEXED)) {
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
               getPageCountBySite(site) : getTotalPageCount();
        double exclusionThreshold = 0.8;


        List<Site> sites = new ArrayList<>();

        if (site != null) {
            sites.add(site);
        } else {
            if (siteRepository.existsByStatus(STATUS.INDEXED)) {
                sites = siteRepository.findALLByStatus(STATUS.INDEXED);
            }

        }
        sites.forEach(siteEach -> {
            for (String lemma : lemmas.keySet()) {
                System.out.println("Search lemma " + lemma);

                if (lemmaRepository.findByLemmaAndSite(lemma, siteEach) != null) {
                    Lemma lemmaEntity = lemmaRepository.findByLemmaAndSite(lemma, siteEach);

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
        Set<Page> resultPages = new HashSet<>(findPagesByLemma(firstLemma));

        for (int i = 1; i < lemmas.size() && !resultPages.isEmpty(); i++) {
            Lemma currentLemma = lemmas.get(i);
            Set<Page> currentLemmaPages = new HashSet<>(findPagesByLemma(currentLemma));
            resultPages.retainAll(currentLemmaPages);
        }

        return new ArrayList<>(resultPages);
    }


    private Map<Page, Double> calculateRelevance(List<Page> pages, List<Lemma> lemmas) {

        Map<Page, Double> absoluteRelevance = new HashMap<>();
        double maxRelevance = 0.0;


        for (Page page : pages) {
            double relevance = 0.0;
            for (Lemma lemma : lemmas) {
                Index index = null;

                if (page != null && lemma != null) {
                      index = indexRepository.findByPageAndLemma(page, lemma);
                }
                if (index != null) {
                    relevance += index.getRank();
                }
            }
            absoluteRelevance.put(page, relevance);
            if (relevance > maxRelevance) {
                maxRelevance = relevance;
            }
        }


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
            result.setTitle(getPageTitle(page));
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


        int snippetStart = Math.max(0, positions.get(0) - 50);
        int snippetEnd = Math.min(content.length(), positions.get(0) + 150);
        String snippet = content.substring(snippetStart, snippetEnd);

        for (String word : queryWords) {
            snippet = snippet.replaceAll("(?i)(" + Pattern.quote(word) + ")", "<b>$1</b>");
        }
        snippet = snippet.substring(snippet.indexOf(' ') + 1);
        snippet = snippet.substring(0, snippet.lastIndexOf(' '));

        return snippet + "...";
    }

    public int getPageCountBySite(Site siteUrl) {
        if (pageRepository.existsBySite(siteUrl)) {
            return pageRepository.findBySite(siteUrl).size();
        } else {
            return 0;
        }
    }

    public int getTotalPageCount() {
        return pageRepository.findAll().size();
    }

    public HashSet<Page> findPagesByLemma(Lemma firstLemma) {
        return new HashSet<>(pageRepository.findAllPageByLemma(firstLemma));
    }

    public String getPageTitle(Page page) {

        if (page.getContent() != null) {

            Pattern pattern = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(page.getContent());

            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return "Без заголовка";
    }
}
