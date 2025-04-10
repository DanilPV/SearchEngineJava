package searchengine.serviceRepositoryes;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.SpringContext.SpringContext;
import searchengine.function.Functions;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.LemmaRepository;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class LemmaService {

    @Autowired
    private LemmaRepository lemmaRepository;


    private static ConcurrentHashMap<String, Lemma> addLemmas = new ConcurrentHashMap<>();

    public void addAllLemma(TreeMap<String, Integer> lemaList, Page page) {

        List<Lemma> newLemmas = new ArrayList<>();
        try {


            lemaList.forEach((lemmaName, frequency) -> {
                Lemma lemma;
                if (lemmaRepository.findByLemmaAndSite(lemmaName, page.getSite()) == null) {

                    lemma = new Lemma();
                    lemma.setSite(page.getSite());
                    lemma.setLemma(lemmaName);
                    newLemmas.add(lemma);

                } else {
                    lemma = lemmaRepository.findByLemmaAndSite(lemmaName, page.getSite());
                }

                // Используем составной ключ для уникальности
                String key = lemmaName + "|" + page.getSite().getId();

                addLemmas.compute(key, (k, existingLemma) -> {
                    if (existingLemma != null) {
                        // Лемма уже существует - обновляем частоту
                        existingLemma.setFrequency(existingLemma.getFrequency() + 1);
                        return existingLemma;
                    } else {
                        // Новая лемма
                        lemma.setFrequency(1);
                        return lemma;
                    }
                });

                Index index = new Index();
                index.setPage(page);
                index.setRank(frequency);
                index.setLemma(addLemmas.get(key));
                IndexService.allIndex.put(index.getPage().getPath() + index.getLemma().getLemma(), index);
            });

        } catch (Exception e) {
            System.out.println(" Ошибка при сохранении леммы:" + e.getMessage());
        }


    }

    public void saveAllLemmaSite(Site site) {


        // 1. Подготовка данных для сохранения
        List<Lemma> lemmasToSave = addLemmas.values().stream()
                .filter(lemma -> lemma.getSite().equals(site))
                .collect(Collectors.toList());

        // 2. Параллельное сохранение с использованием ForkJoinPool
        int batchSize = 1500;  // Размер батча
        List<List<Lemma>> batches = Functions.partitionList(lemmasToSave, batchSize);

        ForkJoinPool customThreadPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());

        try {
            customThreadPool.submit(() ->
                    batches.parallelStream().forEach(batch -> {
                        lemmaRepository.saveAll(batch);
                        lemmaRepository.flush();
                    })
            ).get();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            customThreadPool.shutdown();
        }


        try {
            addLemmas.values().removeIf((lemma -> lemma.getSite().equals(site)));
        } catch (Exception e) {
            System.out.println(" Ошибка при удалении лемм:" + e.getMessage());
        }

        System.out.println("Леммы сайта " + site.getName() + " сохранены");

        if (!batches.isEmpty()) {
            IndexService myIndexService = SpringContext.getBean(IndexService.class);
            myIndexService.saveAllIndexSite(site);
        }


    }

     private static final String[] particlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};

    private boolean hasParticleProperty(String wordBase) {
        for (String property : particlesNames) {
            if (wordBase.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }


    private boolean anyWordBaseBelongToParticle(List<String> wordBaseForms) {
        return wordBaseForms.stream().anyMatch(this::hasParticleProperty);
    }


    private String[] arrayContainsRussianWords(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("([^а-я\\s])", " ")
                .trim()
                .split("\\s+");
    }


    public TreeMap<String, Integer> extractLemmasFromString(String text) throws IOException {

        String[] words = arrayContainsRussianWords(text);
        TreeMap<String, Integer> lemmas = new TreeMap<>();
        LuceneMorphology luceneMorphology = new RussianLuceneMorphology();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }

            List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
            if (anyWordBaseBelongToParticle(wordBaseForms)) {
                continue;
            }

            List<String> normalForms = luceneMorphology.getNormalForms(word);
            if (normalForms.isEmpty()) {
                continue;
            }

            String normalWord = normalForms.get(0);

            if (lemmas.containsKey(normalWord)) {
                lemmas.put(normalWord, lemmas.getOrDefault(normalWord, 0) + 1);
            } else {
                lemmas.put(normalWord, 1);
            }
        }

        return lemmas;
    }


    public Lemma getLemma(String lemma, Site siteUrl) {

        if (lemmaRepository.findByLemmaAndSite(lemma, siteUrl) != null) {
            return lemmaRepository.findByLemmaAndSite(lemma, siteUrl);
        } else {
            return null;
        }
    }


}