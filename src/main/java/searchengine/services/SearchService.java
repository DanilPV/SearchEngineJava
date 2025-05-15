package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import searchengine.enums.Status;
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
import searchengine.util.AddLemmaAndIndex;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {


    private final SiteRepository siteRepository;
    private final AddLemmaAndIndex lemmaService;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;


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

            if (site == null || site.getStatus() != Status.INDEXED) {
                throw new RestException(false, "Сайт " + siteQuery + " не проиндексирован", HttpStatus.OK);
            }

        } else {
            if (!siteRepository.existsByStatus(Status.INDEXED)) {
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
            if (siteRepository.existsByStatus(Status.INDEXED)) {
                sites = siteRepository.findALLByStatus(Status.INDEXED);
            }

        }
        sites.forEach(siteEach -> {
            for (String lemma : lemmas.keySet()) {
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
            Set<Page> currentLemmaPages = new HashSet<>(findPagesByLemmaName(currentLemma.getLemma()));
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
        String cleanText = Jsoup.parse(page.getContent()).text();
        if (cleanText.isEmpty()) {
            return getFallbackSnippet(cleanText);
        }

        try {

            Set<String> queryLemmas = getQueryLemmas(query);
            if (queryLemmas.isEmpty()) {
                return getFallbackSnippet(cleanText);
            }

            List<Match> matches = findMatches(cleanText, queryLemmas);
            if (matches.isEmpty()) {
                return getFallbackSnippet(cleanText);
            }

            SnippetFragment bestFragment = findBestSnippetFragment(cleanText, matches, 200);
            String snippet = cleanText.substring(bestFragment.start, bestFragment.end);

            snippet = highlightMatches(snippet, matches, bestFragment.start);

            return formatSnippet(snippet, bestFragment.start, bestFragment.end, cleanText.length());
        } catch (Exception e) {
            return getFallbackSnippet(cleanText);
        }
    }

    private SnippetFragment findBestSnippetFragment(String text, List<Match> matches, int desiredLength) {
        matches.sort(Comparator.comparingInt(m -> m.start));

        int bestStart = 0;
        int bestEnd = Math.min(desiredLength, text.length());
        int maxMatches = 0;

        for (int i = 0; i < matches.size(); i++) {
            int windowStart = matches.get(i).start;
            int windowEnd = Math.min(windowStart + desiredLength, text.length());
            int matchCount = 0;

            for (int j = i; j < matches.size() && matches.get(j).start < windowEnd; j++) {
                matchCount++;
            }

            if (matchCount > maxMatches) {
                maxMatches = matchCount;
                bestStart = Math.max(0, windowStart - 30);
                bestEnd = Math.min(text.length(), bestStart + desiredLength);
            }
        }

        return new SnippetFragment(bestStart, bestEnd);
    }


    private static class SnippetFragment {
        int start;
        int end;

        SnippetFragment(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private static class Match {
        int start;
        int end;
        String word;

        Match(int start, int end, String word) {
            this.start = start;
            this.end = end;
            this.word = word;
        }
    }

    private Set<String> getQueryLemmas(String query) throws IOException {
        Set<String> lemmas = new HashSet<>();
        LuceneMorphology luceneMorphology = new RussianLuceneMorphology();

        for (String word : query.split("[\\s\\p{Punct}]+")) {
            if (word.isEmpty()) continue;

            try {
                List<String> normalForms = luceneMorphology.getNormalForms(word.toLowerCase());
                lemmas.addAll(normalForms);

                lemmas.add(word.toLowerCase());
            } catch (Exception e) {
                lemmas.add(word.toLowerCase());
            }
        }

        return lemmas;
    }

    private List<Match> findMatches(String text, Set<String> queryLemmas) throws IOException {
        List<Match> matches = new ArrayList<>();
        LuceneMorphology luceneMorphology = new RussianLuceneMorphology();
        Pattern wordPattern = Pattern.compile("\\b[\\p{L}-]+\\b");
        Matcher matcher = wordPattern.matcher(text);

        while (matcher.find()) {
            String word = matcher.group();
            try {
                List<String> lemmas = luceneMorphology.getNormalForms(word.toLowerCase());
                for (String lemma : lemmas) {
                    if (queryLemmas.contains(lemma)) {
                        matches.add(new Match(matcher.start(), matcher.end(), word));
                        break;
                    }
                }
            } catch (Exception e) {
                if (queryLemmas.contains(word.toLowerCase())) {
                    matches.add(new Match(matcher.start(), matcher.end(), word));
                }
            }
        }

        return matches;
    }

    private String highlightMatches(String snippet, List<Match> matches, int snippetStart) {
        StringBuilder builder = new StringBuilder(snippet);

        matches.sort(Comparator.comparingInt((Match m) -> m.start).reversed());

        for (Match match : matches) {
            int startInSnippet = match.start - snippetStart;
            int endInSnippet = match.end - snippetStart;

            if (startInSnippet >= 0 && endInSnippet <= builder.length()) {
                String originalWord = builder.substring(startInSnippet, endInSnippet);

                if (originalWord.equalsIgnoreCase(match.word)) {
                    if (!originalWord.startsWith("<b>")) {
                        builder.replace(startInSnippet, endInSnippet, "<b>" + originalWord + "</b>");

                    }
                }
            }
        }

        return builder.toString();
    }

    private String formatSnippet(String snippet, int start, int end, int textLength) {
        if (start > 0) snippet = "..." + snippet;
        if (end < textLength) snippet += "...";
        return snippet;
    }


    private String getFallbackSnippet(String text) {
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
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

    public HashSet<Page> findPagesByLemmaName(String lemmaName) {
        return new HashSet<>(pageRepository.findAllPageByLemmaName(lemmaName));
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
