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
            System.out.println(currentLemmaPages);
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
            Set<String> searchTerms = prepareSearchTerms(query.toLowerCase());
            Map<Integer, String> termOccurrences = new TreeMap<>();
            for (String term : searchTerms) {
                Pattern pattern = Pattern.compile("\\b" + Pattern.quote(term) + "\\b", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(cleanText);

                while (matcher.find()) {
                    String matchedWord = cleanText.substring(matcher.start(), matcher.end());
                    termOccurrences.put(matcher.start(), matchedWord);
                }
            }

            if (termOccurrences.isEmpty()) {
                return getFallbackSnippet(cleanText);
            }

            int firstPos = termOccurrences.keySet().iterator().next();
            int snippetStart = Math.max(0, firstPos - 50);
            int snippetEnd = Math.min(cleanText.length(), firstPos + 150);

            String snippet = cleanText.substring(snippetStart, snippetEnd);
            snippet = highlightTerms(snippet, termOccurrences, snippetStart, snippetEnd);

            return formatSnippet(snippet, snippetStart, snippetEnd, cleanText.length());
        } catch (Exception e) {
            System.out.println("Ошибка при генерации сниппета: " + e.getMessage());
            return getFallbackSnippet(cleanText);
        }
    }


    private Set<String> prepareSearchTerms(String query) throws IOException {

        Set<String> searchTerms = new HashSet<>();

        for (String originalWord : query.split("\\s+")) {
            List<String> forms = getAllPossibleForms(originalWord);
            List<String> formsHcase = forms.stream()
                    .map(word -> word.isEmpty() ? word
                            : word.substring(0, 1).toUpperCase() + word.substring(1))
                    .toList();
            searchTerms.addAll(forms);
            searchTerms.addAll(formsHcase);
        }

        return searchTerms;
    }


    private String highlightTerms(String snippet, Map<Integer, String> occurrences,
                                  int snippetStart, int snippetEnd) {
        for (Map.Entry<Integer, String> entry : occurrences.entrySet()) {
            int pos = entry.getKey();
            if (pos >= snippetStart && pos <= snippetEnd) {
                String word = entry.getValue();
                snippet = snippet.replaceAll("(?i)\\b" + Pattern.quote(word) + "\\b",
                        "<b>" + word + "</b>");
            }
        }
        return snippet;
    }

    private String formatSnippet(String snippet, int snippetStart, int snippetEnd, int textLength) {
        if (snippetStart > 0) snippet = "..." + snippet;
        if (snippetEnd < textLength) snippet += "...";
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


    public List<String> getAllPossibleForms(String word) throws IOException {

        LuceneMorphology luceneMorphology = new RussianLuceneMorphology();
        List<String> lemmas = luceneMorphology.getNormalForms(word);
        if (lemmas.isEmpty()) {
            return Collections.singletonList(word);
        }

        String lemma = lemmas.get(0);
        List<String> forms = new ArrayList<>();
        forms.add(lemma);

        String morphInfo = luceneMorphology.getMorphInfo(lemma).get(0);
        if (morphInfo.contains("С")) {
            generateNounForms(lemma, forms);
        } else if (morphInfo.contains("Г")) {
            generateVerbForms(lemma, forms);
        } else if (morphInfo.contains("П")) {
            generateAdjectiveForms(lemma, forms);
        }

        if (!forms.contains(word)) {
            forms.add(word);
        }

        return forms.stream().distinct().collect(Collectors.toList());
    }

    private void generateNounForms(String lemma, List<String> forms) {

        String stem = getStem(lemma);

        if (lemma.endsWith("ие")) {
            forms.add(stem + "ия");
        } else if (lemma.endsWith("ость")) {
            forms.add(stem + "ости");
        } else if (lemma.endsWith("ство")) {
            forms.add(stem + "ства");
        } else {
            forms.add(lemma + "а");
        }

        forms.add(lemma + "у");
        forms.add(lemma);
        forms.add(lemma + "ом");
        forms.add(lemma + "м");
        forms.add(lemma + "е");
        forms.add(lemma + "ы");
        forms.add(lemma + "ов");
        forms.add(stem + "ей");
        forms.add(stem + "ией");
        forms.add(stem + "иями");
    }

    private void generateVerbForms(String lemma, List<String> forms) {
        String stem = getStem(lemma);

        forms.add(stem + "ю");
        forms.add(stem + "ешь");
        forms.add(stem + "ет");
        forms.add(stem + "ем");
        forms.add(stem + "ете");
        forms.add(stem + "ют");
        forms.add(stem + "л");
        forms.add(stem + "ла");
        forms.add(stem + "ло");
        forms.add(stem + "ли");
        forms.add(stem + "й");
    }


    private void generateAdjectiveForms(String lemma, List<String> forms) {
        String stem = getStem(lemma);

        forms.add(lemma);
        forms.add(stem + "ого");
        forms.add(stem + "ому");
        forms.add(stem + "ым");
        forms.add(stem + "ом");
        forms.add(stem + "ая");
        forms.add(stem + "ой");
        forms.add(stem + "ую");
        forms.add(stem + "ые");
        forms.add(stem + "ых");
        forms.add(stem + "ыми");

        forms.add(stem);
        forms.add(stem + "а");
        forms.add(stem + "о");
        forms.add(stem + "ы");
    }


    private String getStem(String word) {

        if (word.length() < 3) return word;
        if (word.endsWith("ый") || word.endsWith("ий") || word.endsWith("ой") || word.endsWith("ия") || word.endsWith("ей")) {
            return word.substring(0, word.length() - 2);
        } else if (word.endsWith("ть")) {
            return word.substring(0, word.length() - 2);
        } else if (word.endsWith("а") || word.endsWith("я") || word.endsWith("о")) {
            return word.substring(0, word.length() - 1);
        }

        return word;
    }
}
